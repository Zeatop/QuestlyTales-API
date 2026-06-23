package org.tpi.questlytales.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration OpenAPI / Swagger.
 * Définit le titre de l'API et un schéma de sécurité « bearer JWT » afin que
 * le bouton « Authorize » de Swagger UI permette de tester les routes protégées.
 *
 * UI :   /swagger-ui.html
 * Spec : /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI questlyTalesOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("QuestlyTales API")
                .version("1.0")
                .description("API REST de QuestlyTales : authentification, gestion des histoires "
                    + "interactives (CRUD), catalogue paginé, téléchargement pour le jeu, "
                    + "types dynamiques et génération assistée."))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                new SecurityScheme()
                    .name(BEARER_SCHEME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
