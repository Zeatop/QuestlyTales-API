package org.tpi.questlytales.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tpi.questlytales.services.ConditionRegistry;

/**
 * Controller REST pour consulter les signatures de conditions chargées depuis
 * conditionsSignatures.json (repo distant Zeatop/distantCheck) via ConditionRegistry.
 * Fait la même chose que ActionController, mais pour les conditions de choix.
 * Permet au front de ne proposer que des conditions réellement reconnues par le moteur
 * (distant check) au lieu d'inventer un vocabulaire.
 */
@RestController
@RequestMapping("/api/conditions")
public class ConditionController {

    private final ConditionRegistry conditionRegistry;

    @Autowired
    public ConditionController(ConditionRegistry conditionRegistry) {
        this.conditionRegistry = conditionRegistry;
    }

    /**
     * GET /api/conditions
     * Toutes les conditions avec leur signature ({ attr: [...], value: [...] }).
     */
    @GetMapping
    public ResponseEntity<Map<String, Map<String, List<String>>>> getAllConditions() {
        return ResponseEntity.ok(conditionRegistry.getAllConditions());
    }

    /**
     * POST /api/conditions/refresh
     * Recharge les conditions depuis GitHub.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshConditions() {
        try {
            conditionRegistry.refresh();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Conditions rechargées avec succès",
                    "conditionCount", conditionRegistry.getAllConditions().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erreur lors du rafraîchissement: " + e.getMessage()
            ));
        }
    }
}
