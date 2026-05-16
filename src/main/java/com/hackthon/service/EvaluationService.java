package com.hackthon.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackthon.dto.EvaluationQuestionDTO;
import com.hackthon.entity.Domaine;
import com.hackthon.entity.Etudiant;
import com.hackthon.entity.EvaluationIA;
import com.hackthon.enums.Niveau;
import com.hackthon.repository.DomaineRepository;
import com.hackthon.repository.EtudiantRepository;
import com.hackthon.repository.EvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {

    private final GroqService groqService;
    private final EvaluationRepository evaluationRepository;
    private final EtudiantRepository etudiantRepository;
    private final DomaineRepository domaineRepository;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            Tu es un expert en recrutement technique. 
            Génère 5 questions à choix multiples pour évaluer le niveau d'un candidat.
            Réponds UNIQUEMENT en JSON valide sous la forme d'une liste d'objets :
            [{"question": "...", "options": ["...", "..."]}]
            """;

    public List<EvaluationQuestionDTO> genererQuestions(Long etudiantId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));
        
        String domaineNom = etudiant.getDomaine().getNom();
        String prompt = "Génère 5 questions de niveau variable pour évaluer les compétences en " + domaineNom;

        String response = groqService.ask(SYSTEM_PROMPT, prompt);
        try {
            String cleanJson = response.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();
            }
            return objectMapper.readValue(cleanJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Erreur parsing questions evaluation: {}", response, e);
            throw new RuntimeException("Erreur technique lors de la génération des questions : " + e.getMessage());
        }
    }

    @Transactional
    public String traiterResultats(Long etudiantId, List<Map<String, String>> reponses) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        String prompt = String.format("""
                Analyse ces réponses d'évaluation pour le domaine %s et détermine le niveau de l'étudiant.
                Réponses : %s
                Réponds UNIQUEMENT avec un JSON : {"niveau": "DEBUTANT|INTERMEDIAIRE|AVANCE", "feedback": "..."}
                """, etudiant.getDomaine().getNom(), reponses.toString());

        String response = groqService.ask("Tu es un évaluateur technique.", prompt);
        try {
            String cleanJson = response.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();
            }
            Map<String, String> result = objectMapper.readValue(cleanJson, new TypeReference<>() {});
            String niveauStr = result.get("niveau");
            String feedback = result.get("feedback");

            etudiant.setNiveau(Niveau.valueOf(niveauStr));
            etudiantRepository.save(etudiant);

            EvaluationIA evaluation = EvaluationIA.builder()
                    .etudiant(etudiant)
                    .domaine(etudiant.getDomaine())
                    .dateEvaluation(LocalDateTime.now())
                    .resultatIA(feedback)
                    .score(0.0) // Score symbolique ou calculé
                    .build();
            evaluationRepository.save(evaluation);

            return feedback;
        } catch (Exception e) {
            log.error("Erreur analyse resultats evaluation", e);
            throw new RuntimeException("Erreur technique lors de l'analyse du niveau");
        }
    }
}
