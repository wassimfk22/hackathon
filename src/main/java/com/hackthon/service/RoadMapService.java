package com.hackthon.service;

import com.hackthon.dto.ChatResponse;
import com.hackthon.dto.ProgressionDTO;
import com.hackthon.dto.RoadMapFullDTO;
import com.hackthon.entity.*;
import com.hackthon.enums.Niveau;
import com.hackthon.enums.StatutRoadMap;
import com.hackthon.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadMapService {

    private final RoadMapRepository roadMapRepository;
    private final PhaseRepository phaseRepository;
    private final EtudiantRepository etudiantRepository;
    private final DomaineRepository domaineRepository;
    private final ProgressionRepository progressionRepository;
    private final CoursRepository coursRepository;
    private final AsyncCourseService asyncCourseService;

    // ══════════════════════════════════════════════════════════════════════
    // ENREGISTREMENT DEPUIS LE CHAT
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public RoadMap enregistrerRoadMapIA(Long etudiantId, String niveauStr,
                                        List<ChatResponse.PhaseDTO> phasesIA) {
        log.info("enregistrerRoadMapIA() etudiantId={} niveau={} phases={}",
                etudiantId, niveauStr, phasesIA.size());

        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé: " + etudiantId));

        Domaine domaine = etudiant.getDomaine();
        if (domaine == null) {
            domaine = domaineRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("Aucun domaine en BDD"));
            log.warn("Fallback domaine id={} pour étudiant {}", domaine.getId(), etudiantId);
        }

        Niveau niveau = parseNiveau(niveauStr);
        etudiant.setNiveau(niveau);
        etudiantRepository.save(etudiant);

        // Marquer l'ancienne EN_COURS → TERMINEE (pas de delete = pas de risque rollback)
        roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiantId)
                .filter(old -> old.getStatut() == StatutRoadMap.EN_COURS)
                .ifPresent(old -> {
                    old.setStatut(StatutRoadMap.TERMINEE);
                    roadMapRepository.save(old);
                    log.info("Ancienne roadmap {} → TERMINEE", old.getId());
                });

        // Créer la RoadMap
        String titre = "Roadmap " + domaine.getNom() + " — " + niveau.name();
        RoadMap roadMap = roadMapRepository.save(RoadMap.builder()
                .titre(titre)
                .dateCreation(LocalDate.now())
                .statut(StatutRoadMap.EN_COURS)
                .etudiant(etudiant)
                .domaine(domaine)
                .phases(new ArrayList<>())
                .build());

        log.info("RoadMap créée id={} '{}'", roadMap.getId(), titre);

        // Créer phases + cours (contenu vide)
        List<Long> phaseIds = new ArrayList<>();
        for (ChatResponse.PhaseDTO phaseDTO : phasesIA) {
            Phase phase = phaseRepository.save(Phase.builder()
                    .titre(phaseDTO.titre())
                    .ordrePhase(phaseDTO.ordre())
                    .notePhase(0.0)
                    .estValidee(false)
                    .roadMap(roadMap)
                    .cours(new ArrayList<>())
                    .build());
            phaseIds.add(phase.getId());

            for (String coursTitre : phaseDTO.cours()) {
                coursRepository.save(Cours.builder()
                        .titre(coursTitre)
                        .contenu("")
                        .phase(phase)
                        .noteCours(0.0)
                        .build());
            }
            log.info("  Phase '{}' — {} cours", phaseDTO.titre(), phaseDTO.cours().size());
        }

        // Reset progression
        Progression progression = progressionRepository.findByEtudiant(etudiant)
                .orElse(Progression.builder().etudiant(etudiant).build());
        progression.setProgressionGlobale(0.0);
        progression.setPhasesTerminees(0);
        progression.setCoursTermines(0);
        progression.setQuizReussis(0);
        progressionRepository.save(progression);

        log.info("RoadMap id={} enregistrée — lancement génération async", roadMap.getId());
        asyncCourseService.genererCoursEnArrierePlan(phaseIds);

        return roadMap;
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONSULTATION — DTO COMPLET
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Retourne la roadmap COMPLÈTE avec toutes les relations chargées en DTO.
     * Plus de null : étudiant, domaine, phases, cours — tout est là.
     */
    @Transactional(readOnly = true)
    public RoadMapFullDTO getRoadMapFull(Long etudiantId) {
        RoadMap roadMap = roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiantId)
                .orElseThrow(() -> new RuntimeException("Aucune roadmap pour l'étudiant " + etudiantId));

        Etudiant e = roadMap.getEtudiant();
        Domaine d  = roadMap.getDomaine();

        // ── Étudiant DTO
        RoadMapFullDTO.EtudiantDTO etudiantDTO = e == null ? null : new RoadMapFullDTO.EtudiantDTO(
                e.getId(), e.getNom(), e.getPrenom(), e.getEmail(),
                e.getNiveau(), e.getScoreGlobal()
        );

        // ── Domaine DTO
        RoadMapFullDTO.DomaineDTO domaineDTO = d == null ? null : new RoadMapFullDTO.DomaineDTO(
                d.getId(), d.getNom(), d.getDescription()
        );

        // ── Phases + Cours
        List<Phase> phases = phaseRepository.findByRoadMapIdOrderByOrdrePhase(roadMap.getId());

        List<RoadMapFullDTO.PhaseFullDTO> phasesDTO = phases.stream().map(phase -> {
            List<Cours> coursList = coursRepository.findByPhaseIdOrderById(phase.getId());

            List<RoadMapFullDTO.CoursDTO> coursDTO = coursList.stream().map(cours ->
                    new RoadMapFullDTO.CoursDTO(
                            cours.getId(),
                            cours.getTitre(),
                            cours.getContenu(),
                            cours.getTypeContenu(),
                            cours.getDateGeneration(),
                            cours.getNoteCours() != null ? cours.getNoteCours() : 0.0,
                            cours.getContenu() != null && !cours.getContenu().isBlank()
                    )
            ).toList();

            return new RoadMapFullDTO.PhaseFullDTO(
                    phase.getId(),
                    phase.getTitre(),
                    phase.getOrdrePhase() != null ? phase.getOrdrePhase() : 0,
                    phase.getNotePhase() != null ? phase.getNotePhase() : 0.0,
                    Boolean.TRUE.equals(phase.getEstValidee()),
                    coursDTO
            );
        }).toList();

        // ── Métriques
        int totalCours    = phasesDTO.stream().mapToInt(p -> p.cours().size()).sum();
        int phasesValidees = (int) phasesDTO.stream().filter(RoadMapFullDTO.PhaseFullDTO::estValidee).count();

        Progression progression = progressionRepository.findByEtudiant(roadMap.getEtudiant()).orElse(null);
        double progressionGlobale = progression != null ? progression.getProgressionGlobale() : 0.0;

        return new RoadMapFullDTO(
                roadMap.getId(),
                roadMap.getTitre(),
                roadMap.getDateCreation(),
                roadMap.getStatut(),
                etudiantDTO,
                domaineDTO,
                phasesDTO,
                progressionGlobale,
                phases.size(),
                phasesValidees,
                totalCours
        );
    }

    // ── Ancienne méthode conservée pour compatibilité
    @Transactional(readOnly = true)
    public RoadMap getRoadMapByEtudiant(Long etudiantId) {
        return roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiantId)
                .orElseThrow(() -> new RuntimeException("Aucune roadmap pour l'étudiant " + etudiantId));
    }

    @Transactional(readOnly = true)
    public List<Phase> getPhasesByRoadMap(Long roadMapId) {
        return phaseRepository.findByRoadMapIdOrderByOrdrePhase(roadMapId);
    }

    @Transactional
    public ProgressionDTO getProgression(Long roadMapId) {
        RoadMap roadMap = roadMapRepository.findById(roadMapId)
                .orElseThrow(() -> new RuntimeException("RoadMap non trouvée: " + roadMapId));

        long totalCours  = coursRepository.countByPhase_RoadMap(roadMap);
        long totalPhases = phaseRepository.countByRoadMapId(roadMapId);

        Progression p = progressionRepository.findByEtudiant(roadMap.getEtudiant())
                .orElse(Progression.builder().coursTermines(0).quizReussis(0).phasesTerminees(0).build());

        double taux = 0.0;
        if (totalCours > 0)
            taux = Math.min(((p.getCoursTermines() * 0.5) + (p.getQuizReussis() * 0.5)) / totalCours * 100.0, 100.0);

        p.setProgressionGlobale(taux);
        progressionRepository.save(p);

        return new ProgressionDTO(taux, p.getCoursTermines(), (int) totalCours,
                p.getQuizReussis(), p.getPhasesTerminees(), (int) totalPhases);
    }

    @Transactional
    public double getNotePhase(Long phaseId) {
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new RuntimeException("Phase non trouvée: " + phaseId));

        double moyenne = coursRepository.findByPhaseId(phaseId).stream()
                .filter(c -> c.getNoteCours() != null)
                .mapToDouble(Cours::getNoteCours)
                .average().orElse(0.0);

        phase.setNotePhase(moyenne);
        if (moyenne >= 60.0) phase.setEstValidee(true);
        phaseRepository.save(phase);
        return moyenne;
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILITAIRE
    // ══════════════════════════════════════════════════════════════════════

    private Niveau parseNiveau(String s) {
        if (s == null) return Niveau.DEBUTANT;
        String clean = s.toUpperCase().trim()
                .replace("É", "E").replace("Ê", "E")
                .replace("Â", "A").replace("È", "E");
        return switch (clean) {
            case "INTERMEDIAIRE", "INTERMEDIATE" -> Niveau.INTERMEDIAIRE;
            case "AVANCE", "ADVANCED"            -> Niveau.AVANCE;
            case "EXPERT"                        -> Niveau.EXPERT;
            default                              -> Niveau.DEBUTANT;
        };
    }
}