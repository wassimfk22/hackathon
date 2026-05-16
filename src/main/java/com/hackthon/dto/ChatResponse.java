package com.hackthon.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hackthon.enums.ChatResponseType;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(

        // Type de réponse : TEXT, QUIZ, ROADMAP, SCORE, NIVEAU
        ChatResponseType type,

        // Toujours présent : message texte lisible par le frontend
        String message,

        // Présent si type = QUIZ
        List<QuizQuestion> questions,

        // Présent si type = SCORE
        Integer score,

        // Présent si type = NIVEAU ou ROADMAP
        String niveau,

        // Présent si type = ROADMAP
        List<PhaseDTO> roadmap

) {
    // Factory methods pour construire proprement chaque type

    public static ChatResponse text(String message) {
        return new ChatResponse(ChatResponseType.TEXT, message, null, null, null, null);
    }

    public static ChatResponse quiz(String message, List<QuizQuestion> questions) {
        return new ChatResponse(ChatResponseType.QUIZ, message, questions, null, null, null);
    }

    public static ChatResponse score(String message, int score, String niveau) {
        return new ChatResponse(ChatResponseType.SCORE, message, null, score, niveau, null);
    }

    public static ChatResponse roadmap(String message, String niveau, List<PhaseDTO> phases) {
        return new ChatResponse(ChatResponseType.ROADMAP, message, null, null, niveau, phases);
    }

    // ─── Records imbriqués ────────────────────────────────────────────────

    public record QuizQuestion(
            int numero,
            String question,
            List<String> options,
            // null côté client, rempli après correction
            String bonneReponse,
            String explication
    ) {}

    public record PhaseDTO(
            int ordre,
            String titre,
            List<String> cours
    ) {}
}