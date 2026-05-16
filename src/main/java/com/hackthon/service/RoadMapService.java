package com.hackthon.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackthon.entity.*;
import com.hackthon.enums.StatutRoadMap;
import com.hackthon.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadMapService {

    private final GroqService groqService;
    private final RoadMapRepository roadMapRepository;
    private final PhaseRepository phaseRepository;
    private final EtudiantRepository etudiantRepository;
    private final DomaineRepository domaineRepository;
    private final ProgressionRepository progressionRepository;
    private final CoursRepository coursRepository;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_ROADMAP = """
            Tu es un expert pédagogique en développement logiciel.
            Réponds UNIQUEMENT en JSON valide, sans texte autour, sans markdown.
            """;

    @Transactional
    public RoadMap genererRoadMap(Long etudiantId, Long domaineId, String niveau) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé: " + etudiantId));
        Domaine domaine = domaineRepository.findById(domaineId)
                .orElseThrow(() -> new RuntimeException("Domaine non trouvé: " + domaineId));

        String prompt = String.format("""
                Génère une roadmap d'apprentissage pour un étudiant de niveau %s qui veut apprendre "%s".
                La roadmap doit avoir entre 4 et 6 phases progressives.
                Chaque phase doit avoir 2 à 4 cours.
                
                Réponds UNIQUEMENT avec ce JSON (sans markdown, sans texte avant ou après) :
                {
                  "titre": "titre de la roadmap",
                  "phases": [
                    {
                      "titre": "titre de la phase",
                      "ordrePhase": 1,
                      "cours": ["titre cours 1", "titre cours 2"]
                    }
                  ]
                }
                """, niveau, domaine.getNom());

        String jsonResponse = groqService.ask(SYSTEM_ROADMAP, prompt);
        log.info("Réponse Groq roadmap: {}", jsonResponse);

        try {
            // Nettoyer la réponse au cas où
            String cleanJson = jsonResponse.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();
            }

            Map<String, Object> roadmapData = objectMapper.readValue(cleanJson, new TypeReference<>() {});

            // Créer la RoadMap
            RoadMap roadMap = RoadMap.builder()
                    .titre((String) roadmapData.get("titre"))
                    .dateCreation(LocalDate.now())
                    .statut(StatutRoadMap.EN_COURS)
                    .etudiant(etudiant)
                    .domaine(domaine)
                    .phases(new ArrayList<>())
                    .build();
            roadMap = roadMapRepository.save(roadMap);

            // Créer les phases
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> phasesData = (List<Map<String, Object>>) roadmapData.get("phases");

            for (Map<String, Object> phaseData : phasesData) {
                Phase phase = Phase.builder()
                        .titre((String) phaseData.get("titre"))
                        .ordrePhase((Integer) phaseData.get("ordrePhase"))
                        .notePhase(0.0)
                        .estValidee(false)
                        .roadMap(roadMap)
                        .cours(new ArrayList<>())
                        .build();
                phase = phaseRepository.save(phase);

                // Créer les cours (contenu vide, sera généré à la demande)
                @SuppressWarnings("unchecked")
                List<String> coursTitres = (List<String>) phaseData.get("cours");
                for (String coursTitre : coursTitres) {
                    Cours cours = Cours.builder()
                            .titre(coursTitre)
                            .contenu("") // sera généré à la demande
                            .phase(phase)
                            .noteCours(0.0)
                            .build();
                    coursRepository.save(cours);
                }
            }

            // Initialiser la progression
            long totalCours = coursRepository.countByPhase_RoadMap(roadMap);
            Progression progression = progressionRepository.findByEtudiant(etudiant)
                    .orElse(Progression.builder().etudiant(etudiant).build());
            progression.setProgressionGlobale(0.0);
            progression.setPhasesTerminees(0);
            progression.setCoursTermines(0);
            progression.setQuizReussis(0);
            progressionRepository.save(progression);

            log.info("RoadMap générée avec {} phases pour étudiant {}", phasesData.size(), etudiantId);
            return roadMap;

        } catch (Exception e) {
            log.error("Erreur parsing JSON roadmap: {}", jsonResponse, e);
            throw new RuntimeException("Impossible de parser la réponse de l'IA: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public RoadMap getRoadMapByEtudiant(Long etudiantId) {
        return roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiantId)
                .orElseThrow(() -> new RuntimeException("Aucune roadmap trouvée pour l'étudiant " + etudiantId));
    }

    @Transactional(readOnly = true)
    public List<Phase> getPhasesByRoadMap(Long roadMapId) {
        return phaseRepository.findByRoadMapIdOrderByOrdrePhase(roadMapId);
    }

    @Transactional(readOnly = true)
    public com.hackthon.dto.ProgressionDTO getProgression(Long roadMapId) {
        RoadMap roadMap = roadMapRepository.findById(roadMapId)
                .orElseThrow(() -> new RuntimeException("RoadMap non trouvée: " + roadMapId));

        long totalCours = coursRepository.countByPhase_RoadMap(roadMap);
        long totalPhases = phaseRepository.countByRoadMapId(roadMapId);

        Progression progression = progressionRepository.findByEtudiant(roadMap.getEtudiant())
                .orElse(Progression.builder().coursTermines(0).quizReussis(0).phasesTerminees(0).build());

        double tauxProgression = 0.0;
        if (totalCours > 0) {
            // (cours complétés × 0.5 + quizs réussis × 0.5) / total cours
            tauxProgression = ((progression.getCoursTermines() * 0.5) + (progression.getQuizReussis() * 0.5))
                    / totalCours * 100.0;
            tauxProgression = Math.min(tauxProgression, 100.0);
        }

        progression.setProgressionGlobale(tauxProgression);
        progressionRepository.save(progression);

        return new com.hackthon.dto.ProgressionDTO(
                tauxProgression,
                progression.getCoursTermines(),
                (int) totalCours,
                progression.getQuizReussis(),
                progression.getPhasesTerminees(),
                (int) totalPhases
        );
    }

    @Transactional(readOnly = true)
    public double getNotePhase(Long phaseId) {
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new RuntimeException("Phase non trouvée: " + phaseId));

        List<Cours> coursList = coursRepository.findByPhaseId(phaseId);
        if (coursList.isEmpty()) return 0.0;

        double moyenne = coursList.stream()
                .filter(c -> c.getNoteCours() != null)
                .mapToDouble(Cours::getNoteCours)
                .average()
                .orElse(0.0);

        // Mettre à jour la note de la phase
        phase.setNotePhase(moyenne);
        if (moyenne >= 60.0) {
            phase.setEstValidee(true);
        }
        phaseRepository.save(phase);

        return moyenne;
    }

    @Transactional
    public RoadMap enregistrerRoadMapIA(Long etudiantId, String niveau, List<com.hackthon.dto.ChatResponse.PhaseDTO> phasesData) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé: " + etudiantId));

        Domaine domaine = etudiant.getDomaine();
        if (domaine == null) {
            log.warn("L'étudiant {} n'a pas de domaine, impossible de lier la roadmap.", etudiantId);
            return null;
        }

        try {
            etudiant.setNiveau(com.hackthon.enums.Niveau.valueOf(niveau.toUpperCase()));
            etudiantRepository.save(etudiant);
        } catch (Exception e) {
            log.warn("Niveau inconnu reçu de l'IA: {}", niveau);
        }

        RoadMap roadMap = RoadMap.builder()
                .titre("Mon parcours " + domaine.getNom() + " (" + niveau + ")")
                .dateCreation(LocalDate.now())
                .statut(StatutRoadMap.EN_COURS)
                .etudiant(etudiant)
                .domaine(domaine)
                .phases(new ArrayList<>())
                .build();
        roadMap = roadMapRepository.save(roadMap);

        for (com.hackthon.dto.ChatResponse.PhaseDTO phaseDTO : phasesData) {
            Phase phase = Phase.builder()
                    .titre(phaseDTO.titre())
                    .ordrePhase(phaseDTO.ordre())
                    .notePhase(0.0)
                    .estValidee(false)
                    .roadMap(roadMap)
                    .cours(new ArrayList<>())
                    .build();
            phase = phaseRepository.save(phase);

            for (String titreCours : phaseDTO.cours()) {
                Cours cours = Cours.builder()
                        .titre(titreCours)
                        .phase(phase)
                        .build();
                coursRepository.save(cours);
            }
        }
        return roadMap;
    }
}