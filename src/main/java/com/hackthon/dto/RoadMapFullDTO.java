package com.hackthon.dto;

import com.hackthon.enums.Niveau;
import com.hackthon.enums.StatutRoadMap;
import com.hackthon.enums.TypeContenu;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RoadMapFullDTO(

        Long id,
        String titre,
        LocalDate dateCreation,
        StatutRoadMap statut,

        // Étudiant
        EtudiantDTO etudiant,

        // Domaine
        DomaineDTO domaine,

        // Phases avec cours
        List<PhaseFullDTO> phases,

        // Progression globale
        double progressionGlobale,
        int totalPhases,
        int phasesValidees,
        int totalCours

) {

    // ── Étudiant ──────────────────────────────────────────────────────────
    public record EtudiantDTO(
            Long id,
            String nom,
            String prenom,
            String email,
            Niveau niveau,
            Double scoreGlobal
    ) {}

    // ── Domaine ───────────────────────────────────────────────────────────
    public record DomaineDTO(
            Long id,
            String nom,
            String description
    ) {}

    // ── Phase ─────────────────────────────────────────────────────────────
    public record PhaseFullDTO(
            Long id,
            String titre,
            int ordrePhase,
            double notePhase,
            boolean estValidee,
            List<CoursDTO> cours
    ) {}

    // ── Cours ─────────────────────────────────────────────────────────────
    public record CoursDTO(
            Long id,
            String titre,
            String contenu,              // contenu complet String
            TypeContenu typeContenu,
            LocalDateTime dateGeneration,
            double noteCours,
            boolean contenuGenere        // true si contenu != vide
    ) {}
}