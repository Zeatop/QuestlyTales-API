package org.tpi.questlytales.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tpi.questlytales.dtos.ActionDTO;
import org.tpi.questlytales.dtos.AttributeDTO;
import org.tpi.questlytales.dtos.ChoiceDTO;
import org.tpi.questlytales.dtos.ConditionDTO;
import org.tpi.questlytales.dtos.storydtos.StoryMetadataDTO;
import org.tpi.questlytales.dtos.storydtos.StorySubmissionDTO;
import org.tpi.questlytales.dtos.storynodedtos.EditorNodeResponseDTO;
import org.tpi.questlytales.models.DynamicDataType;
import org.tpi.questlytales.services.StoryValidationService.ValidationResult;

@ExtendWith(MockitoExtension.class)
class StoryValidationServiceTest {

    @Mock
    private DataTypeRegistry dataTypeRegistry;

    @Mock
    private ActionRegistry actionRegistry;

    @Mock
    private ConditionRegistry conditionRegistry;

    @InjectMocks
    private StoryValidationService validationService;

    // ===== Helpers =====

    private StoryMetadataDTO metadata(String title) {
        StoryMetadataDTO meta = new StoryMetadataDTO();
        meta.setTitle(title);
        return meta;
    }

    private EditorNodeResponseDTO node(Integer id) {
        EditorNodeResponseDTO node = new EditorNodeResponseDTO();
        node.setId(id);
        return node;
    }

    private boolean errorsContain(ValidationResult result, String fragment) {
        return result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains(fragment.toLowerCase()));
    }

    // ===== Tests =====

    @Test
    void validMinimalStory_passes() {
        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(metadata("Mon histoire"));
        dto.setNodes(List.of(node(1)));

        ValidationResult result = validationService.validate(dto);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void missingTitle_fails() {
        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(metadata("  "));
        dto.setNodes(List.of(node(1)));

        ValidationResult result = validationService.validate(dto);

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "titre"));
    }

    @Test
    void noNodes_fails() {
        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(metadata("Mon histoire"));
        dto.setNodes(List.of());

        ValidationResult result = validationService.validate(dto);

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "nœud"));
    }

    @Test
    void unknownAttributeType_fails() {
        when(dataTypeRegistry.typeExists("Unknown")).thenReturn(false);
        when(dataTypeRegistry.getAllTypeNames()).thenReturn(Set.of("Int", "String"));

        AttributeDTO attr = new AttributeDTO();
        attr.setLabel("hp");
        attr.setType("Unknown");

        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(metadata("Mon histoire"));
        dto.setNodes(List.of(node(1)));
        dto.setAttributes(List.of(attr));

        ValidationResult result = validationService.validate(dto);

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "type inconnu"));
    }

    @Test
    void choiceTargetingMissingNode_fails() {
        ChoiceDTO choice = new ChoiceDTO();
        choice.setNextNodeId(99); // n'existe pas
        EditorNodeResponseDTO node = node(1);
        node.setChoices(List.of(choice));

        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(metadata("Mon histoire"));
        dto.setNodes(List.of(node));

        ValidationResult result = validationService.validate(dto);

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "n'existe pas"));
    }

    @Test
    void duplicateNodeIds_fails() {
        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(metadata("Mon histoire"));
        dto.setNodes(List.of(node(1), node(1)));

        ValidationResult result = validationService.validate(dto);

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "dupliqu"));
    }

    // ===== Validation des actions contre les signatures distantes =====

    @Test
    void increaseOnIntAttribute_withIntValue_passes() {
        stubAttributeType("Int");
        when(actionRegistry.getAllActions()).thenReturn(actionSignatures());

        ActionDTO increase = new ActionDTO("Increase", Map.of("attr", "hp", "value", "5"));
        ValidationResult result = validationService.validate(
            storyWith(attribute("hp", "Int"), nodeWithActions(1, increase)));

        assertTrue(result.isValid(), () -> "Erreurs inattendues: " + result.getErrors());
    }

    @Test
    void increaseOnIntAttribute_withNonNumericValue_fails() {
        stubAttributeType("Int");
        when(actionRegistry.getAllActions()).thenReturn(actionSignatures());

        ActionDTO increase = new ActionDTO("Increase", Map.of("attr", "hp", "value", "beaucoup"));
        ValidationResult result = validationService.validate(
            storyWith(attribute("hp", "Int"), nodeWithActions(1, increase)));

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "doit être de type Int"));
    }

    @Test
    void increaseOnStringAttribute_fails() {
        stubAttributeType("String");
        when(actionRegistry.getAllActions()).thenReturn(actionSignatures());

        ActionDTO increase = new ActionDTO("Increase", Map.of("attr", "nom", "value", "5"));
        ValidationResult result = validationService.validate(
            storyWith(attribute("nom", "String"), nodeWithActions(1, increase)));

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "ne s'applique pas à un attribut de type 'String'"));
    }

    @Test
    void actionReferencingUnknownAttribute_fails() {
        when(actionRegistry.getAllActions()).thenReturn(actionSignatures());

        ActionDTO increase = new ActionDTO("Increase", Map.of("attr", "fantome", "value", "5"));
        EditorNodeResponseDTO node = nodeWithActions(1, increase);

        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(metadata("Mon histoire"));
        dto.setNodes(List.of(node));

        ValidationResult result = validationService.validate(dto);

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "l'attribut 'fantome' n'existe pas"));
    }

    @Test
    void unknownActionType_fails() {
        when(actionRegistry.getAllActions()).thenReturn(actionSignatures());

        ActionDTO bogus = new ActionDTO("Teleport", Map.of("attr", "hp", "value", "5"));
        EditorNodeResponseDTO node = nodeWithActions(1, bogus);

        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(metadata("Mon histoire"));
        dto.setNodes(List.of(node));

        ValidationResult result = validationService.validate(dto);

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "action inconnue"));
    }

    // ===== Validation des conditions contre les signatures distantes =====

    @Test
    void equalsConditionOnBooleanAttribute_withBooleanValue_passes() {
        stubAttributeType("Boolean");
        when(conditionRegistry.getAllConditions()).thenReturn(conditionSignatures());

        ConditionDTO equals = new ConditionDTO("equals", Map.of("attr", "vivant", "value", "true"));
        ChoiceDTO choice = new ChoiceDTO();
        choice.setConditions(List.of(equals));
        EditorNodeResponseDTO node = node(1);
        node.setChoices(List.of(choice));

        ValidationResult result = validationService.validate(
            storyWith(attribute("vivant", "Boolean"), node));

        assertTrue(result.isValid(), () -> "Erreurs inattendues: " + result.getErrors());
    }

    @Test
    void equalsConditionOnIntAttribute_withNonNumericValue_fails() {
        stubAttributeType("Int");
        when(conditionRegistry.getAllConditions()).thenReturn(conditionSignatures());

        ConditionDTO equals = new ConditionDTO("equals", Map.of("attr", "hp", "value", "plein"));
        ChoiceDTO choice = new ChoiceDTO();
        choice.setConditions(List.of(equals));
        EditorNodeResponseDTO node = node(1);
        node.setChoices(List.of(choice));

        ValidationResult result = validationService.validate(
            storyWith(attribute("hp", "Int"), node));

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "doit être de type Int"));
    }

    // ===== Helpers signatures =====

    /** Sous-ensemble réel de actionsSignatures.json (repo distant). */
    private Map<String, Map<String, List<String>>> actionSignatures() {
        return Map.of(
            "Increase", Map.of("attr", List.of("attrInt", "attrFloat"), "value", List.of("Int", "Float")),
            "Set", Map.of(
                "attr", List.of("attrInt", "attrFloat", "attrString", "attrBoolean", "attrList"),
                "value", List.of("Int", "Float", "String", "Boolean", "List")));
    }

    /** Sous-ensemble réel de conditionsSignatures.json (repo distant). */
    private Map<String, Map<String, List<String>>> conditionSignatures() {
        return Map.of(
            "equals", Map.of(
                "attr", List.of("attrInt", "attrFloat", "attrString", "attrBoolean"),
                "value", List.of("Int", "Float", "String", "Boolean")));
    }

    private void stubAttributeType(String type) {
        when(dataTypeRegistry.typeExists(type)).thenReturn(true);
        when(dataTypeRegistry.getType(type)).thenReturn(Optional.of(new DynamicDataType(type, List.of())));
    }

    private AttributeDTO attribute(String label, String type) {
        AttributeDTO attr = new AttributeDTO();
        attr.setLabel(label);
        attr.setType(type);
        attr.setValue(""); // valeur vide => pas de validation de valeur initiale
        return attr;
    }

    private EditorNodeResponseDTO nodeWithActions(Integer id, ActionDTO... actions) {
        EditorNodeResponseDTO node = node(id);
        node.setActions(List.of(actions));
        return node;
    }

    private StorySubmissionDTO storyWith(AttributeDTO attribute, EditorNodeResponseDTO node) {
        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(metadata("Mon histoire"));
        dto.setNodes(List.of(node));
        dto.setAttributes(List.of(attribute));
        return dto;
    }
}
