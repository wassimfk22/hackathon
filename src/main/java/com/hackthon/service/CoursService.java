package com.hackthon.service;

import com.hackthon.entity.Cours;
import com.hackthon.entity.Etudiant;
import com.hackthon.entity.Phase;
import com.hackthon.entity.Progression;
import com.hackthon.enums.TypeContenu;
import com.hackthon.repository.CoursRepository;
import com.hackthon.repository.EtudiantRepository;
import com.hackthon.repository.PhaseRepository;
import com.hackthon.repository.ProgressionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoursService {

    private final GroqService groqService;
    private final CoursRepository coursRepository;
    private final PhaseRepository phaseRepository;
    private final EtudiantRepository etudiantRepository;
    private final ProgressionRepository progressionRepository;

    private static final String SYSTEM_COURS = """
            Tu es un enseignant expert en développement logiciel.
            Tu génères des cours pédagogiques, clairs et structurés.
            Utilise des exemples de code concrets quand c'est pertinent.
            Réponds directement avec le contenu du cours, sans introduction ni conclusion méta.
            """;

    @Transactional
    public Cours genererContenuCours(Long coursId) {
        Cours cours = coursRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé: " + coursId));

        if (cours.getContenu() != null && !cours.getContenu().isBlank()) {
            log.info("Contenu déjà généré pour cours {}", coursId);
            return cours;
        }

        Phase phase = cours.getPhase();
        String domaineNom = phase.getRoadMap().getDomaine().getNom();
        String phaseNom = phase.getTitre();

        String prompt = String.format("""
                Génère un cours complet sur "%s" dans le cadre de la phase "%s" du domaine "%s".
                
                Structure le cours avec :
                - Une introduction claire
                - Les concepts clés expliqués simplement
                - Des exemples pratiques ou extraits de code si applicable
                - Un résumé des points importants à retenir
                
                Le cours doit être suffisamment détaillé pour qu'un étudiant puisse apprendre efficacement.
                """, cours.getTitre(), phaseNom, domaineNom);

        String contenu = groqService.ask(SYSTEM_COURS, prompt);

        cours.setContenu(contenu);
        cours.setDateGeneration(LocalDateTime.now());
        cours.setTypeContenu(TypeContenu.TEXT);

        log.info("Contenu généré pour cours '{}' ({})", cours.getTitre(), coursId);
        return coursRepository.save(cours);
    }

    @Transactional(readOnly = true)
    public Cours getCours(Long coursId) {
        return coursRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé: " + coursId));
    }

    @Transactional(readOnly = true)
    public List<Cours> getCoursByPhase(Long phaseId) {
        phaseRepository.findById(phaseId)
                .orElseThrow(() -> new RuntimeException("Phase non trouvée: " + phaseId));
        return coursRepository.findByPhaseIdOrderById(phaseId);
    }

    @Transactional
    public void marquerCoursComplete(Long coursId, Long etudiantId) {
        Cours cours = coursRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé: " + coursId));
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé: " + etudiantId));

        Progression progression = progressionRepository.findByEtudiant(etudiant)
                .orElse(Progression.builder()
                        .etudiant(etudiant)
                        .coursTermines(0)
                        .quizReussis(0)
                        .phasesTerminees(0)
                        .progressionGlobale(0.0)
                        .build());

        progression.setCoursTermines(progression.getCoursTermines() + 1);
        progressionRepository.save(progression);

        log.info("Cours {} marqué complet pour étudiant {}", coursId, etudiantId);
    }
    
    
    
}