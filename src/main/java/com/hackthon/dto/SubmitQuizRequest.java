package com.hackthon.dto;

import java.util.List;

public record SubmitQuizRequest(
    Long etudiantId,
    Long quizId,
    List<ReponseDTO> reponses
) {
    public record ReponseDTO(String questionTexte, String reponseChoisie) {}
}
