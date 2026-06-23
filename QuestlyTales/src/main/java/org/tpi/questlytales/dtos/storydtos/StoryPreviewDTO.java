package org.tpi.questlytales.dtos.storydtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Représentation allégée d'une story pour le catalogue.
 * Ne contient que l'identifiant et les métadonnées (pas les nœuds),
 * afin de référencer/parcourir les stories sans télécharger le graphe complet.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryPreviewDTO {
    private String id;
    private String ownerId;
    private StoryMetadataDTO metadata;
}
