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

    @Transactional(readOnly = true)
    public List<EvaluationQuestionDTO> genererQuestions(Long etudiantId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));
        
        String domaineNom = etudiant.getDomaine().getNom();
        String prompt = "Génère 5 questions de niveau variable pour évaluer les compétences en " + domaineNom;

        String response = groqService.ask(SYSTEM_PROMPT, prompt);
        try {
            String cleanJson = response.trim();
            // Extraction robuste du JSON (cherche le premier [ et le dernier ])
            int start = cleanJson.indexOf("[");
            int end = cleanJson.lastIndexOf("]");
            if (start != -1 && end != -1 && end > start) {
                cleanJson = cleanJson.substring(start, end + 1);
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

        if (etudiant.getDomaine() == null) {
            throw new RuntimeException("L'étudiant n'est associé à aucun domaine d'étude.");
        }

        try {
            // 1. Convertir proprement les réponses en JSON valide pour Groq
            String reponsesJson = objectMapper.writeValueAsString(reponses);

            String prompt = String.format("""
                    Analyse ces réponses d'évaluation pour le domaine "%s" et détermine le niveau de l'étudiant.
                    Réponses de l'étudiant : %s
                    
                    Tu dois impérativement choisir une valeur textuelle exacte parmi ces trois-là pour le champ "niveau" : DEBUTANT, INTERMEDIAIRE, ou AVANCE.
                    
                    Réponds UNIQUEMENT avec ce format JSON (sans markdown, sans texte autour) : 
                    {"niveau": "DEBUTANT|INTERMEDIAIRE|AVANCE", "feedback": "Ton feedback ici"}
                    """, etudiant.getDomaine().getNom(), reponsesJson);

            String response = groqService.ask("Tu es un évaluateur technique rigoureux.", prompt);
            
            // 2. Extraction robuste du JSON
            String cleanJson = response.trim();
            int start = cleanJson.indexOf("{");
            int end = cleanJson.lastIndexOf("}");
            if (start != -1 && end != -1 && end > start) {
                cleanJson = cleanJson.substring(start, end + 1);
            }

            Map<String, String> result = objectMapper.readValue(cleanJson, new TypeReference<>() {});
            String niveauStr = result.get("niveau");
            String feedback = result.get("feedback");

            // 3. Normalisation et repli sécurisé pour l'Enum Niveau
            com.hackthon.enums.Niveau niveauEnum = com.hackthon.enums.Niveau.INTERMEDIAIRE; // Valeur par défaut
            if (niveauStr != null) {
                String cleanNiveau = niveauStr.toUpperCase().trim();
                try {
                    niveauEnum = com.hackthon.enums.Niveau.valueOf(cleanNiveau);
                } catch (IllegalArgumentException e) {
                    log.warn("Niveau renvoyé par l'IA inconnu ({}), repli sur INTERMEDIAIRE", niveauStr);
                    // Optionnel : faire un mapping si vos Enums s'appellent autrement (ex: BEGINNER)
                }
            }

            etudiant.setNiveau(niveauEnum);
            etudiantRepository.save(etudiant);

            // 4. Enregistrement de l'évaluation
            EvaluationIA evaluation = EvaluationIA.builder()
                    .etudiant(etudiant)
                    .domaine(etudiant.getDomaine())
                    .dateEvaluation(java.time.LocalDateTime.now())
                    .resultatIA(feedback)
                    .score(0.0) 
                    .build();
            evaluationRepository.save(evaluation);

            return feedback;

        } catch (Exception e) {
            log.error("Erreur critique lors de l'analyse des résultats", e);
            throw new RuntimeException("Erreur technique lors de l'analyse du niveau : " + e.getMessage());
        }
    }
}
