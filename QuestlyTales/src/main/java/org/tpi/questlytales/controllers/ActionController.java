package org.tpi.questlytales.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tpi.questlytales.services.ActionRegistry;

import java.util.List;
import java.util.Map;

/**
 * Controller REST pour consulter les signatures d'actions chargées depuis
 * actionsSignatures.json (repo distant Zeatop/distantCheck) via ActionRegistry.
 * Fait la même chose que DataTypeController, mais pour les actions.
 */
@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private final ActionRegistry actionRegistry;

    @Autowired
    public ActionController(ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
    }

    /**
     * GET /api/actions
     * Toutes les actions avec leurs paramètres (ambiance incluse).
     */
    @GetMapping
    public ResponseEntity<Map<String, Map<String, List<String>>>> getAllActions() {
        return ResponseEntity.ok(actionRegistry.getAllActions());
    }

    /**
     * GET /api/actions/narrative
     * Uniquement les actions narratives/gameplay (hors ambiance) :
     * celles que la génération IA est censée utiliser.
     */
    @GetMapping("/narrative")
    public ResponseEntity<Map<String, Map<String, List<String>>>> getNarrativeActions() {
        return ResponseEntity.ok(actionRegistry.getNarrativeActions());
    }

    /**
     * POST /api/actions/refresh
     * Recharge les actions depuis GitHub.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshActions() {
        try {
            actionRegistry.refresh();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Actions rechargées avec succès",
                    "actionCount", actionRegistry.getAllActions().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erreur lors du rafraîchissement: " + e.getMessage()
            ));
        }
    }
}
