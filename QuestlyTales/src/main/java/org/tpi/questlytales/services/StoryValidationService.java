package org.tpi.questlytales.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tpi.questlytales.dtos.ActionDTO;
import org.tpi.questlytales.dtos.AttributeDTO;
import org.tpi.questlytales.dtos.ChoiceDTO;
import org.tpi.questlytales.dtos.ConditionDTO;
import org.tpi.questlytales.dtos.storydtos.StorySubmissionDTO;
import org.tpi.questlytales.dtos.storynodedtos.EditorNodeResponseDTO;
import org.tpi.questlytales.models.DynamicDataType;

@Service
public class StoryValidationService {

    @Autowired
    private DataTypeRegistry dataTypeRegistry;

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

        if (dto.getNodes() != null && !dto.getNodes().isEmpty()) {
            validateNodes(dto.getNodes(), errors);
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
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

    private void validateNodes(List<EditorNodeResponseDTO> nodes, List<String> errors) {
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
                validateActions(node.getActions(), prefix, errors);
            }

            if (node.getChoices() != null) {
                validateChoices(node.getChoices(), node.getId(), nodeIds, errors);
            }
        }
    }

    private void validateActions(List<ActionDTO> actions, String prefix, List<String> errors) {
        for (ActionDTO action : actions) {
            if (action.getType() == null || action.getType().trim().isEmpty()) {
                errors.add(prefix + "Une action doit avoir un type");
            }
        }
    }

    private void validateChoices(List<ChoiceDTO> choices, Integer sourceNodeId, Set<Integer> allNodeIds, List<String> errors) {
        for (ChoiceDTO choice : choices) {
            String prefix = "Nœud '" + sourceNodeId + "' > Choix: ";

            if (choice.getConditions() != null) {
                for (ConditionDTO condition : choice.getConditions()) {
                    if (condition.getType() == null || condition.getType().trim().isEmpty()) {
                        errors.add(prefix + "Une condition doit avoir un type");
                    }
                }
            }

            if (choice.getActions() != null) {
                validateActions(choice.getActions(), prefix, errors);
            }

            // nextNodeId null = end node, valid
            if (choice.getNextNodeId() != null && !allNodeIds.contains(choice.getNextNodeId())) {
                errors.add(prefix + "Le nœud cible '" + choice.getNextNodeId() + "' n'existe pas");
            }
        }
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
