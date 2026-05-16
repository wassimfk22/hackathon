package com.hackthon.dto;

import java.time.LocalDateTime;
import java.util.List;

public class QuizDTO {

    // ── Réponse : quiz généré (questions seulement, sans les bonnes réponses) ──
    public record QuizGeneréDTO(
            Long quizId,
            Long coursId,
            String coursTitre,
            String phaseTitre,
            int nombreQuestions,
            List<QuestionDTO> questions
    ) {}

    public record QuestionDTO(
            int numero,
            String question,
            List<String> options   // ["A) ...", "B) ...", "C) ...", "D) ..."]
    ) {}

    // ── Requête : l'étudiant soumet ses réponses ──────────────────────────
    public record SoumettreQuizRequest(
            Long etudiantId,
            List<ReponseDTO> reponses   // une par question
    ) {}

    public record ReponseDTO(
            int numeroQuestion,
            String reponseChoisie   // "A", "B", "C" ou "D"
    ) {}

    // ── Réponse : résultat de l'évaluation ───────────────────────────────
    public record ResultatQuizDTO(
            Long quizId,
            Long coursId,
            String coursTitre,
            int scoreObtenu,
            int scoreMax,
            double pourcentage,
            boolean quizReussi,         // >= 50%
            String feedbackGlobal,
            List<CorrectionDTO> corrections,

            // Impact sur la phase
            PhaseProgressDTO phaseProgress
    ) {}

    public record CorrectionDTO(
            int numeroQuestion,
            String question,
            String reponseEtudiant,
            String bonneReponse,
            boolean estCorrecte,
            String explication
    ) {}

    public record PhaseProgressDTO(
            Long phaseId,
            String phaseTitre,
            double pointsObtenus,
            double pointsMax,
            double pourcentagePhase,
            boolean phaseDebloquee,       // > 50% des points de la phase
            boolean peutPasserSuivante,
            String messageDeblocage
    ) {}
}