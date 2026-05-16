package com.hackthon.service;

import com.hackthon.entity.Cours;
import com.hackthon.entity.Phase;
import com.hackthon.enums.TypeContenu;
import com.hackthon.repository.CoursRepository;
import com.hackthon.repository.PhaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoursGenerationService {

    private final GroqService groqService;
    private final CoursRepository coursRepository;
    private final PhaseRepository phaseRepository;

    private static final String SYSTEM_COURS = """
            Tu es un expert pédagogique en développement logiciel.
            Tu génères des cours complets, structurés et progressifs.
            
            RÈGLES DE FORMAT DU COURS :
            - Utilise des titres clairs avec === TITRE === pour chaque section
            - Pour le code, utilise ce format :
                [CODE - langage]
                // ton code ici
                [FIN CODE]
            - Explique chaque bloc de code juste après
            - Utilise des exemples concrets et progressifs
            - Ajoute des notes importantes avec → NOTE : ...
            - Termine par un résumé avec === RÉSUMÉ ===
            - Réponds en français uniquement
            - Réponds DIRECTEMENT avec le contenu du cours, sans introduction méta du style "Voici le cours..."
            """;

    /**
     * Génère le contenu de TOUS les cours d'une phase (appelé après génération roadmap)
     */
    @Transactional
    public void genererTousLesCoursDePhase(Long phaseId) {
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new RuntimeException("Phase non trouvée: " + phaseId));

        List<Cours> coursList = coursRepository.findByPhaseIdOrderById(phaseId);
        log.info("Génération de {} cours pour la phase '{}'", coursList.size(), phase.getTitre());

        for (Cours cours : coursList) {
            if (cours.getContenu() == null || cours.getContenu().isBlank()) {
                genererContenuCours(cours, phase);
            }
        }
    }

    /**
     * Génère le contenu d'UN cours précis (lazy, à la première consultation)
     */
    @Transactional
    public Cours genererOuRecupererCours(Long coursId) {
        Cours cours = coursRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé: " + coursId));

        // Déjà généré → on retourne directement
        if (cours.getContenu() != null && !cours.getContenu().isBlank()) {
            log.info("Cours '{}' déjà généré, retour depuis BDD", cours.getTitre());
            return cours;
        }

        return genererContenuCours(cours, cours.getPhase());
    }

    // ─── Privé : logique de génération ───────────────────────────────────

    private Cours genererContenuCours(Cours cours, Phase phase) {
        String domaine   = phase.getRoadMap().getDomaine().getNom();
        String phaseNom  = phase.getTitre();
        String coursNom  = cours.getTitre();
        int ordrePhase   = phase.getOrdrePhase() != null ? phase.getOrdrePhase() : 1;

        // Calcul du niveau approximatif selon la position de la phase
        String niveauApprox = switch (ordrePhase) {
            case 1      -> "débutant (concepts de base, exemples très simples)";
            case 2      -> "intermédiaire-bas (concepts consolidés, exemples pratiques)";
            case 3      -> "intermédiaire (approfondissement, cas réels)";
            default     -> "avancé (patterns, bonnes pratiques, cas complexes)";
        };

        String prompt = String.format("""
                Génère un cours COMPLET sur le sujet : "%s"
                
                Contexte :
                - Domaine : %s
                - Phase d'apprentissage : %s (phase %d)
                - Niveau attendu : %s
                
                Le cours doit contenir OBLIGATOIREMENT :
                1. Une introduction qui explique l'utilité du sujet en situation réelle
                2. Les concepts théoriques expliqués clairement
                3. Des exemples de code commentés si le sujet le nécessite (avec le format [CODE] ... [FIN CODE])
                4. Des exercices pratiques ou cas d'usage
                5. Les erreurs courantes à éviter
                6. Un résumé des points clés
                
                Le cours doit être suffisamment détaillé pour qu'un étudiant comprenne sans aide extérieure.
                Si le sujet implique du code (boucles, fonctions, classes, requêtes SQL, commandes Git, etc.),
                donne OBLIGATOIREMENT des exemples de code concrets et commentés.
                """,
                coursNom, domaine, phaseNom, ordrePhase, niveauApprox
        );

        log.info("Génération IA en cours pour le cours '{}'...", coursNom);
        String contenu = groqService.ask(SYSTEM_COURS, prompt);

        cours.setContenu(contenu);
        cours.setDateGeneration(LocalDateTime.now());
        cours.setTypeContenu(TypeContenu.TEXT);
        cours.setNoteCours(0.0);

        Cours saved = coursRepository.save(cours);
        log.info("Cours '{}' généré et sauvegardé ({} caractères)", coursNom, contenu.length());
        return saved;
    }
    
    
    
}