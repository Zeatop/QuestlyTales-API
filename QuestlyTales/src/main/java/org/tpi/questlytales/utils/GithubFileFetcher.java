package org.tpi.questlytales.utils;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

public class GithubFileFetcher {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String token;

    public GithubFileFetcher(String token) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.token = token;
    }

    public String getFileContent(String owner, String repo, String filePath, String branch) {
        try {
            String apiUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s",
                    owner, repo, filePath);

            if (branch != null && !branch.isEmpty()) {
                apiUrl += "?ref=" + branch;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Accept", "application/vnd.github+json");
            headers.set("X-GitHub-Api-Version", "2022-11-28");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            String contentBase64 = jsonResponse.get("content").asText();
            byte[] decodedBytes = Base64.getDecoder().decode(contentBase64.replace("\n", ""));
            return new String(decodedBytes);

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la récupération du fichier: " + e.getMessage(), e);
        }
    }

    public String getFileContent(String owner, String repo, String filePath) {
        return getFileContent(owner, repo, filePath, "origin");
    }

    public String getFileMetadata(String owner, String repo, String filePath) {
        try {
            String apiUrl = String.format("https://api.github.com/repos/%s/%s/contents/%s",
                    owner, repo, filePath);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Accept", "application/vnd.github+json");
            headers.set("X-GitHub-Api-Version", "2022-11-28");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            return response.getBody();

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la récupération des métadonnées: " + e.getMessage(), e);
        }
    }
}
