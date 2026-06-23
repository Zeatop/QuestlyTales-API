package org.tpi.questlytales.dtos.storydtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryMetadataDTO {
    private String title;
    private String author;
    private String version;
    private String language;
    private Long creationDate;
    private Long lastUpdateDate;
    private String description;
    private List<String> tags;
    private String thumbnailImage;
    private String genre;
    private Integer size;
    private Integer numberOfNodes;
}
