package com.hackthon.controller;

import com.hackthon.service.GroqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final GroqService groqService;

    // Stockage temporaire en mémoire (pour le hackathon)
    private final List<GroqService.ChatMessage> conversationHistory = new ArrayList<>();

    @PostMapping
    public String chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");

        if (userMessage == null || userMessage.isBlank()) {
            return "Veuillez envoyer un message.";
        }

        // Ajouter le message utilisateur à l'historique
        conversationHistory.add(new GroqService.ChatMessage("user", userMessage));

        // Appel avec l'historique complet
        String response = groqService.askWithHistory(getSystemPrompt(), conversationHistory);

        // Ajouter la réponse de l'IA à l'historique
        conversationHistory.add(new GroqService.ChatMessage("assistant", response));

        // Limiter l'historique pour éviter de consommer trop de tokens
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
}