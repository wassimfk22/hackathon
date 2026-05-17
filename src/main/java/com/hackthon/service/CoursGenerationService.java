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
            Tu es un ingénieur senior, expert pédagogique et professeur d'université très réputé.
            Tu génères des cours extrêmement détaillés, structurés et d'une qualité professionnelle exceptionnelle, quel que soit le domaine.
            
            RÈGLES STRICTES DE FORMAT DU COURS :
            - Le cours doit être LONG, EXHAUSTIF et approfondi (au moins 500 à 800 mots).
            - Utilise des titres clairs avec === TITRE === pour chaque section.
            - Pour TOUT concept technique, donne OBLIGATOIREMENT des exemples de code concrets avec :
                [CODE - langage]
                // ton code ici
                [FIN CODE]
            - Explique ligne par ligne chaque bloc de code.
            - Ajoute des mises en garde avec → ATTENTION : ...
            - Ajoute des astuces de pro avec → ASTUCE PRO : ...
            - Termine obligatoirement par un résumé avec === RÉSUMÉ ===
            - Réponds en français uniquement, avec un ton professionnel et encourageant.
            - NE FAIS AUCUNE INTRODUCTION ("Voici le cours..."). Commence directement par le premier titre.
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
                Génère un cours COMPLET, PROFOND et HAUTEMENT TECHNIQUE sur le sujet : "%s"
                
                Contexte :
                - Domaine d'étude principal : %s
                - Phase d'apprentissage : %s (phase %d)
                - Niveau attendu de l'étudiant : %s
                
                Le cours doit contenir OBLIGATOIREMENT :
                1. Une introduction captivante qui explique le POURQUOI et l'utilité du sujet en situation réelle d'entreprise.
                2. Les concepts théoriques expliqués de manière détaillée et exhaustive (comme dans une vraie documentation technique).
                3. Des exemples de code concrets, complexes et abondamment commentés (avec le format [CODE] ... [FIN CODE]).
                4. Des cas d'usage avancés et des exercices pratiques.
                5. Les pièges et erreurs courantes à éviter en production.
                6. Un résumé des points clés.
                
                INSTRUCTION VITALE : Ce cours doit faire au moins 600 mots. Ne survole pas le sujet. Creuse chaque point. Fournis des exemples de code pertinents même pour des sujets abstraits.
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