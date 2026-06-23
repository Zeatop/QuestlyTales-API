package org.tpi.questlytales.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tpi.questlytales.models.DynamicDataType;
import org.tpi.questlytales.utils.GithubFileFetcher;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry qui charge et gère dynamiquement les types de données depuis le JSON distant.
 * Les types sont chargés au démarrage et peuvent être rafraîchis à la demande.
 */
@Service
public class DataTypeRegistry {

    private final Map<String, DynamicDataType> typeRegistry = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final GithubFileFetcher fetcher;

    // Configuration (pourrait être dans application.properties)
    private static final String GITHUB_OWNER = "Zeatop";
    private static final String GITHUB_REPO = "distantCheck";
    private static final String FILE_PATH = "actionsFromTypes.json";

    public DataTypeRegistry(@Value("${github.token}") String githubToken) {
        this.objectMapper = new ObjectMapper();
        this.fetcher = new GithubFileFetcher(githubToken);
        // Chargement initial des types
        loadTypesFromGithub();
    }

    /**
     * Charge tous les types depuis le fichier JSON distant
     */
    public void loadTypesFromGithub() {
        try {
            String jsonContent = fetcher.getFileContent(GITHUB_OWNER, GITHUB_REPO, FILE_PATH);

            Map<String, List<String>> typesMap = objectMapper.readValue(
                jsonContent,
                new TypeReference<Map<String, List<String>>>() {}
            );

            // Vider le registry et le recharger
            typeRegistry.clear();

            typesMap.forEach((typeName, actions) -> {
                DynamicDataType dataType = new DynamicDataType(typeName, actions);
                typeRegistry.put(typeName, dataType);
            });

            System.out.println("✓ " + typeRegistry.size() + " types chargés depuis GitHub");

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du chargement des types: " + e.getMessage(), e);
        }
    }

    /**
     * Récupère un type par son nom
     */
    public Optional<DynamicDataType> getType(String typeName) {
        return Optional.ofNullable(typeRegistry.get(typeName));
    }

    /**
     * Récupère tous les types disponibles
     */
    public Collection<DynamicDataType> getAllTypes() {
        return Collections.unmodifiableCollection(typeRegistry.values());
    }

    /**
     * Récupère tous les noms de types disponibles
     */
    public Set<String> getAllTypeNames() {
        return Collections.unmodifiableSet(typeRegistry.keySet());
    }

    /**
     * Vérifie si un type existe
     */
    public boolean typeExists(String typeName) {
        return typeRegistry.containsKey(typeName);
    }

    /**
     * Récupère les actions disponibles pour un type
     */
    public List<String> getActionsForType(String typeName) {
        return getType(typeName)
                .map(DynamicDataType::getAllowedActions)
                .orElse(Collections.emptyList());
    }

    /**
     * Vérifie si une action est valide pour un type donné
     */
    public boolean isValidAction(String typeName, String action) {
        return getType(typeName)
                .map(type -> type.isActionAllowed(action))
                .orElse(false);
    }

    /**
     * Rafraîchit le registry en rechargeant depuis GitHub
     */
    public void refresh() {
        loadTypesFromGithub();
    }

    /**
     * Retourne le nombre de types enregistrés
     */
    public int getTypeCount() {
        return typeRegistry.size();
    }
}
