package org.tpi.questlytales.dtos.storydtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.tpi.questlytales.dtos.AttributeDTO;
import org.tpi.questlytales.dtos.storynodedtos.GameNodeResponseDTO;

import java.util.List;
import java.util.Map;

/**
 * Story téléchargeable pour le jeu.
 * Contient le graphe complet (tous les nœuds) afin que l'application mobile
 * puisse jouer en local en naviguant via les choix, sans rappeler le serveur.
 * Les métadonnées d'édition (x, y, color) sont exclues des nœuds.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameStoryResponseDTO {
    private String id;
    private StoryMetadataDTO metadata;
    private Integer startNodeId;
    private List<GameNodeResponseDTO> nodes;
    private List<AttributeDTO> playerAttributes;
    // Map { nomMedia: url } : les noeuds referencent un media par son nom (action changeLocation).
    private Map<String, String> images;
    private Map<String, String> videos;
    private Map<String, String> sounds;
}
