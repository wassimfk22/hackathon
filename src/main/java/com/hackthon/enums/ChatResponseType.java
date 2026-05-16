package com.hackthon.enums;

public enum ChatResponseType {
    TEXT,       // réponse texte simple
    QUIZ,       // quiz de 5 questions généré
    SCORE,      // correction + score + niveau détecté
    NIVEAU,     // niveau sélectionné manuellement
    ROADMAP     // roadmap générée
}