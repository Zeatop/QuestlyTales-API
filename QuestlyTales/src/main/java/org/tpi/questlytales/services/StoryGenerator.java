package org.tpi.questlytales.services;

import org.tpi.questlytales.dtos.GenerateStoryRequestDTO;

import java.util.Map;

/**
 * Contrat commun aux générateurs d'histoires (DeepSeek, Claude, ...).
 * Permet de basculer d'un moteur à l'autre via configuration sans toucher au contrôleur.
 */
public interface StoryGenerator {
    Map<String, Object> generateStory(GenerateStoryRequestDTO request) throws Exception;
}
