package org.tpi.questlytales.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tpi.questlytales.dtos.ActionDTO;
import org.tpi.questlytales.dtos.AttributeDTO;
import org.tpi.questlytales.dtos.ChoiceDTO;
import org.tpi.questlytales.dtos.ConditionDTO;
import org.tpi.questlytales.dtos.storynodedtos.EditorNodeResponseDTO;
import org.tpi.questlytales.dtos.storydtos.StorySubmissionDTO;
import org.tpi.questlytales.models.DynamicDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StoryValidationService {

    @Autowired
    private DataTypeRegistry dataTypeRegistry;

    @Autowired
    private ActionRegistry actionRegistry;

    @Autowired
    private ConditionRegistry conditionRegistry;

    public ValidationResult validate(StorySubmissionDTO dto) {
        List<String> errors = new ArrayList<>();

        if (dto.getMetadata() == null || dto.getMetadata().getTitle() == null
                || dto.getMetadata().getTitle().trim().isEmpty()) {
            errors.add("Le titre est obligatoire");
        }

        if (dto.getNodes() == null || dto.getNodes().isEmpty()) {
            errors.add("Au moins un nœud est requis");
        }

        if (dto.getAttributes() != null) {
            validateAttributes(dto.getAttributes(), errors);
        }

        // Table label -> type : les actions/conditions référencent l'attribut qu'elles
        // manipulent par son label (paramètre "attr"), et la validation n'a besoin que
        // du type pour vérifier la signature. Une seule map suffit à la méthode générique.
        Map<String, String> attributeTypesByLabel = mapAttributeTypesByLabel(dto.getAttributes());

        if (dto.getNodes() != null && !dto.getNodes().isEmpty()) {
            validateNodes(dto.getNodes(), attributeTypesByLabel, errors);
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    private Map<String, String> mapAttributeTypesByLabel(List<AttributeDTO> attributes) {
        Map<String, String> typesByLabel = new LinkedHashMap<>();
        if (attributes != null) {
            for (AttributeDTO attr : attributes) {
                if (attr.getLabel() != null) {
                    // La valeur peut être null : on garde l'entrée pour distinguer
                    // « attribut absent » de « attribut sans type ».
                    typesByLabel.put(attr.getLabel(), attr.getType());
                }
            }
        }
        return typesByLabel;
    }

    private void validateAttributes(List<AttributeDTO> attributes, List<String> errors) {
        for (int i = 0; i < attributes.size(); i++) {
            AttributeDTO attr = attributes.get(i);
            String prefix = "Attribut '" + attr.getLabel() + "': ";

            if (attr.getLabel() == null || attr.getLabel().trim().isEmpty()) {
                errors.add("Attribut[" + i + "]: Le label est obligatoire");
                continue;
            }

            if (attr.getType() == null) {
                errors.add(prefix + "Le type est obligatoire");
                continue;
            }

            if (!dataTypeRegistry.typeExists(attr.getType())) {
                errors.add(prefix + "Type inconnu '" + attr.getType() + "'. Types disponibles: "
                    + dataTypeRegistry.getAllTypeNames());
                continue;
            }

            DynamicDataType dataType = dataTypeRegistry.getType(attr.getType()).orElse(null);
            if (dataType == null) continue;

            if (attr.getValue() != null && !attr.getValue().trim().isEmpty()) {
                if (!validateValue(attr.getValue(), dataType)) {
                    errors.add(prefix + "Valeur invalide '" + attr.getValue() + "' pour le type '" + attr.getType() + "'");
                }
            }
        }
    }

    private void validateNodes(List<EditorNodeResponseDTO> nodes, Map<String, String> attributeTypesByLabel, List<String> errors) {
        Set<Integer> nodeIds = nodes.stream()
            .map(EditorNodeResponseDTO::getId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        if (nodeIds.size() < nodes.size()) {
            errors.add("Des nœuds ont des IDs dupliqués ou vides");
        }

        for (EditorNodeResponseDTO node : nodes) {
            String prefix = "Nœud '" + node.getId() + "': ";

            if (node.getId() == null) {
                errors.add("L'ID du nœud est obligatoire");
                continue;
            }

            // Les coordonnees x/y sont des positions sur le canvas de l'editeur : elles
            // peuvent legitimement etre negatives (noeud place a gauche/au-dessus de l'origine).

            if (node.getActions() != null) {
                validateActions(node.getActions(), attributeTypesByLabel, prefix, errors);
            }

            if (node.getChoices() != null) {
                validateChoices(node.getChoices(), node.getId(), nodeIds, attributeTypesByLabel, errors);
            }
        }
    }

    private void validateActions(List<ActionDTO> actions, Map<String, String> attributeTypesByLabel,
                                 String prefix, List<String> errors) {
        Map<String, Map<String, List<String>>> signatures = actionRegistry.getAllActions();
        for (ActionDTO action : actions) {
            if (action.getType() == null || action.getType().trim().isEmpty()) {
                errors.add(prefix + "Une action doit avoir un type");
                continue;
            }

            Map<String, List<String>> signature = signatures.get(action.getType());
            if (signature == null) {
                errors.add(prefix + "Action inconnue '" + action.getType() + "'. Actions disponibles: "
                    + signatures.keySet());
                continue;
            }

            validateAgainstSignature("Action", action.getType(), signature, action.getParams(),
                attributeTypesByLabel, prefix, errors);
        }
    }

    private void validateChoices(List<ChoiceDTO> choices, Integer sourceNodeId, Set<Integer> allNodeIds,
                                 Map<String, String> attributeTypesByLabel, List<String> errors) {
        Map<String, Map<String, List<String>>> conditionSignatures = conditionRegistry.getAllConditions();
        for (ChoiceDTO choice : choices) {
            String prefix = "Nœud '" + sourceNodeId + "' > Choix: ";

            if (choice.getConditions() != null) {
                for (ConditionDTO condition : choice.getConditions()) {
                    if (condition.getType() == null || condition.getType().trim().isEmpty()) {
                        errors.add(prefix + "Une condition doit avoir un type");
                        continue;
                    }

                    Map<String, List<String>> signature = conditionSignatures.get(condition.getType());
                    if (signature == null) {
                        errors.add(prefix + "Condition inconnue '" + condition.getType()
                            + "'. Conditions disponibles: " + conditionSignatures.keySet());
                        continue;
                    }

                    validateAgainstSignature("Condition", condition.getType(), signature,
                        condition.getParams(), attributeTypesByLabel, prefix, errors);
                }
            }

            if (choice.getActions() != null) {
                validateActions(choice.getActions(), attributeTypesByLabel, prefix, errors);
            }

            // nextNodeId null = end node, valid
            if (choice.getNextNodeId() != null && !allNodeIds.contains(choice.getNextNodeId())) {
                errors.add(prefix + "Le nœud cible '" + choice.getNextNodeId() + "' n'existe pas");
            }
        }
    }

    /**
     * Valide les paramètres d'une action ou d'une condition contre sa signature distante.
     * <p>
     * Une signature a la forme {@code {"attr": ["attrInt","attrFloat"], "value": ["Int","Float"]}} :
     * <ul>
     *   <li>{@code attr} : l'attribut référencé (par son label) doit exister et son type, préfixé
     *       par {@code "attr"}, doit figurer dans la liste autorisée ;</li>
     *   <li>{@code value} : la valeur doit correspondre — son type doit être celui aligné
     *       positionnellement avec le type d'attribut accepté (attrInt → Int, attrFloat → Float) ;</li>
     *   <li>tout autre paramètre (speaker, loop, …) est contrôlé contre ses types autorisés.</li>
     * </ul>
     */
    private void validateAgainstSignature(String kind, String type, Map<String, List<String>> signature,
                                          Map<String, Object> rawParams, Map<String, String> attributeTypesByLabel,
                                          String prefix, List<String> errors) {
        Map<String, Object> params = rawParams != null ? rawParams : Map.of();
        String label = kind + " '" + type + "': ";

        // 1. Paramètre "attr" : résolution du type de l'attribut référencé + contrôle
        Integer attrIndex = null; // index du type d'attribut accepté, sert à aligner la valeur
        if (signature.containsKey("attr")) {
            List<String> allowedAttrTypes = signature.get("attr");
            Object attrRef = params.get("attr");

            if (attrRef == null || attrRef.toString().trim().isEmpty()) {
                errors.add(prefix + label + "le paramètre 'attr' est obligatoire");
            } else {
                String attrLabel = attrRef.toString();
                String attributeType = attributeTypesByLabel.get(attrLabel);
                if (!attributeTypesByLabel.containsKey(attrLabel)) {
                    errors.add(prefix + label + "l'attribut '" + attrLabel + "' n'existe pas");
                } else if (attributeType == null || attributeType.trim().isEmpty()) {
                    errors.add(prefix + label + "l'attribut '" + attrLabel + "' n'a pas de type");
                } else {
                    String attrToken = "attr" + capitalize(attributeType);
                    attrIndex = allowedAttrTypes.indexOf(attrToken);
                    if (attrIndex < 0) {
                        errors.add(prefix + label + "ne s'applique pas à un attribut de type '"
                            + attributeType + "'. Types acceptés: " + stripAttrPrefix(allowedAttrTypes));
                    }
                }
            }
        }

        // 2. Paramètre "value" : la valeur doit correspondre au type attendu
        if (signature.containsKey("value")) {
            List<String> valueTypes = signature.get("value");
            Object value = params.get("value");

            if (value == null) {
                errors.add(prefix + label + "le paramètre 'value' est obligatoire");
            } else if (attrIndex != null && attrIndex >= 0 && attrIndex < valueTypes.size()) {
                // Correspondance positionnelle avec l'attribut (ex: attrInt → Int)
                String expected = valueTypes.get(attrIndex);
                if (!valueMatchesType(value, expected)) {
                    errors.add(prefix + label + "la valeur '" + value + "' doit être de type " + expected
                        + " (pour correspondre à l'attribut)");
                }
            } else if (attrIndex == null) {
                // Pas d'attribut associé : la valeur doit matcher l'un des types autorisés
                boolean ok = valueTypes.stream().anyMatch(t -> valueMatchesType(value, t));
                if (!ok) {
                    errors.add(prefix + label + "la valeur '" + value + "' doit être de type "
                        + String.join(" ou ", valueTypes));
                }
            }
        }

        // 3. Autres paramètres (speaker, loop, …) : contrôle de type best-effort
        for (Map.Entry<String, List<String>> entry : signature.entrySet()) {
            String key = entry.getKey();
            if ("attr".equals(key) || "value".equals(key)) continue;
            Object value = params.get(key);
            if (value == null) continue; // paramètres secondaires tolérés s'ils sont absents
            boolean ok = entry.getValue().stream().anyMatch(t -> valueMatchesType(value, t));
            if (!ok) {
                errors.add(prefix + label + "le paramètre '" + key + "' doit être de type "
                    + String.join(" ou ", entry.getValue()));
            }
        }
    }

    /** Vérifie qu'une valeur (issue du JSON) est compatible avec un type de signature. */
    private boolean valueMatchesType(Object value, String type) {
        if (value == null) return false;
        String s = value.toString().trim();
        switch (type) {
            case "Any":
                return true;
            case "String":
                return value instanceof CharSequence;
            case "Boolean":
                if (value instanceof Boolean) return true;
                return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false");
            case "Int":
                if (value instanceof Integer || value instanceof Long) return true;
                try { Long.parseLong(s); return true; } catch (NumberFormatException e) { return false; }
            case "Float":
                if (value instanceof Number) return true;
                try { Double.parseDouble(s); return true; } catch (NumberFormatException e) { return false; }
            case "List":
                return value instanceof List;
            default:
                return true; // type inconnu de la signature : ne pas bloquer
        }
    }

    /** "Int" -> "attrInt" se construit en capitalisant ; ici on normalise le type d'attribut. */
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    /** Affiche ["attrInt","attrFloat"] sous une forme lisible ["Int","Float"] pour les messages. */
    private List<String> stripAttrPrefix(List<String> attrTypes) {
        return attrTypes.stream()
            .map(t -> t.startsWith("attr") ? t.substring(4) : t)
            .collect(Collectors.toList());
    }

    private boolean validateValue(String value, DynamicDataType dataType) {
        Class<?> javaType = dataType.getJavaType();

        if (javaType == Boolean.class) {
            String lower = value.toLowerCase();
            return lower.equals("true") || lower.equals("false");
        }

        try {
            if (javaType == Integer.class) {
                Integer.parseInt(value);
            } else if (javaType == Long.class) {
                Long.parseLong(value);
            } else if (javaType == Float.class) {
                Float.parseFloat(value);
            } else if (javaType == Double.class) {
                Double.parseDouble(value);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
