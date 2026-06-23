package org.tpi.questlytales.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tpi.questlytales.models.DynamicDataType;
import org.tpi.questlytales.services.DataTypeRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller REST pour gérer les types de données dynamiques.
 * Permet de consulter les types disponibles et leurs actions sans modifier le code.
 */
@RestController
@RequestMapping("/api/datatypes")
public class DataTypeController {

    private final DataTypeRegistry dataTypeRegistry;

    @Autowired
    public DataTypeController(DataTypeRegistry dataTypeRegistry) {
        this.dataTypeRegistry = dataTypeRegistry;
    }

    /**
     * GET /api/datatypes
     * Récupère tous les noms de types disponibles
     */
    @GetMapping
    public ResponseEntity<Set<String>> getAllTypeNames() {
        return ResponseEntity.ok(dataTypeRegistry.getAllTypeNames());
    }

    /**
     * GET /api/datatypes/details
     * Récupère tous les types avec leurs détails
     */
    @GetMapping("/details")
    public ResponseEntity<List<Map<String, Object>>> getAllTypesDetails() {
        List<Map<String, Object>> details = dataTypeRegistry.getAllTypes().stream()
                .map(type -> Map.of(
                        "typeName", (Object) type.getTypeName(),
                        "javaType", type.getJavaType().getSimpleName(),
                        "allowedActions", type.getAllowedActions()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(details);
    }

    /**
     * GET /api/datatypes/{typeName}
     * Récupère les informations d'un type spécifique
     */
    @GetMapping("/{typeName}")
    public ResponseEntity<Map<String, Object>> getTypeDetails(@PathVariable String typeName) {
        return dataTypeRegistry.getType(typeName)
                .map(type -> ResponseEntity.ok(Map.of(
                        "typeName", (Object) type.getTypeName(),
                        "javaType", type.getJavaType().getSimpleName(),
                        "allowedActions", type.getAllowedActions()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/datatypes/{typeName}/actions
     * Récupère les actions disponibles pour un type
     */
    @GetMapping("/{typeName}/actions")
    public ResponseEntity<List<String>> getActionsForType(@PathVariable String typeName) {
        if (!dataTypeRegistry.typeExists(typeName)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dataTypeRegistry.getActionsForType(typeName));
    }

    /**
     * GET /api/datatypes/{typeName}/validate/{action}
     * Valide si une action est supportée par un type
     */
    @GetMapping("/{typeName}/validate/{action}")
    public ResponseEntity<Map<String, Boolean>> validateAction(
            @PathVariable String typeName,
            @PathVariable String action) {
        if (!dataTypeRegistry.typeExists(typeName)) {
            return ResponseEntity.notFound().build();
        }
        boolean isValid = dataTypeRegistry.isValidAction(typeName, action);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }

    /**
     * POST /api/datatypes/refresh
     * Rafraîchit le registry en rechargeant les types depuis GitHub
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshTypes() {
        try {
            dataTypeRegistry.refresh();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Types rechargés avec succès",
                    "typeCount", dataTypeRegistry.getTypeCount()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erreur lors du rafraîchissement: " + e.getMessage()
            ));
        }
    }

    /**
     * GET /api/datatypes/count
     * Récupère le nombre de types disponibles
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Integer>> getTypeCount() {
        return ResponseEntity.ok(Map.of("count", dataTypeRegistry.getTypeCount()));
    }
}
