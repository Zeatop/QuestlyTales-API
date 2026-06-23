package org.tpi.questlytales.models;

import java.util.List;
import java.util.Objects;

/**
 * Représente un type de données chargé dynamiquement depuis le JSON.
 * Permet d'ajouter de nouveaux types dans le JSON sans modifier le code.
 */
public class DynamicDataType {

    private final String typeName;
    private final List<String> allowedActions;
    private final Class<?> javaType;

    public DynamicDataType(String typeName, List<String> allowedActions) {
        this.typeName = typeName;
        this.allowedActions = allowedActions;
        this.javaType = inferJavaType(typeName);
    }

    /**
     * Infère le type Java correspondant au nom du type
     * Cette méthode peut être étendue pour supporter de nouveaux types
     */
    private Class<?> inferJavaType(String typeName) {
        return switch (typeName.toLowerCase()) {
            case "int", "integer" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "string" -> String.class;
            case "boolean", "bool" -> Boolean.class;
            case "list", "array" -> List.class;
            default -> Object.class; // Type par défaut pour les types inconnus
        };
    }

    public String getTypeName() {
        return typeName;
    }

    public List<String> getAllowedActions() {
        return allowedActions;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    /**
     * Vérifie si une action est valide pour ce type
     */
    public boolean isActionAllowed(String action) {
        return allowedActions.contains(action);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamicDataType that = (DynamicDataType) o;
        return Objects.equals(typeName, that.typeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeName);
    }

    @Override
    public String toString() {
        return "DynamicDataType{" +
                "typeName='" + typeName + '\'' +
                ", actions=" + allowedActions +
                ", javaType=" + javaType.getSimpleName() +
                '}';
    }
}
