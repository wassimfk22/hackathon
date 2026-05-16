package com.hackthon.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatClient chatClient;

    // Stockage temporaire en mémoire (pour le hackathon)
    private final List<ChatMessage> conversationHistory = new ArrayList<>();

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @PostMapping
    public String chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");

        // Ajouter le message utilisateur à l'historique
        conversationHistory.add(new ChatMessage("user", userMessage));

        String response = chatClient.prompt()
                .system(getSystemPrompt())
                .messages(convertToSpringAiMessages())
                .user(userMessage)
                .call()
                .content();

        // Ajouter la réponse de l'IA à l'historique
        conversationHistory.add(new ChatMessage("assistant", response));

        // Limiter l'historique à 15 messages pour éviter de consommer trop de tokens
        if (conversationHistory.size() > 30) {
            conversationHistory.remove(0);
            conversationHistory.remove(0);
        }

        return response;
    }

    private String getSystemPrompt() {
        return """
            Tu es un Tuteur IA spécialisé EXCLUSIVEMENT en Software Engineering et Développement IT.
            Tu es clair, concis, pédagogique et tu ne répètes JAMAIS les mêmes phrases d'introduction.

            **Règles strictes de flow :**
            1. Commence directement par proposer 5 sujets sans longue introduction.
            2. Une fois les sujets validés par l'utilisateur ("oui", "continuer", "ok"...), passe directement à la demande du niveau.
            3. Si l'utilisateur choisit "Laisser moi choisir", génère immédiatement un quiz de 5 questions.
            4. Quand l'utilisateur donne ses réponses et dit "Corriger mes réponses", corrige + donne un score + crée une roadmap.

            Ne répète jamais la liste des sujets plusieurs fois.
            Ne dis jamais "Il semble que nous recommençons" ou "je n'ai pas pu voir les messages précédents".
            Garde le contexte de la conversation en cours.
            Réponds en français, de façon structurée et motivante.
            """;
    }

    private List<Message> convertToSpringAiMessages() {
        return conversationHistory.stream()
                .map(msg -> msg.role.equals("user")
                        ? (Message) new UserMessage(msg.content)
                        : (Message) new AssistantMessage(msg.content))
                .toList();
    }

    // Classe interne pour l'historique
    private static class ChatMessage {
        String role;
        String content;
        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}