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
        return new DataTypeRegistry(githubToken);
    }
}
