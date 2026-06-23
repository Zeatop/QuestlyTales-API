package org.tpi.questlytales.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tpi.questlytales.utils.GithubFileFetcher;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ActionRegistry {

    private static final String GITHUB_OWNER = "Zeatop";
    private static final String GITHUB_REPO = "distantCheck";
    private static final String FILE_PATH = "actionsSignatures.json";

    // Actions liées à l'ambiance : laissées vides à la génération, remplies par l'utilisateur
    private static final Set<String> AMBIANCE_ACTIONS = Set.of("ChangeLocation", "PlaySound", "StopSound");

    private final Map<String, Map<String, List<String>>> actionsMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final GithubFileFetcher fetcher;

    public ActionRegistry(@Value("${github.token}") String githubToken) {
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
            actionsMap.clear();
            actionsMap.putAll(loaded);
            System.out.println("✓ " + actionsMap.size() + " actions chargées depuis GitHub");
        } catch (Exception e) {
            throw new RuntimeException("Erreur chargement des actions: " + e.getMessage(), e);
        }
    }

    /**
     * Retourne uniquement les actions narratives/gameplay (hors ambiance).
     * Ce sont les seules que la génération IA doit utiliser.
     */
    public Map<String, Map<String, List<String>>> getNarrativeActions() {
        Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();
        actionsMap.forEach((name, params) -> {
            if (!AMBIANCE_ACTIONS.contains(name)) {
                result.put(name, params);
            }
        });
        return result;
    }

    public Map<String, Map<String, List<String>>> getAllActions() {
        return Collections.unmodifiableMap(actionsMap);
    }

    public void refresh() {
        loadFromGithub();
    }
}
