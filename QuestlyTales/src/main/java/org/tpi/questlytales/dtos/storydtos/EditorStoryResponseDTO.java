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
public class EditorStoryResponseDTO {
    private String id;  // MongoDB ObjectId
    private StoryMetadataDTO metadata;
    private List<EditorNodeResponseDTO> nodes;
    private List<AttributeDTO> attributes;
    // Map { nomMedia: url } : les noeuds referencent un media par son nom (action changeLocation).
    private Map<String, String> images;
    private Map<String, String> videos;
    private Map<String, String> sounds;
}
