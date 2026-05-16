package com.hackthon.dto;

public record ProgressionDTO(
        double tauxProgression,
        int coursTermines,
        int totalCours,
        int quizReussis,
        int phasesTerminees,
        int totalPhases
) {}