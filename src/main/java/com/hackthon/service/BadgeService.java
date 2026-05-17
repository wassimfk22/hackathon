package com.hackthon.service;

import com.hackthon.entity.Badge;
import com.hackthon.entity.Etudiant;
import com.hackthon.entity.Phase;
import com.hackthon.entity.Cours;
import com.hackthon.entity.RoadMap;
import com.hackthon.repository.BadgeRepository;
import com.hackthon.repository.EtudiantRepository;
import com.hackthon.repository.PhaseRepository;
import com.hackthon.repository.CoursRepository;
import com.hackthon.repository.RoadMapRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final EtudiantRepository etudiantRepository;
    private final PhaseRepository phaseRepository;
    private final CoursRepository coursRepository;
    private final RoadMapRepository roadMapRepository;

    @PostConstruct
    @Transactional
    public void initDefaultBadges() {
        try {
            creerBadgeSiInexistant("BYTE PIONEER", "A validé son premier cours et posé la première pierre de son empire de compétences.", "pioneer");
            creerBadgeSiInexistant("PHASE SHIFTER", "A maîtrisé et complété avec succès une phase entière de sa roadmap personnalisée.", "shifter");
            creerBadgeSiInexistant("QUANTUM CENTURY", "A dépassé la barre mythique des 100 points d'XP grâce à sa persévérance.", "quantum");
        } catch (Exception ex) {
            log.error("Erreur lors de l'initialisation des badges par défaut", ex);
        }
    }

    private void creerBadgeSiInexistant(String nom, String description, String image) {
        Optional<Badge> optBadge = badgeRepository.findByNom(nom);
        if (optBadge.isEmpty()) {
            Badge badge = Badge.builder()
                    .nom(nom)
                    .description(description)
                    .image(image)
                    .build();
            badgeRepository.save(badge);
            log.info("Badge par défaut créé : '{}'", nom);
        }
    }

    @Transactional
    public void evaluerEtAttribuerBadges(Long etudiantId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé: " + etudiantId));

        log.info("Évaluation des badges pour l'étudiant id={}", etudiantId);

        // Trouver la roadmap active de l'étudiant
        RoadMap roadMap = roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiantId).orElse(null);
        if (roadMap == null) {
            log.warn("Aucune roadmap active pour l'étudiant id={}. Évaluation des badges annulée.", etudiantId);
            return;
        }

        List<Phase> phases = phaseRepository.findByRoadMapIdOrderByOrdrePhase(roadMap.getId());
        
        // 1. BYTE PIONEER : Au moins 1 cours complété (noteCours >= 70%)
        long completedCoursesCount = phases.stream()
                .flatMap(p -> {
                    List<Cours> coursList = coursRepository.findByPhaseIdOrderById(p.getId());
                    return (coursList != null ? coursList : List.<Cours>of()).stream();
                })
                .filter(c -> c.getNoteCours() != null && c.getNoteCours() >= 70.0)
                .count();

        if (completedCoursesCount >= 1) {
            attribuerBadge(etudiant, "BYTE PIONEER");
        }

        // 2. PHASE SHIFTER : Au moins 1 phase validée (estValidee = true)
        long validatedPhasesCount = phases.stream()
                .filter(p -> Boolean.TRUE.equals(p.getEstValidee()))
                .count();

        if (validatedPhasesCount >= 1) {
            attribuerBadge(etudiant, "PHASE SHIFTER");
        }

        // 3. QUANTUM CENTURY : scoreGlobal >= 100
        double xp = etudiant.getScoreGlobal() != null ? etudiant.getScoreGlobal() : 0.0;
        if (xp >= 100.0) {
            attribuerBadge(etudiant, "QUANTUM CENTURY");
        }
    }

    private void attribuerBadge(Etudiant etudiant, String badgeNom) {
        Badge badge = badgeRepository.findByNom(badgeNom)
                .orElseThrow(() -> new RuntimeException("Badge introuvable : " + badgeNom));

        if (!etudiant.getBadges().contains(badge)) {
            etudiant.getBadges().add(badge);
            etudiantRepository.save(etudiant);
            log.info("FÉLICITATIONS ! Badge '{}' attribué à l'étudiant id={}", badgeNom, etudiant.getId());
        }
    }
}
