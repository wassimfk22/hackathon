package com.hackthon.dto;

import com.hackthon.enums.TypeContenu;

import java.time.LocalDateTime;

public record CoursDetailDTO(
        Long id,
        String titre,
        String contenu,         // le String complet généré par l'IA
        TypeContenu typeContenu,
        LocalDateTime dateGeneration,
        double noteCours,
        Long phaseId,
        String phaseTitre,
        int ordrePhase,
        boolean estValide       // true si l'étudiant a complété ce cours
) {}