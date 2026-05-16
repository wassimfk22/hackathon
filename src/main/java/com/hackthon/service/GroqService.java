package com.hackthon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.model:llama3-8b-8192}")
    private String model;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Appel simple : un seul message utilisateur
     */
    public String ask(String systemPrompt, String userMessage) {
        return askWithHistory(systemPrompt, List.of(
                new ChatMessage("user", userMessage)
        ));
    }

    /**
     * Appel avec historique de conversation (pour le chatbot)
     */
    public String askWithHistory(String systemPrompt, List<ChatMessage> messages) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.7);
            body.put("max_tokens", 2048);
            body.put("stream", false);

            ArrayNode messagesArray = objectMapper.createArrayNode();

            // System prompt
            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messagesArray.add(systemMsg);

            // Historique + message actuel
            for (ChatMessage msg : messages) {
                ObjectNode m = objectMapper.createObjectNode();
                m.put("role", msg.role());
                m.put("content", msg.content());
                messagesArray.add(m);
            }

            body.set("messages", messagesArray);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String errorBody = response.body();
                log.error("Groq API error {}: {}", response.statusCode(), errorBody);
                throw new RuntimeException("Groq API error: " + response.statusCode() + " - " + errorBody);
            }

            JsonNode json = objectMapper.readTree(response.body());
            return json.at("/choices/0/message/content").asText();

        } catch (Exception e) {
            log.error("Erreur appel Groq", e);
            throw new RuntimeException("Erreur lors de l'appel à l'IA: " + e.getMessage(), e);
        }
    }

    public record ChatMessage(String role, String content) {}
    
    
    
}