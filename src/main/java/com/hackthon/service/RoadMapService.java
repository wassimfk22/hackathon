package com.hackthon.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackthon.dto.ChatResponse.PhaseDTO;
import com.hackthon.dto.ProgressionDTO;
import com.hackthon.entity.Cours;
import com.hackthon.entity.Domaine;
import com.hackthon.entity.Etudiant;
import com.hackthon.entity.Phase;
import com.hackthon.entity.Progression;
import com.hackthon.entity.RoadMap;
import com.hackthon.enums.Niveau;
import com.hackthon.enums.StatutRoadMap;
import com.hackthon.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
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

    // @Lazy pour éviter le cycle : RoadMapService ↔ CoursGenerationService
    @Autowired @Lazy
    private CoursGenerationService coursGenerationService;

    public RoadMapService(GroqService groqService,
                          RoadMapRepository roadMapRepository,
                          PhaseRepository phaseRepository,
                          EtudiantRepository etudiantRepository,
                          DomaineRepository domaineRepository,
                          ProgressionRepository progressionRepository,
                          CoursRepository coursRepository,
                          ObjectMapper objectMapper) {
        this.groqService = groqService;
        this.roadMapRepository = roadMapRepository;
        this.phaseRepository = phaseRepository;
        this.etudiantRepository = etudiantRepository;
        this.domaineRepository = domaineRepository;
        this.progressionRepository = progressionRepository;
        this.coursRepository = coursRepository;
        this.objectMapper = objectMapper;
    }

    // ══════════════════════════════════════════════════════════════════════
    // ENREGISTREMENT DEPUIS LE CHAT (appelé par ChatController)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Persiste la roadmap générée par l'IA dans le chat.
     * Crée RoadMap → Phases → Cours (contenu vide) → Progression.
     * Puis déclenche la génération des cours en arrière-plan.
     */
    @Transactional
    public RoadMap enregistrerRoadMapIA(Long etudiantId, String niveauStr, List<PhaseDTO> phasesIA) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé: " + etudiantId));

        // Récupérer le domaine de l'étudiant (déjà associé dans ChatController)
        Domaine domaine = etudiant.getDomaine();
        if (domaine == null) {
            // Fallback : prendre le premier domaine disponible
            domaine = domaineRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("Aucun domaine trouvé en BDD"));
        }

        // Niveau de l'étudiant
        Niveau niveau = parseNiveau(niveauStr);
        etudiant.setNiveau(niveau);
        etudiantRepository.save(etudiant);

        // Supprimer l'ancienne roadmap EN_COURS si elle existe (on repart propre)
        roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiantId)
                .ifPresent(old -> {
                    if (old.getStatut() == StatutRoadMap.EN_COURS) {
                        roadMapRepository.delete(old);
                        log.info("Ancienne roadmap supprimée pour étudiant {}", etudiantId);
                    }
                });

        // Créer la RoadMap
        String titreRoadMap = "Roadmap " + domaine.getNom() + " - " + niveau.name();
        RoadMap roadMap = RoadMap.builder()
                .titre(titreRoadMap)
                .dateCreation(LocalDate.now())
                .statut(StatutRoadMap.EN_COURS)
                .etudiant(etudiant)
                .domaine(domaine)
                .phases(new ArrayList<>())
                .build();
        roadMap = roadMapRepository.save(roadMap);

        // Créer les phases et leurs cours (contenu vide, sera généré en async)
        List<Long> phaseIds = new ArrayList<>();
        for (PhaseDTO phaseDTO : phasesIA) {
            Phase phase = Phase.builder()
                    .titre(phaseDTO.titre())
                    .ordrePhase(phaseDTO.ordre())
                    .notePhase(0.0)
                    .estValidee(false)
                    .roadMap(roadMap)
                    .cours(new ArrayList<>())
                    .build();
            phase = phaseRepository.save(phase);
            phaseIds.add(phase.getId());

            for (String coursTitre : phaseDTO.cours()) {
                Cours cours = Cours.builder()
                        .titre(coursTitre)
                        .contenu("")   // sera généré en arrière-plan
                        .phase(phase)
                        .noteCours(0.0)
                        .build();
                coursRepository.save(cours);
            }
        }

        // Initialiser la progression à zéro
        Progression progression = progressionRepository.findByEtudiant(etudiant)
                .orElse(Progression.builder().etudiant(etudiant).build());
        progression.setProgressionGlobale(0.0);
        progression.setPhasesTerminees(0);
        progression.setCoursTermines(0);
        progression.setQuizReussis(0);
        progressionRepository.save(progression);

        log.info("RoadMap '{}' enregistrée en BDD avec {} phases pour étudiant {}",
                titreRoadMap, phasesIA.size(), etudiantId);

        // Déclencher la génération des cours en arrière-plan (non bloquant)
        genererTousLesCourseAsync(phaseIds);

        return roadMap;
    }

    /**
     * Génère le contenu de tous les cours en arrière-plan, phase par phase.
     * Utilise @Async pour ne pas bloquer la réponse HTTP du chat.
     */
    /**
     * Génère le contenu de tous les cours en arrière-plan, phase par phase.
     * Utilise @Async pour ne pas bloquer la réponse HTTP du chat.
     */
    @Async
    public void genererTousLesCourseAsync(List<Long> phaseIds) {
        log.info("Début génération async des cours pour {} phases", phaseIds.size());
        for (Long phaseId : phaseIds) {
            try {
                coursGenerationService.genererTousLesCoursDePhase(phaseId);
                
                // 🚀 LA SOLUTION : On force une pause de 600ms entre chaque phase 
                // pour laisser respirer l'API de Groq et éviter l'erreur 429
                Thread.sleep(600);
                
            } catch (InterruptedException e) {
                log.error("La génération des cours a été interrompue");
                Thread.currentThread().interrupt(); // Restaurer le statut d'interruption
                break;
            } catch (Exception e) {
                log.error("Erreur génération cours phase {}: {}", phaseId, e.getMessage());
                // On continue les autres phases même si une échoue
            }
        }
        log.info("Génération async des cours terminée pour toutes les phases");
    }
    // ══════════════════════════════════════════════════════════════════════
    // CONSULTATION
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public RoadMap getRoadMapByEtudiant(Long etudiantId) {
        return roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiantId)
                .orElseThrow(() -> new RuntimeException("Aucune roadmap trouvée pour l'étudiant " + etudiantId));
    }

    @Transactional(readOnly = true)
    public List<Phase> getPhasesByRoadMap(Long roadMapId) {
        return phaseRepository.findByRoadMapIdOrderByOrdrePhase(roadMapId);
    }

    @Transactional
    public ProgressionDTO getProgression(Long roadMapId) {
        RoadMap roadMap = roadMapRepository.findById(roadMapId)
                .orElseThrow(() -> new RuntimeException("RoadMap non trouvée: " + roadMapId));

        long totalCours = coursRepository.countByPhase_RoadMap(roadMap);
        long totalPhases = phaseRepository.countByRoadMapId(roadMapId);

        Progression progression = progressionRepository.findByEtudiant(roadMap.getEtudiant())
                .orElse(Progression.builder().coursTermines(0).quizReussis(0).phasesTerminees(0).build());

        double tauxProgression = 0.0;
        if (totalCours > 0) {
            tauxProgression = ((progression.getCoursTermines() * 0.5) + (progression.getQuizReussis() * 0.5))
                    / totalCours * 100.0;
            tauxProgression = Math.min(tauxProgression, 100.0);
        }

        progression.setProgressionGlobale(tauxProgression);
        progressionRepository.save(progression);

        return new ProgressionDTO(
                tauxProgression,
                progression.getCoursTermines(),
                (int) totalCours,
                progression.getQuizReussis(),
                progression.getPhasesTerminees(),
                (int) totalPhases
        );
    }

    @Transactional
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

        phase.setNotePhase(moyenne);
        if (moyenne >= 60.0) phase.setEstValidee(true);
        phaseRepository.save(phase);

        return moyenne;
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILITAIRE
    // ══════════════════════════════════════════════════════════════════════

    private Niveau parseNiveau(String niveauStr) {
        if (niveauStr == null) return Niveau.DEBUTANT;
        return switch (niveauStr.toUpperCase().trim()) {
            case "INTERMEDIAIRE", "INTERMÉDIAIRE", "INTERMEDIATE" -> Niveau.INTERMEDIAIRE;
            case "AVANCE", "AVANCÉ", "ADVANCED"                  -> Niveau.AVANCE;
            case "EXPERT"                                          -> Niveau.EXPERT;
            default                                                -> Niveau.DEBUTANT;
        };
    }
}