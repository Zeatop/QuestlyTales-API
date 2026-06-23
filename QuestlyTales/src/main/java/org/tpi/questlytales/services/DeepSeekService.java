package org.tpi.questlytales.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.tpi.questlytales.dtos.GenerateStoryRequestDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class DeepSeekService implements StoryGenerator {

    private static final int MAX_NODE_COUNT = 200;
    private static final int CONTENT_BATCH_SIZE = 3;

    private static final String[] NODE_COLORS = {
        "#4A90D9", "#27AE60", "#8E44AD", "#E67E22", "#16A085",
        "#2980B9", "#D35400", "#1ABC9C", "#9B59B6", "#2ECC71"
    };
    private static final String END_NODE_COLOR = "#E74C3C";

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Autowired
    private DataTypeRegistry dataTypeRegistry;

    @Autowired
    private ActionRegistry actionRegistry;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ===== POINT D'ENTRÉE =====

    public Map<String, Object> generateStory(GenerateStoryRequestDTO request) throws Exception {
        int minNodes = Objects.requireNonNullElse(request.getMinNodeCount(), 5);
        if (minNodes > MAX_NODE_COUNT) {
            throw new IllegalArgumentException(
                "minNodeCount ne peut pas dépasser " + MAX_NODE_COUNT + ". Valeur reçue : " + minNodes
            );
        }

        // Phase 1 — Structure (skeleton)
        Map<String, Object> skeleton = generateSkeleton(request, minNodes);
        Map<String, Object> skeletonMeta = castMap(skeleton.get("metadata"));
        String title = skeletonMeta != null ? (String) skeletonMeta.get("title") : null;
        String storyDesc = skeletonMeta != null ? (String) skeletonMeta.get("description") : null;
        List<Map<String, Object>> skeletonNodes = castList(skeleton.get("nodes"));
        List<Map<String, Object>> storyAttributes = castList(skeleton.getOrDefault("attributes", List.of()));
        String characters = storyAttributes.stream()
            .filter(a -> "characters".equals(a.get("label")))
            .map(a -> (String) a.get("value"))
            .findFirst()
            .orElse(null);

        // Phase 1.5 — Correction topologique du graphe
        rewriteGraph(skeletonNodes);

        // Phase 2 — Contenu narratif par lots avec contexte glissant
        List<Map<String, Object>> filledNodes = new ArrayList<>();
        for (int i = 0; i < skeletonNodes.size(); i += CONTENT_BATCH_SIZE) {
            int end = Math.min(i + CONTENT_BATCH_SIZE, skeletonNodes.size());
            List<Map<String, Object>> batch = skeletonNodes.subList(i, end);
            int ctxStart = Math.max(0, filledNodes.size() - 3);
            List<Map<String, Object>> previousBatch = filledNodes.subList(ctxStart, filledNodes.size());
            filledNodes.addAll(fillNodeBatch(batch, title, storyDesc, characters, storyAttributes, previousBatch));
        }

        // Phase 2.5 — Normalisation : corrige les actions sans wrapper "params"
        sanitizeNodes(filledNodes);

        // Phase 2.6 — Correction des nextNodeId cassés générés par le LLM en Phase 2
        fixBrokenChoiceRefs(filledNodes);

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
        result.put("attributes", skeleton.getOrDefault("attributes", List.of()));
        result.put("images", List.of());
        result.put("videos", List.of());
        result.put("sounds", List.of());
        return result;
    }

    // ===== PHASE 1 : SKELETON =====

    private Map<String, Object> generateSkeleton(GenerateStoryRequestDTO request, int nodeCount) throws Exception {
        String systemPrompt = buildSkeletonSystemPrompt();
        String userMessage = buildSkeletonUserMessage(request, nodeCount);
        String json = callDeepSeek(systemPrompt, userMessage, 8192);
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private String buildSkeletonSystemPrompt() {
        String availableTypes = String.join(", ", dataTypeRegistry.getAllTypeNames());
        return """
            Tu es un scénariste professionnel expert en récits interactifs. \
            Tu maîtrises les arcs dramatiques, le tempo et le suspense. \
            Ta mission : concevoir la STRUCTURE d'une histoire interactive à embranchements riches. \
            Retourne UNIQUEMENT un JSON valide, sans markdown ni texte.

            FORMAT EXACT :
            {
              "metadata": {"title": "string", "description": "string"},
              "attributes": [
                {"label": "Vie", "type": "Int", "value": "100"},
                {"label": "autreAttribut", "type": "TYPE", "value": "valeurInitiale"}
              ],
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
            - Nœuds terminaux : targetNodeIds=[]
            - Nœuds non-terminaux : 2 targetNodeIds minimum
            - Histoire centrée sur le joueur : le protagoniste est le joueur (désigné par ${name}) et le narrateur porte le récit. N'impose PAS de casting de personnages nommés ; des figurants ponctuels sont tolérés sans en faire des personnages principaux
            - Types d'attributs disponibles : """
            + availableTypes + "\n"
            + "            - Retourner UNIQUEMENT le JSON";
    }

    private String buildSkeletonUserMessage(GenerateStoryRequestDTO request, int nodeCount) throws Exception {
        String existingJson = objectMapper.writeValueAsString(request.getExistingStory());
        String extra = request.getDescription() != null ? " Instructions supplémentaires : " + request.getDescription() : "";
        return "Étends cette histoire pour atteindre environ " + nodeCount
            + " nœuds au total. Conserve TOUS les nœuds existants et leurs connexions."
            + " Enrichis le graphe avec des convergences et des carrefours narratifs."
            + " Histoire existante : " + existingJson + extra;
    }

    // ===== PHASE 2 : CONTENU =====

    private List<Map<String, Object>> fillNodeBatch(
            List<Map<String, Object>> batch,
            String storyTitle,
            String storyDescription,
            String characters,
            List<Map<String, Object>> storyAttributes,
            List<Map<String, Object>> previousNodes) throws Exception {

        String systemPrompt = buildContentSystemPrompt(storyTitle, storyDescription, characters);

        Map<String, Object> userMsg = new LinkedHashMap<>();
        if (!previousNodes.isEmpty()) {
            userMsg.put("previousNodes", previousNodes);
        }
        if (!storyAttributes.isEmpty()) {
            userMsg.put("attributes", storyAttributes);
        }
        userMsg.put("nodes", batch);

        String json = callDeepSeek(systemPrompt, objectMapper.writeValueAsString(userMsg), 16384);
        Map<String, Object> response = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        return castList(response.get("nodes"));
    }

    private String buildContentSystemPrompt(String title, String description, String characters) {
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
                - Si "previousNodes" est présent : assure la continuité (lieu, moment, ton, personnages)
                - Si "attributes" est présent : utilise leurs labels exacts dans les actions Set/Increase/Decrease
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

    // ===== PHASE 1.5 : CORRECTION TOPOLOGIQUE =====

    /**
     * Corrige le graphe skeleton avant la génération de contenu :
     * - Répare les références cassées (targetNodeId inexistant)
     * - Injecte des convergences dans la seconde moitié de l'histoire
     * - Désigne de vraies fins pour les nœuds sans cibles valides
     */
    @SuppressWarnings("unchecked")
    private void rewriteGraph(List<Map<String, Object>> nodes) {
        int size = nodes.size();
        if (size < 4) return;

        // Index des IDs valides
        Set<Integer> validIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            if (node.get("id") instanceof Integer id) validIds.add(id);
        }

        // Carrefours dramatiques aux 3 actes
        int hub1 = nodes.get(size / 4).get("id") instanceof Integer i1 ? i1 : size / 4;
        int hub2 = nodes.get(size / 2).get("id") instanceof Integer i2 ? i2 : size / 2;
        int hub3 = nodes.get(3 * size / 4).get("id") instanceof Integer i3 ? i3 : 3 * size / 4;

        for (int i = 0; i < size; i++) {
            Map<String, Object> node = nodes.get(i);
            List<Integer> targets = (List<Integer>) node.get("targetNodeIds");
            if (targets == null || targets.isEmpty()) continue;

            List<Integer> fixed = new ArrayList<>();
            for (Integer target : targets) {
                if (validIds.contains(target)) {
                    fixed.add(target);
                } else {
                    // Référence cassée : remplacer par le carrefour de l'acte suivant
                    fixed.add(nearestHub(i, size, hub1, hub2, hub3));
                }
            }

            // Convergence supplémentaire : 30% des nœuds de la seconde moitié
            // voient leur premier choix rewire vers un carrefour antérieur
            if (i > size / 2 && fixed.size() >= 2 && Math.random() < 0.30) {
                int convergenceTarget = (i < 3 * size / 4) ? hub1 : hub2;
                // Ne pas créer de boucle sur soi-même
                Integer self = (Integer) node.get("id");
                if (self == null || convergenceTarget != self) {
                    fixed.set(0, convergenceTarget);
                }
            }

            node.put("targetNodeIds", fixed);
        }

        // Désigner de vraies fins : nœuds dont toutes les cibles sont des hubs déjà très ciblés
        // → les nœuds de la dernière tranche sans targetNodeIds valides deviennent des end nodes
        for (int i = 3 * size / 4; i < size; i++) {
            Map<String, Object> node = nodes.get(i);
            List<Integer> targets = (List<Integer>) node.get("targetNodeIds");
            if (targets == null || targets.isEmpty()) {
                node.put("targetNodeIds", List.of());
            }
        }
    }

    /**
     * Seconde passe après Phase 2 : corrige les nextNodeId dans les choices
     * qui ne correspondent à aucun nœud existant (le LLM peut dériver des targetNodeIds corrigés).
     */
    private void fixBrokenChoiceRefs(List<Map<String, Object>> filledNodes) {
        int size = filledNodes.size();
        if (size < 4) return;

        Set<Integer> validIds = new HashSet<>();
        for (Map<String, Object> node : filledNodes) {
            if (node.get("id") instanceof Integer id) validIds.add(id);
        }

        int hub1 = filledNodes.get(size / 4).get("id") instanceof Integer i1 ? i1 : size / 4;
        int hub2 = filledNodes.get(size / 2).get("id") instanceof Integer i2 ? i2 : size / 2;
        int hub3 = filledNodes.get(3 * size / 4).get("id") instanceof Integer i3 ? i3 : 3 * size / 4;

        for (int i = 0; i < size; i++) {
            List<Map<String, Object>> choices = castList(filledNodes.get(i).get("choices"));
            if (choices == null) continue;
            for (Map<String, Object> choice : choices) {
                if (choice.get("nextNodeId") instanceof Integer next && !validIds.contains(next)) {
                    choice.put("nextNodeId", nearestHub(i, size, hub1, hub2, hub3));
                }
            }
        }
    }

    private int nearestHub(int nodeIndex, int total, int hub1, int hub2, int hub3) {
        if (nodeIndex < total / 3) return hub1;
        if (nodeIndex < 2 * total / 3) return hub2;
        return hub3;
    }

    // ===== PHASE 2.5 : NORMALISATION =====

    /**
     * Corrige les actions générées sans wrapper "params" par le LLM.
     * Ex: {"type":"ShowDialog","speaker":"X","value":"Y"}
     * devient: {"type":"ShowDialog","params":{"speaker":"X","value":"Y"}}
     */
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

        // "params" absent ou non-Map : on regroupe tout sauf "type" dedans
        Map<String, Object> rebuiltParams = new HashMap<>();
        Map<String, Object> sanitized = new HashMap<>();
        action.forEach((key, value) -> {
            if ("type".equals(key)) sanitized.put(key, value);
            else rebuiltParams.put(key, value);
        });
        sanitized.put("params", rebuiltParams);
        return sanitized;
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

    // ===== HTTP =====

    @SuppressWarnings({"unchecked", "null"})
    private String callDeepSeek(String systemPrompt, String userMessage, int maxTokens) throws Exception {
        Map<String, Object> body = Map.of(
            "model", "deepseek-chat",
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "response_format", Map.of("type", "json_object"),
            "max_tokens", maxTokens
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<String> response = restTemplate.exchange(
            apiUrl, HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class
        );

        String rawBody = response.getBody();
        if (rawBody == null || rawBody.isBlank()) throw new RuntimeException("Réponse vide de DeepSeek");

        Map<String, Object> responseBody = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");
        if (content == null || content.isBlank()) throw new RuntimeException("Contenu vide dans la réponse DeepSeek");
        return content;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object obj) {
        return (List<Map<String, Object>>) obj;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object obj) {
        return (Map<String, Object>) obj;
    }
}
