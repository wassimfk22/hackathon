package com.hackthon.service;

import com.hackthon.entity.Etudiant;
import com.hackthon.entity.Progression;
import com.hackthon.repository.EtudiantRepository;
import com.hackthon.repository.ProgressionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgressionService {

    private final ProgressionRepository progressionRepository;
    private final EtudiantRepository etudiantRepository;

    /**
     * Récupérer ou créer la progression d'un étudiant
     */
    public Progression getOrCreateProgression(Long etudiantId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        Optional<Progression> existing = progressionRepository.findByEtudiantId(etudiantId);

        if (existing.isPresent()) {
            return existing.get();
        }

        // Création initiale
        Progression progression = Progression.builder()
                .etudiant(etudiant)
                .xp(0)
                .niveau(1)
                .titreRank("Débutant")
                .progressionGlobale(0.0)
                .phasesTerminees(0)
                .coursTermines(0)
                .quizReussis(0)
                .build();

        return progressionRepository.save(progression);
    }

    /**
     * Ajouter de l'XP et gérer la montée de niveau
     */
    public Progression addXp(Long etudiantId, int xpGagne) {
        Progression progression = getOrCreateProgression(etudiantId);

        int nouveauXp = progression.getXp() + xpGagne;
        progression.setXp(nouveauXp);

        // Calcul du nouveau niveau
        int nouveauNiveau = calculateLevel(nouveauXp);

        if (nouveauNiveau > progression.getNiveau()) {
            progression.setNiveau(nouveauNiveau);
            progression.setTitreRank(getRankTitle(nouveauNiveau));
            // Tu peux ajouter une logique de notification "Level Up !" ici
        }

        // Mise à jour progression globale (en %)
        updateGlobalProgression(progression);

        return progressionRepository.save(progression);
    }

    /**
     * Formule simple et efficace de calcul de niveau
     */
    private int calculateLevel(int xp) {
        // Exemple : Niveau = sqrt(XP / 100) + 1  → courbe agréable
        return (int) Math.floor(Math.sqrt(xp / 100.0)) + 1;
    }

    /**
     * Ranks / Titres gamifiés (tu peux en ajouter beaucoup plus)
     */
    private String getRankTitle(int niveau) {
        return switch (niveau) {
            case 1 -> "Débutant";
            case 2, 3 -> "Apprenti";
            case 4, 5 -> "Codeur Junior";
            case 6, 7 -> "Développeur Confirmé";
            case 8, 9 -> "Senior Dev";
            case 10 -> "Master Coder";
            case 11, 12 -> "Architecte";
            default -> niveau >= 15 ? "Légende du Code" : "Expert";
        };
    }

    private void updateGlobalProgression(Progression progression) {
        // Tu peux combiner avec la progression des roadmaps
        double xpProgress = Math.min(100.0, (progression.getXp() % 1000) / 10.0); // exemple
        progression.setProgressionGlobale(xpProgress);
    }

    /**
     * Méthodes pratiques
     */
    public Progression getProgression(Long etudiantId) {
        return progressionRepository.findByEtudiantId(etudiantId)
                .orElseGet(() -> getOrCreateProgression(etudiantId));
    }

    public List<Progression> getAllProgressions() {
        return progressionRepository.findAll();
    }

    /**
     * Ajouter XP selon les actions (à appeler depuis QuizService, CoursService, etc.)
     */
    public void onCoursCompleted(Long etudiantId) {
        addXp(etudiantId, 50);   // +50 XP par cours terminé
    }

    public void onQuizPassed(Long etudiantId, int score) {
        int xp = score > 80 ? 100 : score > 60 ? 70 : 40;
        addXp(etudiantId, xp);
    }

    public void onPhaseCompleted(Long etudiantId) {
        addXp(etudiantId, 150);  // Bonus phase
    }
}