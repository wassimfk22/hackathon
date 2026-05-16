package com.hackthon.dto;

import java.time.LocalDateTime;

public record ExplicationResponse(
        String explication,         // réponse de l'IA
        String extraitOriginal,     // l'extrait que l'étudiant a envoyé
        int tourDeConversation,     // numéro du tour (1, 2, 3...)
        LocalDateTime timestamp
) {}