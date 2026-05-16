package com.hackthon.service;


import com.hackthon.dto.CoursDetailDTO;
import com.hackthon.dto.CoursResume;
import com.hackthon.dto.PhaseDetailDTO;
import com.hackthon.dto.RoadMapDetailDTO;
import com.hackthon.entity.*;
import com.hackthon.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadMapConsultationService {

    private final RoadMapRepository roadMapRepository;
    private final PhaseRepository phaseRepository;
    private final CoursRepository coursRepository;
    private final ProgressionRepository progressionRepository;
    private final EtudiantRepository etudiantRepository;
    private final CoursGenerationService coursGenerationService;

    /**
     * Retourne la roadmap COMPLÈTE de l'étudiant avec toutes les phases et cours
     * Sans le contenu des cours (trop lourd) — juste les métadonnées
     */
    @Transactional(readOnly = true)
    public RoadMapDetailDTO getRoadMapComplete(Long etudiantId) {
        RoadMap roadMap = roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiantId)
                .orElseThrow(() -> new RuntimeException("Aucune roadmap trouvée pour l'étudiant " + etudiantId));

        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        Progression progression = progressionRepository.findByEtudiant(etudiant)
                .orElse(null);

        double progressionGlobale = progression != null ? progression.getProgressionGlobale() : 0.0;

        List<Phase> phases = phaseRepository.findByRoadMapIdOrderByOrdrePhase(roadMap.getId());
        int phasesValidees = (int) phases.stream().filter(p -> Boolean.TRUE.equals(p.getEstValidee())).count();

        List<PhaseDetailDTO> phasesDTO = phases.stream()
                .map(phase -> {
                    List<Cours> coursList = coursRepository.findByPhaseIdOrderById(phase.getId());
                    List<CoursResume> coursResumes = coursList.stream()
                            .map(cours -> new CoursResume(
                                    cours.getId(),
                                    cours.getTitre(),
                                    cours.getNoteCours() != null ? cours.getNoteCours() : 0.0,
                                    cours.getContenu() != null && !cours.getContenu().isBlank()
                            ))
                            .toList();

                    return new PhaseDetailDTO(
                            phase.getId(),
                            phase.getTitre(),
                            phase.getOrdrePhase() != null ? phase.getOrdrePhase() : 0,
                            phase.getNotePhase() != null ? phase.getNotePhase() : 0.0,
                            Boolean.TRUE.equals(phase.getEstValidee()),
                            coursResumes
                    );
                })
                .toList();

        return new RoadMapDetailDTO(
                roadMap.getId(),
                roadMap.getTitre(),
                roadMap.getDomaine().getNom(),
                roadMap.getDateCreation(),
                roadMap.getStatut(),
                progressionGlobale,
                phases.size(),
                phasesValidees,
                phasesDTO
        );
    }

    /**
     * Retourne le contenu COMPLET d'un cours.
     * Si le contenu n'est pas encore généré → génération lazy à la demande.
     */
    @Transactional
    public CoursDetailDTO getCoursDetail(Long coursId) {
        // Génération lazy si pas encore fait
        Cours cours = coursGenerationService.genererOuRecupererCours(coursId);
        Phase phase = cours.getPhase();

        return new CoursDetailDTO(
                cours.getId(),
                cours.getTitre(),
                cours.getContenu(),
                cours.getTypeContenu(),
                cours.getDateGeneration(),
                cours.getNoteCours() != null ? cours.getNoteCours() : 0.0,
                phase.getId(),
                phase.getTitre(),
                phase.getOrdrePhase() != null ? phase.getOrdrePhase() : 0,
                false // TODO : marquer true si l'étudiant a passé le quiz avec succès
        );
    }

    /**
     * Liste tous les cours d'une phase (résumés)
     */
    @Transactional(readOnly = true)
    public List<CoursResume> getCoursByPhase(Long phaseId) {
        List<Cours> coursList = coursRepository.findByPhaseIdOrderById(phaseId);
        return coursList.stream()
                .map(cours -> new CoursResume(
                        cours.getId(),
                        cours.getTitre(),
                        cours.getNoteCours() != null ? cours.getNoteCours() : 0.0,
                        cours.getContenu() != null && !cours.getContenu().isBlank()
                ))
                .toList();
    }
}