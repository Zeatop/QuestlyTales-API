package org.tpi.questlytales.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.tpi.questlytales.dtos.GenerateStoryRequestDTO;
import org.tpi.questlytales.dtos.storydtos.EditorStoryResponseDTO;
import org.tpi.questlytales.dtos.storydtos.GameStoryResponseDTO;
import org.tpi.questlytales.dtos.storydtos.StorySubmissionDTO;
import org.tpi.questlytales.security.AuthenticatedUser;
import org.tpi.questlytales.services.ClaudeStoryService;
import org.tpi.questlytales.services.DeepSeekService;
import org.tpi.questlytales.services.StoryGenerator;
import org.tpi.questlytales.services.StoryMongoService;
import org.tpi.questlytales.services.StoryMongoService.WriteOutcome;
import org.tpi.questlytales.services.StoryValidationService;
import org.tpi.questlytales.services.StoryValidationService.ValidationResult;

import java.util.Map;

/**
 * Controller REST pour la gestion des stories.
 * Gère la création, modification, récupération et suppression des stories.
 */
@RestController
@RequestMapping("/api/stories")
public class StoryController {

    @Autowired
    private StoryValidationService validationService;

    @Autowired
    private StoryMongoService mongoService;

    @Autowired
    private DeepSeekService deepSeekService;

    @Autowired
    private ClaudeStoryService claudeStoryService;

    /** Moteur de génération actif : "deepseek" (défaut) ou "claude". */
    @Value("${story.generator:deepseek}")
    private String storyGenerator;

    private StoryGenerator generator() {
        return "claude".equalsIgnoreCase(storyGenerator) ? claudeStoryService : deepSeekService;
    }

    /**
     * Créer une nouvelle story
     * POST /api/stories
     */
    @PostMapping
    public ResponseEntity<?> createStory(@RequestBody StorySubmissionDTO dto,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        // Validation
        ValidationResult validation = validationService.validate(dto);
        if (!validation.isValid()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "errors", validation.getErrors()
            ));
        }

        // Sauvegarde (l'auteur authentifié devient le propriétaire)
        String storyId = mongoService.saveStory(dto, user.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "storyId", storyId,
            "message", "Story créée avec succès"
        ));
    }

    /**
     * Lister les stories pour le catalogue (preview paginée, métadonnées seules)
     * GET /api/stories?page=0&size=12&genre=&tag=&author=&search=
     */
    @GetMapping
    public ResponseEntity<?> listStories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String search) {

        if (page < 0) page = 0;
        if (size < 1) size = 12;
        if (size > 100) size = 100;

        return ResponseEntity.ok(
            mongoService.listStories(page, size, null, genre, tag, author, search));
    }

    /**
     * Lister les stories de l'utilisateur authentifié (page « mes histoires »)
     * GET /api/stories/mine?page=0&size=12&genre=&tag=&search=
     */
    @GetMapping("/mine")
    public ResponseEntity<?> listMyStories(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search) {

        if (page < 0) page = 0;
        if (size < 1) size = 12;
        if (size > 100) size = 100;

        return ResponseEntity.ok(
            mongoService.listStories(page, size, user.id(), genre, tag, null, search));
    }

    /**
     * Récupérer une story pour l'éditeur (données complètes)
     * GET /api/stories/{id}/edit
     */
    @GetMapping("/{id}/edit")
    public ResponseEntity<?> getStoryForEditor(@PathVariable String id) {
        EditorStoryResponseDTO story = mongoService.getStoryForEditor(id);
        if (story == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(story);
    }

    /**
     * Récupérer une story pour le jeu (nœud initial)
     * GET /api/stories/{id}/play
     */
    @GetMapping("/{id}/play")
    public ResponseEntity<?> getStoryForGame(@PathVariable String id) {
        GameStoryResponseDTO gameState = mongoService.getStoryForGame(id);
        if (gameState == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gameState);
    }

    /**
     * Mettre à jour une story existante
     * PUT /api/stories/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateStory(@PathVariable String id,
                                         @RequestBody StorySubmissionDTO dto,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        // Validation
        ValidationResult validation = validationService.validate(dto);
        if (!validation.isValid()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "errors", validation.getErrors()
            ));
        }

        // Mise à jour (soumise au contrôle de propriété)
        WriteOutcome outcome = mongoService.updateStory(id, dto, user.id());
        switch (outcome) {
            case NOT_FOUND:
                return ResponseEntity.notFound().build();
            case FORBIDDEN:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Vous n'êtes pas le propriétaire de cette story"
                ));
            default:
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Story mise à jour avec succès"
                ));
        }
    }

    /**
     * Générer une story via DeepSeek
     * POST /api/stories/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateStory(@RequestBody GenerateStoryRequestDTO request) {
        // Validation des champs obligatoires
        if (request.getMinNodeCount() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "message", "minNodeCount est obligatoire."
            ));
        }
        if (request.getExistingStory() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "message", "existingStory est obligatoire. Fournissez au minimum metadata.description et des attributs."
            ));
        }
        var meta = request.getExistingStory().getMetadata();
        if (meta == null || meta.getDescription() == null || meta.getDescription().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "message", "existingStory.metadata.description est obligatoire."
            ));
        }
        var attrs = request.getExistingStory().getAttributes();
        if (attrs == null || attrs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, "message", "existingStory.attributes est obligatoire (incluez au minimum l'attribut 'characters')."
            ));
        }

        try {
            Map<String, Object> generated = generator().generateStory(request);
            return ResponseEntity.ok(generated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Erreur lors de la génération : " + e.getMessage()
            ));
        }
    }

    /**
     * Supprimer une story
     * DELETE /api/stories/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStory(@PathVariable String id,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        WriteOutcome outcome = mongoService.deleteStory(id, user.id());
        switch (outcome) {
            case NOT_FOUND:
                return ResponseEntity.notFound().build();
            case FORBIDDEN:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Vous n'êtes pas le propriétaire de cette story"
                ));
            default:
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Story supprimée avec succès"
                ));
        }
    }
}