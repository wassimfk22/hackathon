package com.hackthon.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackthon.dto.SubmitQuizRequest;
import com.hackthon.entity.*;
import com.hackthon.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {

    private final GroqService groqService;
    private final QuizRepository quizRepository;
    private final ReponseEtudiantRepository reponseEtudiantRepository;
    private final CoursRepository coursRepository;
    private final EtudiantRepository etudiantRepository;
    private final AmeliorationRepository ameliorationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Quiz genererQuiz(Long coursId) {
        Cours cours = coursRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé"));

        if (quizRepository.findByCoursId(coursId).isPresent()) {
            return quizRepository.findByCoursId(coursId).get();
        }

        String prompt = String.format("Génère un quiz de 3 questions (avec options et bonne réponse) pour le cours suivant : %s", cours.getContenu());
        String systemPrompt = "Réponds UNIQUEMENT en JSON : [{\"question\": \"...\", \"options\": [\"...\"], \"reponseCorrecte\": \"...\"}]";

        String response = groqService.ask(systemPrompt, prompt);
        
        Quiz quiz = Quiz.builder()
                .titre("Quiz : " + cours.getTitre())
                .cours(cours)
                .score(0.0)
                .estReussi(false)
                .feedbackIA(response) // On stocke le JSON brut pour le front
                .build();
        
        return quizRepository.save(quiz);
    }

    @Transactional
    public Map<String, Object> soumettreQuiz(SubmitQuizRequest request) {
        Quiz quiz = quizRepository.findById(request.quizId())
                .orElseThrow(() -> new RuntimeException("Quiz non trouvé"));
        Etudiant etudiant = etudiantRepository.findById(request.etudiantId())
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        // Analyse IA des réponses
        String prompt = String.format("""
                Analyse ces réponses au quiz "%s".
                Réponses de l'étudiant : %s
                Quiz original (JSON) : %s
                
                Calcule la note sur 100.
                Identifie les points à améliorer.
                Réponds UNIQUEMENT en JSON : {"score": 80, "feedback": "...", "pointsAmelioration": ["...", "..."]}
                """, quiz.getTitre(), request.reponses().toString(), quiz.getFeedbackIA());

        String response = groqService.ask("Tu es un correcteur automatique.", prompt);
        try {
            Map<String, Object> result = objectMapper.readValue(response, new TypeReference<>() {});
            double score = ((Number) result.get("score")).doubleValue();
            String feedback = (String) result.get("feedback");
            List<String> points = (List<String>) result.get("pointsAmelioration");

            quiz.setScore(score);
            quiz.setEstReussi(score >= 60.0);
            quiz.setDatePassage(LocalDateTime.now());
            quiz.setFeedbackIA(feedback);
            quizRepository.save(quiz);

            // Créer les points d'amélioration
            for (String point : points) {
                Amelioration amel = Amelioration.builder()
                        .etudiant(etudiant)
                        .pointAmelioration(point)
                        .dateAction(LocalDateTime.now())
                        .conseilIA("Conseil IA basé sur ton erreur")
                        .build();
                ameliorationRepository.save(amel);
            }

            return result;
        } catch (Exception e) {
            log.error("Erreur analyse quiz", e);
            throw new RuntimeException("Erreur technique lors de la correction du quiz");
        }
    }
}
