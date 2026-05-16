package com.hackthon.service;

import com.hackthon.service.GroqService.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stockage en mémoire des historiques de conversation.
 * Clé = userId. Chaque utilisateur a son propre historique isolé.
 */
@Component
public class ConversationStore {

    private static final int MAX_HISTORY = 30;

    // Thread-safe : plusieurs requêtes simultanées possibles
    private final Map<Long, List<ChatMessage>> store = new ConcurrentHashMap<>();

    public List<ChatMessage> getHistory(Long userId) {
        return store.computeIfAbsent(userId, k -> new ArrayList<>());
    }

    public void addUserMessage(Long userId, String content) {
        add(userId, "user", content);
    }

    public void addAssistantMessage(Long userId, String content) {
        add(userId, "assistant", content);
    }

    private void add(Long userId, String role, String content) {
        List<ChatMessage> history = getHistory(userId);
        history.add(new ChatMessage(role, content));

        // Trim : on garde toujours les 30 derniers messages
        if (history.size() > MAX_HISTORY) {
            // Supprimer par paires (user + assistant) pour garder la cohérence
            history.remove(0);
            if (!history.isEmpty()) history.remove(0);
        }
    }

    public void clear(Long userId) {
        store.remove(userId);
    }

    public int size(Long userId) {
        return store.getOrDefault(userId, List.of()).size();
    }
    
    
    
}