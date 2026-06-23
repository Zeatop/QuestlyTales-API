package org.tpi.questlytales.dtos.storydtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.tpi.questlytales.dtos.AttributeDTO;
import org.tpi.questlytales.dtos.storynodedtos.EditorNodeResponseDTO;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StorySubmissionDTO {
    private StoryMetadataDTO metadata;
    private List<EditorNodeResponseDTO> nodes;
    private List<AttributeDTO> attributes;
    // Map { nomImage: url } : les noeuds referencent une image par son nom (action changeLocation).
    private Map<String, String> images;
    private List<String> videos;
    private List<String> sounds;
}
