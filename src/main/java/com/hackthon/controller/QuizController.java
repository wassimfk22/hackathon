package com.hackthon.controller;

import com.hackthon.dto.QuizDTO.*;
import com.hackthon.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cours")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QuizController {

    private final QuizService quizService;

    /**
     * POST /api/cours/{coursId}/terminer
     * L'étudiant termine le cours → l'IA génère le quiz lié au contenu
     *
     * Param : ?etudiantId=1
     * Retourne : les questions du quiz (sans les bonnes réponses)
     */
    @PostMapping("/{coursId}/terminer")
    public ResponseEntity<QuizGeneréDTO> terminerCours(
            @PathVariable Long coursId,
            @RequestParam Long etudiantId) {
        return ResponseEntity.ok(quizService.terminerCoursEtGenererQuiz(coursId, etudiantId));
    }

    /**
     * POST /api/cours/quiz/{quizId}/soumettre
     * L'étudiant soumet ses réponses → IA évalue → score + impact phase
     *
     * Body :
     * {
     *   "etudiantId": 1,
     *   "reponses": [
     *     { "numeroQuestion": 1, "reponseChoisie": "A" },
     *     { "numeroQuestion": 2, "reponseChoisie": "C" },
     *     { "numeroQuestion": 3, "reponseChoisie": "B" },
     *     { "numeroQuestion": 4, "reponseChoisie": "D" },
     *     { "numeroQuestion": 5, "reponseChoisie": "A" }
     *   ]
     * }
     */
    @PostMapping("/quiz/{quizId}/soumettre")
    public ResponseEntity<ResultatQuizDTO> soumettreQuiz(
            @PathVariable Long quizId,
            @Valid @RequestBody SoumettreQuizRequest request) {
        return ResponseEntity.ok(quizService.soumettreReponses(quizId, request));
    }
}