package com.hackthon.controller;

import com.hackthon.dto.ChatRequest;
import com.hackthon.dto.ChatResponse;
import com.hackthon.service.AIOrchestratorService;
import com.hackthon.service.ConversationStore;
import com.hackthon.service.GroqService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ChatController {

    private final GroqService groqService;
    private final AIOrchestratorService orchestrator;
    private final ConversationStore conversationStore;

    /**
     * POST /api/chat
     * Corps : { "userId": 1, "message": "Je veux apprendre Java" }
     * Retour : ChatResponse avec type TEXT | QUIZ | SCORE | ROADMAP
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        Long userId = request.userId();
        String userMessage = request.message();

        log.info("Chat userId={} | message={}", userId, userMessage);

        // 1. Récupérer l'historique isolé de cet utilisateur
        List<GroqService.ChatMessage> history = conversationStore.getHistory(userId);

        // 2. Ajouter le message utilisateur à son historique
        conversationStore.addUserMessage(userId, userMessage);

        // 3. Appel Groq avec le system prompt de l'orchestrateur + historique
        String rawResponse = groqService.askWithHistory(
                orchestrator.buildSystemPrompt(),
                conversationStore.getHistory(userId)
        );

        // 4. Ajouter la réponse IA à l'historique
        conversationStore.addAssistantMessage(userId, rawResponse);

        // 5. Parser la réponse brute → ChatResponse structuré
        com.hackthon.dto.ChatResponse response = orchestrator.parseGroqResponse(rawResponse);

        log.info("Chat userId={} | responseType={}", userId, response.type());
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/chat/{userId}/reset
     * Remet à zéro la conversation (nouvelle session)
     */
    @DeleteMapping("/{userId}/reset")
    public ResponseEntity<Map<String, String>> reset(@PathVariable Long userId) {
        conversationStore.clear(userId);
        log.info("Conversation réinitialisée pour userId={}", userId);
        return ResponseEntity.ok(Map.of("message", "Conversation réinitialisée"));
    }

    /**
     * GET /api/chat/{userId}/history
     * Retourne l'historique complet de la conversation (debug)
     */
    @GetMapping("/{userId}/history")
    public ResponseEntity<List<GroqService.ChatMessage>> history(@PathVariable Long userId) {
        return ResponseEntity.ok(conversationStore.getHistory(userId));
    }
    
    
    
}