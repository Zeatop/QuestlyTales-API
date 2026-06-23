package org.tpi.questlytales.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tpi.questlytales.services.DataTypeRegistry;

/**
 * Configuration Spring pour le système de types dynamiques.
 * Le token GitHub peut être configuré dans application.properties
 */
@Configuration
public class DataTypeConfig {

    @Value("${github.token:}")
    private String githubToken;

    @Bean
    public DataTypeRegistry dataTypeRegistry() {
        if (githubToken == null || githubToken.isBlank()) {
            throw new IllegalStateException(
                    "github.token manquant : définis la variable d'environnement QUESTLYTALES_GITHUB_TOKEN "
                            + "(ou github.token dans application-local.properties pour le dev local).");
        }
        return new DataTypeRegistry(githubToken);
    }
}
