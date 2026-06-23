package org.tpi.questlytales.security;

/**
 * Principal authentifié exposé dans le SecurityContext.
 * Porte l'identifiant Mongo de l'utilisateur (issu du claim JWT) en plus de l'email,
 * afin que les controllers puissent récupérer l'owner sans requête supplémentaire.
 */
public record AuthenticatedUser(String id, String email) {
}
