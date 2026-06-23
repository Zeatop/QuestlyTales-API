package org.tpi.questlytales.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tpi.questlytales.dtos.storydtos.StorySubmissionDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateStoryRequestDTO {
    // Instructions complémentaires optionnelles pour orienter la génération
    private String description;
    // Obligatoire : doit contenir metadata.description, attributes (dont characters) et minNodeCount
    private StorySubmissionDTO existingStory;
    // Obligatoire : nombre minimum de nœuds à générer
    private Integer minNodeCount;
}
