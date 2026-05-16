package com.hackthon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// ─── Requête d'explication ────────────────────────────────────────────────────

public record ExplicationRequest(
        @NotNull  Long   etudiantId,
        @NotBlank String extraitCours,   // la partie sélectionnée par l'étudiant
        @NotBlank String question        // ce que l'étudiant demande sur cet extrait
) {}