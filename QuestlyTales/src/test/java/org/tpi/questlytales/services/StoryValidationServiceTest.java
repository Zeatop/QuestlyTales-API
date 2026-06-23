package org.tpi.questlytales.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tpi.questlytales.dtos.AttributeDTO;
import org.tpi.questlytales.dtos.ChoiceDTO;
import org.tpi.questlytales.dtos.storydtos.StoryMetadataDTO;
import org.tpi.questlytales.dtos.storydtos.StorySubmissionDTO;
import org.tpi.questlytales.dtos.storynodedtos.EditorNodeResponseDTO;
import org.tpi.questlytales.services.StoryValidationService.ValidationResult;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryValidationServiceTest {

    @Mock
    private DataTypeRegistry dataTypeRegistry;

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

    @Test
    void negativeCoordinates_fail() {
        EditorNodeResponseDTO node = node(1);
        node.setX(-5);
        node.setY(-3);

        StorySubmissionDTO dto = new StorySubmissionDTO();
        dto.setMetadata(metadata("Mon histoire"));
        dto.setNodes(List.of(node));

        ValidationResult result = validationService.validate(dto);

        assertFalse(result.isValid());
        assertTrue(errorsContain(result, "négative"));
    }
}
