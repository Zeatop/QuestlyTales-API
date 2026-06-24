package org.tpi.questlytales.services;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tpi.questlytales.dtos.AttributeDTO;
import org.tpi.questlytales.dtos.GenerateStoryRequestDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Générateur d'histoires basé sur Claude (Anthropic).
 *
 * Différence clé avec {@link DeepSeekService} : la topologie du graphe est
 * construite par un cycle générateur→critique multi-passes (Passe A = premier
 * jet raisonné, Passes B = relecture/réécriture pour des interconnexions
 * motivées), au lieu d'un re-câblage aléatoire en Java. Le raisonnement étendu
 * de Claude (adaptive thinking) porte exactement la partie où DeepSeek échouait :
 * décider OÙ les chemins doivent converger pour que ce soit dramatiquement juste.
 */
@Service
public class ClaudeStoryService implements StoryGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClaudeStoryService.class);

    /**
     * Parseur tolérant DÉDIÉ aux réponses Claude : les LLM glissent souvent des
     * virgules traînantes, des quotes simples ou des commentaires. On isole cette
     * permissivité ici, sans relâcher l'ObjectMapper partagé de Spring.
     */
    private static final ObjectMapper LENIENT_JSON = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .build();

    private static final int MAX_NODE_COUNT = 200;
    private static final int CONTENT_BATCH_SIZE = 3;

    // Budgets de tokens. Les passes de structure incluent le thinking (qui
    // compte dans le budget), d'où une marge confortable.
    private static final long STRUCTURE_MAX_TOKENS = 20000L;
    private static final long CONTENT_MAX_TOKENS = 16000L;

    private static final String[] NODE_COLORS = {
        "#4A90D9", "#27AE60", "#8E44AD", "#E67E22", "#16A085",
        "#2980B9", "#D35400", "#1ABC9C", "#9B59B6", "#2ECC71"
    };
    private static final String END_NODE_COLOR = "#E74C3C";

    @Value("${anthropic.model:claude-opus-4-8}")
    private String model;

    /** Nombre de passes de critique/réécriture de la topologie après le premier jet. */
    @Value("${anthropic.structure.refinement-passes:2}")
    private int refinementPasses;

    @Autowired
    private DataTypeRegistry dataTypeRegistry;

    @Autowired
    private ActionRegistry actionRegistry;

    @Autowired
    private AnthropicClient anthropicClient;

    @Autowired
    private ObjectMapper objectMapper;

    // ===== POINT D'ENTRÉE =====

    @Override
    public Map<String, Object> generateStory(GenerateStoryRequestDTO request) throws Exception {
        int minNodes = Objects.requireNonNullElse(request.getMinNodeCount(), 5);
        if (minNodes > MAX_NODE_COUNT) {
            throw new IllegalArgumentException(
                "minNodeCount ne peut pas dépasser " + MAX_NODE_COUNT + ". Valeur reçue : " + minNodes
            );
        }

        // Phase 1 — Premier jet de la structure (Passe A)
        Map<String, Object> skeleton = generateSkeleton(request, minNodes);

        // Phase 1.5 — Cycle générateur→critique : on renvoie le graphe à Claude
        // pour qu'il l'améliore lui-même (convergences, carrefours, fins distinctes).
        for (int pass = 0; pass < Math.max(0, refinementPasses); pass++) {
            skeleton = refineTopology(skeleton, request, pass + 1);
        }

        // Phase 1.6 — Normalisation générique : impose les invariants que le reste
        // du pipeline suppose (id uniques et séquentiels 0..N-1, aucune référence
        // morte, pas d'auto-boucle). Identité par position. Si Claude a émis des id
        // en double (cas ambigu), déclenche UNE passe d'auto-réparation avant de
        // retomber sur l'heuristique. Renvoie l'index id -> cibles (topologie validée).
        Map<Integer, List<Integer>> skeletonTargets = normalizeSkeleton(skeleton, request);

        Map<String, Object> skeletonMeta = castMap(skeleton.get("metadata"));
        String title = skeletonMeta != null ? (String) skeletonMeta.get("title") : null;
        String storyDesc = skeletonMeta != null ? (String) skeletonMeta.get("description") : null;
        List<Map<String, Object>> skeletonNodes = castList(skeleton.get("nodes"));

        // Schéma d'attributs AUTORITAIRE = celui fourni dans la requête. Le modèle ne
        // peut ni en inventer, ni en changer le type : on l'impose ici et on le
        // réinjecte tel quel dans le résultat. Élimine la dérive de types
        // (ex. confiance Boolean→Int→Float d'un run à l'autre).
        List<Map<String, Object>> storyAttributes = requestAttributes(request);
        String characters = storyAttributes.stream()
            .filter(a -> "characters".equals(a.get("label")))
            .map(a -> (String) a.get("value"))
            .findFirst()
            .orElse(null);

        // Phase 2 — Contenu narratif par lots, EN ORDRE TOPOLOGIQUE : chaque nœud est
        // rempli après TOUS ses prédécesseurs, donc ses incomingChoices (textes des
        // choix qui y mènent) sont toujours connus — y compris pour les arcs arrière
        // (ex. 17→11). On honore ainsi la promesse de chaque choix entrant.
        List<Map<String, Object>> orderedNodes = orderNodesTopologically(skeletonNodes, skeletonTargets);

        List<Map<String, Object>> filledNodes = new ArrayList<>();
        Map<Integer, List<String>> incomingChoicesByNodeId = new HashMap<>();
        for (int i = 0; i < orderedNodes.size(); i += CONTENT_BATCH_SIZE) {
            int end = Math.min(i + CONTENT_BATCH_SIZE, orderedNodes.size());
            List<Map<String, Object>> batch = orderedNodes.subList(i, end);
            int ctxStart = Math.max(0, filledNodes.size() - 3);
            List<Map<String, Object>> previousBatch = filledNodes.subList(ctxStart, filledNodes.size());

            List<Map<String, Object>> filled = fillNodeBatch(
                batch, incomingChoicesByNodeId, title, storyDesc, characters, storyAttributes, previousBatch);
            filledNodes.addAll(filled);

            // Enregistre les choix sortants pour alimenter les nœuds avals
            for (Map<String, Object> fn : filled) {
                List<Map<String, Object>> choices = castList(fn.get("choices"));
                if (choices == null) continue;
                for (Map<String, Object> c : choices) {
                    if (c.get("nextNodeId") instanceof Number num
                            && c.get("choiceText") instanceof String text && !text.isBlank()) {
                        incomingChoicesByNodeId.computeIfAbsent(num.intValue(), k -> new ArrayList<>()).add(text);
                    }
                }
            }
        }

        // Rétablit l'ordre par id pour la sortie : le remplissage topologique ne doit
        // changer ni la forme du résultat ni le layout x/y (calculé par position).
        filledNodes.sort(java.util.Comparator.comparingInt(fn ->
            fn.get("id") instanceof Number num ? num.intValue() : Integer.MAX_VALUE));

        // Phase 2.5 — Normalisation : corrige les actions sans wrapper "params"
        sanitizeNodes(filledNodes);

        // Phase 2.5b — Garde-fou schéma : supprime toute action d'état (params.attr)
        // ciblant un attribut hors du schéma autoritaire (le modèle ne doit en utiliser
        // aucun autre que ceux fournis).
        Set<String> allowedLabels = new HashSet<>();
        for (Map<String, Object> a : storyAttributes) {
            if (a.get("label") instanceof String label) allowedLabels.add(label);
        }
        stripUnknownAttributeActions(filledNodes, allowedLabels);

        // Phase 2.6 — Réconciliation : la topologie finale doit correspondre
        // EXACTEMENT au squelette validé (on ignore tout choix qui dérive).
        reconcileChoicesWithSkeleton(filledNodes, skeletonTargets);

        // Phase 3 — Enrichissement Java (coordonnées, couleurs)
        enrichNodes(filledNodes);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", title);
        metadata.put("description", storyDesc);
        metadata.put("author", null);
        metadata.put("version", "1.0");
        metadata.put("language", null);
        metadata.put("creationDate", null);
        metadata.put("lastUpdateDate", null);
        metadata.put("tags", List.of());
        metadata.put("thumbnailImage", null);
        metadata.put("genre", null);
        metadata.put("size", filledNodes.size());
        metadata.put("numberOfNodes", filledNodes.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metadata", metadata);
        result.put("nodes", filledNodes);
        result.put("attributes", storyAttributes);
        result.put("images", Map.of());
        result.put("videos", Map.of());
        result.put("sounds", Map.of());
        return result;
    }

    // ===== PHASE 1 : SQUELETTE (Passe A) =====

    private Map<String, Object> generateSkeleton(GenerateStoryRequestDTO request, int nodeCount) throws Exception {
        String systemPrompt = buildSkeletonSystemPrompt();
        String userMessage = buildSkeletonUserMessage(request, nodeCount);
        return callClaudeJson("Passe A (squelette)", systemPrompt, userMessage, STRUCTURE_MAX_TOKENS, true);
    }

    private String buildSkeletonSystemPrompt() {
        return """
            Tu es un scénariste professionnel expert en récits interactifs. \
            Tu maîtrises les arcs dramatiques, le tempo et le suspense. \
            Ta mission : concevoir la STRUCTURE d'une histoire interactive à embranchements riches. \
            Retourne UNIQUEMENT un JSON valide, sans markdown ni texte.

            FORMAT EXACT :
            {
              "metadata": {"title": "string", "description": "string"},
              "attributes": [ ... recopie ICI, à l'identique, les attributs fournis dans la requête ... ],
              "nodes": [
                {"id": 0, "targetNodeIds": [1, 2]},
                {"id": 1, "targetNodeIds": [3, 4]},
                {"id": 2, "targetNodeIds": [3, 5]},
                {"id": 3, "targetNodeIds": [6, 7]},
                {"id": 4, "targetNodeIds": [6]},
                {"id": 5, "targetNodeIds": [7, 8]},
                {"id": 6, "targetNodeIds": []},
                {"id": 7, "targetNodeIds": [3]},
                {"id": 8, "targetNodeIds": []}
              ]
            }

            STRUCTURE NARRATIVE (obligatoire) :
            - Acte 1 (~25% des nœuds) : mise en place, présentation de l'enjeu, premiers choix structurants
            - Acte 2 (~50% des nœuds) : complications, retournements, montée de tension, conséquences des choix passés
            - Acte 3 (~25% des nœuds) : climax et résolutions distinctes selon le parcours du joueur

            RÈGLES DE CONNECTIVITÉ (critiques pour la qualité) :
            - Au moins 30% des nœuds non-terminaux doivent pointer vers un nœud déjà défini (convergence ou boucle)
            - Définir 2 à 4 nœuds "carrefour" : des moments narratifs forts où plusieurs chemins se rejoignent avant de rebrancher
            - Prévoir au moins 3 fins distinctes reflétant des choix moraux ou dramatiques différents
            - Varier la longueur des chemins : certains raccourcis dramatiques, d'autres longs et sinueux
            - INTERDIRE les structures "en étoile" : éviter qu'un seul nœud final concentre tous les chemins

            RÈGLES TECHNIQUES :
            - id entier séquentiel démarrant à 0 ; premier nœud = départ
            - id STRICTEMENT uniques : aucun doublon, chaque nœud a un id distinct
            - Nœuds terminaux : targetNodeIds=[]
            - Nœuds non-terminaux : 2 targetNodeIds minimum
            - ATTRIBUTS : recopie EXACTEMENT les attributs fournis dans la requête (mêmes label, type, value). N'en invente AUCUN, n'en retire aucun, ne change aucun type.
            - Retourner UNIQUEMENT le JSON""";
    }

    private String buildSkeletonUserMessage(GenerateStoryRequestDTO request, int nodeCount) throws Exception {
        String existingJson = objectMapper.writeValueAsString(request.getExistingStory());
        String extra = request.getDescription() != null ? " Instructions supplémentaires : " + request.getDescription() : "";
        return "Étends cette histoire pour atteindre environ " + nodeCount
            + " nœuds au total. Conserve TOUS les nœuds existants et leurs connexions."
            + " Enrichis le graphe avec des convergences et des carrefours narratifs."
            + " Histoire existante : " + existingJson + extra;
    }

    // ===== PHASE 1.5 : CRITIQUE / RÉÉCRITURE TOPOLOGIQUE (Passes B) =====

    /**
     * Renvoie le graphe courant à Claude pour qu'il le critique puis le réécrive.
     * C'est le cœur du gain face à DeepSeek : les convergences deviennent des
     * choix dramatiques motivés, pas un re-câblage aléatoire.
     */
    private Map<String, Object> refineTopology(Map<String, Object> skeleton,
                                               GenerateStoryRequestDTO request,
                                               int passNumber) throws Exception {
        String systemPrompt = buildCriticSystemPrompt();
        String userMessage = "Passe de relecture n°" + passNumber + "."
            + " Voici le graphe actuel à améliorer. Renvoie le MÊME format JSON complet"
            + " (metadata, attributes, nodes) avec une topologie améliorée.\n"
            + objectMapper.writeValueAsString(skeleton);

        Map<String, Object> refined = callClaudeJson("Critique #" + passNumber, systemPrompt, userMessage, STRUCTURE_MAX_TOKENS, true);

        // Le critique peut ne renvoyer que "nodes" : on préserve le reste.
        Map<String, Object> merged = new LinkedHashMap<>(skeleton);
        if (refined.get("metadata") != null) merged.put("metadata", refined.get("metadata"));
        if (refined.get("attributes") != null) merged.put("attributes", refined.get("attributes"));
        if (refined.get("nodes") != null) merged.put("nodes", refined.get("nodes"));
        return merged;
    }

    private String buildCriticSystemPrompt() {
        return """
            Tu es un script-doctor expert en récits interactifs à embranchements. \
            On te donne la STRUCTURE d'une histoire (graphe de nœuds reliés par targetNodeIds). \
            Ta mission : la relire d'un œil critique et la RÉÉCRIRE pour la rendre plus riche.

            Diagnostique d'abord les faiblesses, puis corrige-les :
            - Culs-de-sac prématurés ou fins indistinctes (mêmes conséquences pour des choix opposés)
            - Structures "en étoile" (un nœud qui concentre trop de chemins entrants/sortants)
            - Convergences arbitraires : deux chemins qui se rejoignent sans raison dramatique
            - Branches linéaires sans réel embranchement, ou choix sans conséquence
            - Manque de carrefours forts où le destin du joueur bascule

            Améliore en t'assurant que :
            - Chaque convergence est MOTIVÉE (les chemins se rejoignent à un moment de tension partagée)
            - Il existe 2 à 4 carrefours dramatiques répartis dans l'histoire
            - Il y a au moins 3 fins réellement distinctes
            - La longueur des chemins varie (raccourcis tendus vs parcours longs)
            - Le graphe reste cohérent : tout targetNodeId pointe vers un id existant
            - Les id de nœuds restent STRICTEMENT uniques et séquentiels (0..N-1), aucun doublon
            - Recopie les attributs fournis VERBATIM : n'en ajoute, n'en retire, ni n'en retype aucun

            Conserve les id existants quand c'est possible. Tu peux ajouter, retirer ou recâbler des nœuds. \
            Retourne UNIQUEMENT le JSON complet au même format (metadata, attributes, nodes), sans markdown ni texte.""";
    }

    // ===== PHASE 1.6 : NORMALISATION GÉNÉRIQUE DE LA TOPOLOGIE =====

    /**
     * Traite la sortie de Claude comme NON FIABLE et impose les invariants dont le
     * reste du pipeline dépend. Si des id sont en double (seul cas réellement
     * ambigu — une référence ne sait plus quel jumeau viser), on tente d'abord UNE
     * passe d'auto-réparation par Claude (il connaît son intention) ; ce n'est
     * qu'en dernier recours que l'heuristique déterministe (1ʳᵉ occurrence) tranche.
     *
     * @return l'index id -> cibles validées (la topologie "officielle"), après
     *         renumérotation par position.
     */
    private Map<Integer, List<Integer>> normalizeSkeleton(Map<String, Object> skeleton,
                                                          GenerateStoryRequestDTO request) throws Exception {
        List<Map<String, Object>> nodes = castList(skeleton.get("nodes"));
        if (nodes == null) {
            throw new RuntimeException("Squelette sans nœuds après les passes de structure");
        }

        if (hasDuplicateIds(nodes)) {
            log.warn("Squelette : id en double {} → passe d'auto-réparation Claude", duplicateIds(nodes));
            Map<String, Object> repaired = repairDuplicateIds(skeleton);
            List<Map<String, Object>> repairedNodes = castList(repaired.get("nodes"));
            if (repairedNodes != null && !hasDuplicateIds(repairedNodes)) {
                if (repaired.get("metadata") != null) skeleton.put("metadata", repaired.get("metadata"));
                if (repaired.get("attributes") != null) skeleton.put("attributes", repaired.get("attributes"));
                skeleton.put("nodes", repairedNodes);
                nodes = repairedNodes;
                log.info("Squelette : doublons résolus par auto-réparation Claude");
            } else {
                log.warn("Squelette : doublons toujours présents après réparation "
                    + "→ repli sur l'heuristique déterministe (1ʳᵉ occurrence)");
            }
        }

        return normalizeTopology(nodes);
    }

    /**
     * Cœur déterministe. Identité par POSITION : l'index dans le tableau devient le
     * nouvel id (unicité et contiguïté 0..N-1 gratuites, quoi que Claude ait fait).
     * Chaque référence (valeur d'id) est résolue contre l'index valeur -> positions :
     * 1 occurrence = remap trivial ; 0 = référence morte (supprimée) ; ≥2 = doublon
     * résiduel ambigu (1ʳᵉ occurrence + log). Auto-boucles et arêtes dupliquées retirées.
     * Mute les nœuds en place et renvoie l'index id -> cibles.
     */
    private Map<Integer, List<Integer>> normalizeTopology(List<Map<String, Object>> nodes) {
        int n = nodes.size();

        // valeur d'id telle que générée -> indices (positions) qui la portent
        Map<Integer, List<Integer>> valueToIndices = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            Object idObj = nodes.get(i).get("id");
            if (idObj instanceof Number num) {
                valueToIndices.computeIfAbsent(num.intValue(), k -> new ArrayList<>()).add(i);
            }
        }

        int dropped = 0;
        int ambiguous = 0;
        Map<Integer, List<Integer>> targetsById = new LinkedHashMap<>();

        for (int i = 0; i < n; i++) {
            Map<String, Object> node = nodes.get(i);
            List<Integer> oldTargets = castIntList(node.get("targetNodeIds"));
            List<Integer> newTargets = new ArrayList<>();

            if (oldTargets != null) {
                for (Integer t : oldTargets) {
                    List<Integer> positions = valueToIndices.get(t);
                    if (positions == null || positions.isEmpty()) {
                        dropped++;                 // référence morte
                        continue;
                    }
                    if (positions.size() > 1) {
                        ambiguous++;               // doublon résiduel : 1ʳᵉ occurrence
                    }
                    int target = positions.get(0);
                    if (target == i) {
                        continue;                  // auto-boucle
                    }
                    if (!newTargets.contains(target)) {
                        newTargets.add(target);    // dédup d'arêtes
                    }
                }
            }

            node.put("id", i);                     // identité par position
            node.put("targetNodeIds", newTargets);
            targetsById.put(i, newTargets);
        }

        if (dropped > 0 || ambiguous > 0) {
            log.info("Normalisation topologie : {} réf. mortes supprimées, "
                + "{} réf. ambiguës (doublon) tranchées sur 1ʳᵉ occurrence", dropped, ambiguous);
        }
        return targetsById;
    }

    /**
     * Ordre topologique des nœuds (Kahn) : un nœud apparaît après tous ses
     * prédécesseurs, ce qui garantit que ses incomingChoices sont déjà connus au
     * moment de le remplir. Les nœuds pris dans un cycle (boucle narrative) sont
     * ajoutés ensuite en ordre d'id — dégradation propre, jamais d'exception.
     */
    private List<Map<String, Object>> orderNodesTopologically(
            List<Map<String, Object>> nodes, Map<Integer, List<Integer>> targetsById) {
        Map<Integer, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            if (node.get("id") instanceof Number num) byId.put(num.intValue(), node);
        }

        Map<Integer, Integer> indeg = new HashMap<>();
        for (Integer id : byId.keySet()) indeg.put(id, 0);
        for (List<Integer> targets : targetsById.values()) {
            if (targets == null) continue;
            for (Integer t : targets) {
                if (indeg.containsKey(t)) indeg.merge(t, 1, Integer::sum);
            }
        }

        // ids à degré entrant nul, pris dans l'ordre croissant (déterminisme + lisibilité)
        java.util.PriorityQueue<Integer> ready = new java.util.PriorityQueue<>();
        indeg.forEach((id, d) -> { if (d == 0) ready.add(id); });

        List<Map<String, Object>> ordered = new ArrayList<>(byId.size());
        Set<Integer> placed = new HashSet<>();
        while (!ready.isEmpty()) {
            int u = ready.poll();
            ordered.add(byId.get(u));
            placed.add(u);
            List<Integer> targets = targetsById.get(u);
            if (targets == null) continue;
            for (Integer t : targets) {
                if (!indeg.containsKey(t)) continue;
                if (indeg.merge(t, -1, Integer::sum) == 0) ready.add(t);
            }
        }

        // Cycle(s) résiduel(s) : nœuds jamais retombés à degré 0, ajoutés en ordre d'id
        if (placed.size() < byId.size()) {
            byId.keySet().stream().filter(id -> !placed.contains(id)).sorted()
                .forEach(id -> ordered.add(byId.get(id)));
        }
        return ordered;
    }

    /** Passe ciblée : Claude réécrit le graphe avec des id uniques, en préservant la structure. */
    private Map<String, Object> repairDuplicateIds(Map<String, Object> skeleton) throws Exception {
        String system = """
            Tu corriges les id d'un graphe d'histoire interactive. Les id de nœuds sont
            en double, ce qui rend les targetNodeIds ambigus. Réattribue des id STRICTEMENT
            uniques et séquentiels (0..N-1, le nœud de départ = 0) en PRÉSERVANT la topologie
            voulue : mêmes nœuds, mêmes connexions, aucun chemin perdu ni inventé. Mets à jour
            tous les targetNodeIds en conséquence. Retourne UNIQUEMENT le JSON complet
            (metadata, attributes, nodes), sans markdown ni texte.""";
        String user = "Ce graphe contient des id en double. Réécris-le avec des id uniques"
            + " et séquentiels, sans changer la structure narrative ni les connexions voulues.\n"
            + objectMapper.writeValueAsString(skeleton);
        return callClaudeJson("Réparation id", system, user, STRUCTURE_MAX_TOKENS, true);
    }

    private boolean hasDuplicateIds(List<Map<String, Object>> nodes) {
        Set<Integer> seen = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            if (node.get("id") instanceof Number num && !seen.add(num.intValue())) {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> duplicateIds(List<Map<String, Object>> nodes) {
        Set<Integer> seen = new HashSet<>();
        Set<Integer> dups = new java.util.LinkedHashSet<>();
        for (Map<String, Object> node : nodes) {
            if (node.get("id") instanceof Number num && !seen.add(num.intValue())) {
                dups.add(num.intValue());
            }
        }
        return dups;
    }

    // ===== PHASE 2 : CONTENU =====

    private List<Map<String, Object>> fillNodeBatch(
            List<Map<String, Object>> batch,
            Map<Integer, List<String>> incomingChoicesByNodeId,
            String storyTitle,
            String storyDescription,
            String characters,
            List<Map<String, Object>> storyAttributes,
            List<Map<String, Object>> previousNodes) throws Exception {

        String systemPrompt = buildContentSystemPrompt(storyTitle, storyDescription, characters, storyAttributes);

        // Charge utile : on n'expose au modèle que id + targetNodeIds + les
        // incomingChoices (textes des choix qui mènent à ce nœud) — sans muter le squelette.
        List<Map<String, Object>> payloadNodes = new ArrayList<>();
        for (Map<String, Object> n : batch) {
            Map<String, Object> pn = new LinkedHashMap<>();
            pn.put("id", n.get("id"));
            pn.put("targetNodeIds", n.get("targetNodeIds"));
            Integer id = n.get("id") instanceof Number num ? num.intValue() : null;
            List<String> incoming = id != null ? incomingChoicesByNodeId.get(id) : null;
            if (incoming != null && !incoming.isEmpty()) {
                pn.put("incomingChoices", incoming);
            }
            payloadNodes.add(pn);
        }

        Map<String, Object> userMsg = new LinkedHashMap<>();
        if (!previousNodes.isEmpty()) {
            userMsg.put("previousNodes", previousNodes);
        }
        if (!storyAttributes.isEmpty()) {
            userMsg.put("attributes", storyAttributes);
        }
        userMsg.put("nodes", payloadNodes);

        // Pas de thinking ici : c'est de la rédaction, pas du raisonnement de graphe.
        Map<String, Object> response = callClaudeJson(
            "Contenu (lot)", systemPrompt, objectMapper.writeValueAsString(userMsg), CONTENT_MAX_TOKENS, false);

        // Claude peut sur-générer (renvoyer plus de nœuds que demandé) : on ne garde
        // QUE les ids du lot, sinon on réintroduit des doublons au niveau contenu.
        Set<Integer> requestedIds = new HashSet<>();
        for (Map<String, Object> n : batch) {
            if (n.get("id") instanceof Number num) requestedIds.add(num.intValue());
        }
        List<Map<String, Object>> returned = castList(response.get("nodes"));
        List<Map<String, Object>> kept = new ArrayList<>();
        if (returned != null) {
            for (Map<String, Object> rn : returned) {
                if (rn.get("id") instanceof Number num && requestedIds.contains(num.intValue())) {
                    kept.add(rn);
                }
            }
        }
        return kept;
    }

    private String buildContentSystemPrompt(String title, String description, String characters,
                                            List<Map<String, Object>> storyAttributes) {
        String charactersSection = (characters != null && !characters.isBlank())
            ? "\nPersonnages de l'histoire (utilise UNIQUEMENT ces noms, jamais d'autres) :\n" + characters + "\n"
            : "\nLe protagoniste principal est désigné par ${name} dans les dialogues.\n";

        return "Tu es un scénariste professionnel qui rédige le contenu narratif de l'histoire \""
            + title + "\" (" + description + ")."
            + charactersSection
            + "\nTu maîtrises le tempo, le suspense et la psychologie des personnages. "
            + "Chaque nœud est une scène : elle doit avoir une tension, un enjeu, et laisser le joueur dans l'incertitude.\n\n"
            + "Actions disponibles (utilise UNIQUEMENT celles-ci) :\n"
            + buildNarrativeActionsDoc()
            + "\nActions d'état autorisées PAR attribut (n'applique une action de type Set/Increase/Decrease/... "
            + "à un attribut QUE si elle figure dans sa liste ci-dessous) :\n"
            + buildAttributeActionsDoc(storyAttributes)
            + "\n\nPour chaque nœud reçu, retourne UNIQUEMENT ce JSON :\n"
            + """
                {
                  "nodes": [
                    {
                      "id": 0,
                      "actions": [
                        {"type": "ShowDialog", "params": {"speaker": "Narrateur", "value": "texte narratif"}}
                      ],
                      "choices": [
                        {"choiceText": "texte court et percutant du choix", "conditions": [], "actions": [], "nextNodeId": 1}
                      ]
                    }
                  ]
                }

                RÈGLES NARRATIVES :
                - Chaque ShowDialog doit créer une tension ou une révélation (éviter le descriptif plat)
                - Les textes de choix doivent être des dilemmes réels, pas des options anodines
                - Varier le rythme : certaines scènes courtes et intenses, d'autres plus posées
                - Cohérence dramatique : les nœuds qui convergent doivent se sentir comme des "carrefours du destin"
                - Les nœuds terminaux doivent avoir une conclusion émotionnellement forte

                RÈGLES DE COHÉRENCE :
                - Si "incomingChoices" est présent : la scène DOIT être la conséquence directe de ces choix (le joueur vient de cliquer dessus). Honore leur promesse exacte — ne l'inverse jamais, ne la contredis pas. Si plusieurs choix mènent au nœud, la scène doit rester cohérente avec chacun.
                - Si "previousNodes" est présent : assure la continuité (lieu, moment, ton, personnages)
                - N'utilise QUE les attributs listés dans "attributes" — n'en invente AUCUN. Utilise leurs labels exacts, et respecte les "Actions d'état autorisées PAR attribut" : n'applique à un attribut qu'une action listée pour son type
                - Ne jamais réinventer ou renommer un personnage déjà établi

                RÈGLES TECHNIQUES :
                - Conserver EXACTEMENT le même id que l'entrée
                - Reproduire EXACTEMENT les mêmes nextNodeId dans choices (issus de targetNodeIds, même ordre)
                - Nœuds terminaux (targetNodeIds=[]) : choices = []
                - Ne PAS inclure d'actions d'ambiance (ChangeLocation, PlaySound, StopSound)
                - Retourner UNIQUEMENT le JSON""";
    }

    private String buildNarrativeActionsDoc() {
        Map<String, Map<String, List<String>>> actions = actionRegistry.getNarrativeActions();
        StringBuilder sb = new StringBuilder();
        actions.forEach((name, params) -> {
            sb.append("- ").append(name).append(": {\"type\": \"").append(name).append("\", \"params\": {");
            params.forEach((paramName, types) ->
                sb.append("\"").append(paramName).append("\": \"<").append(String.join("|", types)).append(">\", ")
            );
            if (!params.isEmpty()) sb.setLength(sb.length() - 2);
            sb.append("}}\n");
        });
        return sb.toString();
    }

    /**
     * Pour chaque attribut de l'histoire, liste les actions d'état légales selon son
     * type (DataTypeRegistry / actionsFromTypes.json du repo distant). Évite qu'un
     * Set/Increase/Decrease soit appliqué à un attribut d'un type qui ne le permet pas.
     */
    private String buildAttributeActionsDoc(List<Map<String, Object>> storyAttributes) {
        if (storyAttributes == null || storyAttributes.isEmpty()) {
            return "(aucun attribut déclaré)\n";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> attr : storyAttributes) {
            Object label = attr.get("label");
            String typeName = attr.get("type") instanceof String s ? s : null;
            List<String> allowed = typeName != null ? dataTypeRegistry.getActionsForType(typeName) : List.of();
            sb.append("- ").append(label).append(" (type ").append(typeName).append(") : ");
            if (allowed == null || allowed.isEmpty()) {
                sb.append("aucune action d'état (lecture seule / dialogue uniquement)");
            } else {
                sb.append(String.join(", ", allowed));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** Convertit le schéma d'attributs de la requête (autoritaire) en liste de maps {label,type,value}. */
    private List<Map<String, Object>> requestAttributes(GenerateStoryRequestDTO request) {
        List<Map<String, Object>> out = new ArrayList<>();
        var existing = request.getExistingStory();
        if (existing == null || existing.getAttributes() == null) return out;
        for (AttributeDTO a : existing.getAttributes()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", a.getLabel());
            m.put("type", a.getType());
            m.put("value", a.getValue());
            out.add(m);
        }
        return out;
    }

    // ===== PHASE 2.5 : NORMALISATION =====

    private void sanitizeNodes(List<Map<String, Object>> nodes) {
        for (Map<String, Object> node : nodes) {
            node.put("actions", sanitizeActionList(castList(node.get("actions"))));

            List<Map<String, Object>> choices = castList(node.get("choices"));
            if (choices != null) {
                for (Map<String, Object> choice : choices) {
                    choice.put("actions", sanitizeActionList(castList(choice.get("actions"))));
                    choice.put("conditions", sanitizeActionList(castList(choice.get("conditions"))));
                }
            }
        }
    }

    private List<Map<String, Object>> sanitizeActionList(List<Map<String, Object>> actions) {
        if (actions == null) return List.of();
        return actions.stream()
            .map(this::sanitizeAction)
            .collect(java.util.stream.Collectors.toList());
    }

    private Map<String, Object> sanitizeAction(Map<String, Object> action) {
        if (action == null) return null;
        Object params = action.get("params");
        if (params instanceof Map) return action; // déjà correct

        Map<String, Object> rebuiltParams = new HashMap<>();
        Map<String, Object> sanitized = new HashMap<>();
        action.forEach((key, value) -> {
            if ("type".equals(key)) sanitized.put(key, value);
            else rebuiltParams.put(key, value);
        });
        sanitized.put("params", rebuiltParams);
        return sanitized;
    }

    /**
     * Garde-fou schéma : supprime toute action d'état (identifiée par params.attr)
     * qui cible un attribut hors du schéma autoritaire. Le modèle ne doit utiliser
     * que les attributs fournis dans la requête.
     */
    private void stripUnknownAttributeActions(List<Map<String, Object>> nodes, Set<String> allowedLabels) {
        for (Map<String, Object> node : nodes) {
            node.put("actions", filterAttrActions(castList(node.get("actions")), allowedLabels));
            List<Map<String, Object>> choices = castList(node.get("choices"));
            if (choices != null) {
                for (Map<String, Object> choice : choices) {
                    choice.put("actions", filterAttrActions(castList(choice.get("actions")), allowedLabels));
                }
            }
        }
    }

    private List<Map<String, Object>> filterAttrActions(List<Map<String, Object>> actions, Set<String> allowedLabels) {
        if (actions == null) return new ArrayList<>();
        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> action : actions) {
            if (action != null && action.get("params") instanceof Map<?, ?> params
                    && params.get("attr") instanceof String attr && !allowedLabels.contains(attr)) {
                log.warn("Action ignorée : attribut '{}' hors schéma autoritaire", attr);
                continue;
            }
            kept.add(action);
        }
        return kept;
    }

    // ===== PHASE 2.6 : RÉCONCILIATION AVEC LA TOPOLOGIE VALIDÉE =====

    /**
     * Aligne les choix générés sur la topologie officielle du squelette :
     * on supprime tout choix dont le nextNodeId ne fait pas partie des cibles
     * validées du nœud. Garantit que le graphe final == le graphe conçu.
     */
    private void reconcileChoicesWithSkeleton(List<Map<String, Object>> filledNodes,
                                              Map<Integer, List<Integer>> skeletonTargets) {
        for (Map<String, Object> node : filledNodes) {
            Integer id = node.get("id") instanceof Integer i ? i : null;
            List<Integer> validTargets = id != null ? skeletonTargets.getOrDefault(id, List.of()) : List.of();

            List<Map<String, Object>> choices = castList(node.get("choices"));
            if (choices == null) {
                node.put("choices", new ArrayList<>());
                continue;
            }
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Map<String, Object> choice : choices) {
                if (choice.get("nextNodeId") instanceof Integer next && validTargets.contains(next)) {
                    kept.add(choice);
                }
            }
            node.put("choices", kept);
        }
    }

    // ===== PHASE 3 : ENRICHISSEMENT JAVA =====

    private void enrichNodes(List<Map<String, Object>> filledNodes) {
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(filledNodes.size())));
        for (int i = 0; i < filledNodes.size(); i++) {
            Map<String, Object> node = filledNodes.get(i);
            List<?> choices = (List<?>) node.get("choices");
            boolean isEnd = choices == null || choices.isEmpty();
            node.put("x", (i % cols) * 300);
            node.put("y", (i / cols) * 250);
            node.put("color", isEnd ? END_NODE_COLOR : NODE_COLORS[i % NODE_COLORS.length]);
        }
    }

    // ===== APPEL CLAUDE =====

    /**
     * Appelle Claude puis parse la réponse en JSON. Logge la sortie brute (et,
     * en cas d'échec de parsing, le JSON extrait) en préfixant par la phase
     * ("Passe A", "Critique #1", "Contenu (lot)") pour un diagnostic immédiat.
     */
    private Map<String, Object> callClaudeJson(String phase, String systemPrompt, String userMessage,
                                               long maxTokens, boolean reasoning) {
        String raw = callClaude(systemPrompt, userMessage, maxTokens, reasoning);
        log.info("[Claude/{}] {} caractères reçus", phase, raw.length());
        log.debug("[Claude/{}] réponse brute :\n{}", phase, raw);

        String json = extractJson(raw);
        try {
            return LENIENT_JSON.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("[Claude/{}] JSON invalide : {}\n--- JSON extrait ---\n{}", phase, e.getMessage(), json);
            throw new RuntimeException("Réponse Claude non parsable (" + phase + ") : " + e.getMessage(), e);
        }
    }

    /**
     * Un aller-retour vers Claude. Le system prompt est mis en cache (prompt
     * caching) : les passes qui partagent le même system (les 2 passes critique,
     * tous les lots de contenu) ne le repaient qu'une fois.
     *
     * @param reasoning true pour les passes de structure (adaptive thinking +
     *                  effort élevé) ; false pour la rédaction de contenu.
     */
    private String callClaude(String systemPrompt, String userMessage, long maxTokens, boolean reasoning) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .systemOfTextBlockParams(List.of(
                TextBlockParam.builder()
                    .text(systemPrompt)
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build()))
            .addUserMessage(userMessage);

        if (reasoning) {
            builder.thinking(ThinkingConfigAdaptive.builder().build())
                   .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.HIGH).build());
        }

        Message response = anthropicClient.messages().create(builder.build());

        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(t -> sb.append(t.text()));
        }
        return sb.toString();
    }

    /**
     * Isole le PREMIER objet JSON complet de la réponse par équilibrage des
     * accolades (en respectant les chaînes et l'échappement). Ignore donc toute
     * prose que Claude ajouterait avant ou après — y compris si elle contient
     * des accolades. Bien plus robuste qu'un simple premier '{' → dernier '}'.
     */
    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Réponse vide de Claude");
        }
        String s = raw.trim();
        int start = s.indexOf('{');
        if (start < 0) {
            throw new RuntimeException("Aucun objet JSON dans la réponse Claude : " + s);
        }

        boolean inString = false;
        boolean escape = false;
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1);
                }
            }
        }
        throw new RuntimeException("Objet JSON non terminé dans la réponse Claude (tronqué ?)");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object obj) {
        return (List<Map<String, Object>>) obj;
    }

    /** Convertit une liste JSON de nombres en List&lt;Integer&gt; (tolère Long/Double). */
    private List<Integer> castIntList(Object obj) {
        if (!(obj instanceof List<?> list)) return null;
        List<Integer> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number num) out.add(num.intValue());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object obj) {
        return (Map<String, Object>) obj;
    }
}
