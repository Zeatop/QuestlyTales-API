package org.tpi.questlytales.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tpi.questlytales.utils.GithubFileFetcher;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry qui charge les signatures de conditions depuis le JSON distant
 * (repo Zeatop/distantCheck, fichier conditionsSignatures.json).
 * Pendant exact de {@link ActionRegistry}, mais pour les conditions de choix.
 */
@Service
public class ConditionRegistry {

    private static final String GITHUB_OWNER = "Zeatop";
    private static final String GITHUB_REPO = "distantCheck";
    private static final String FILE_PATH = "conditionsSignatures.json";

    private final Map<String, Map<String, List<String>>> conditionsMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final GithubFileFetcher fetcher;

    public ConditionRegistry(@Value("${github.token}") String githubToken) {
        this.objectMapper = new ObjectMapper();
        this.fetcher = new GithubFileFetcher(githubToken);
        loadFromGithub();
    }

    public void loadFromGithub() {
        try {
            String json = fetcher.getFileContent(GITHUB_OWNER, GITHUB_REPO, FILE_PATH);
            Map<String, Map<String, List<String>>> loaded = objectMapper.readValue(
                json, new TypeReference<Map<String, Map<String, List<String>>>>() {}
            );
            conditionsMap.clear();
            conditionsMap.putAll(loaded);
            System.out.println("✓ " + conditionsMap.size() + " conditions chargées depuis GitHub");
        } catch (Exception e) {
            throw new RuntimeException("Erreur chargement des conditions: " + e.getMessage(), e);
        }
    }

    public Map<String, Map<String, List<String>>> getAllConditions() {
        return Collections.unmodifiableMap(conditionsMap);
    }

    public void refresh() {
        loadFromGithub();
    }
}
