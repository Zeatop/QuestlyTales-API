package org.tpi.questlytales.dtos.storydtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.tpi.questlytales.dtos.AttributeDTO;
import org.tpi.questlytales.dtos.storynodedtos.EditorNodeResponseDTO;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditorStoryResponseDTO {
    private String id;  // MongoDB ObjectId
    private StoryMetadataDTO metadata;
    private List<EditorNodeResponseDTO> nodes;
    private List<AttributeDTO> attributes;
    private List<String> images;
    private List<String> videos;
    private List<String> sounds;
}
