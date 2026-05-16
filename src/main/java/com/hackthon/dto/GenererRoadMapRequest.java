package com.hackthon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenererRoadMapRequest(
        @NotNull Long etudiantId,
        @NotNull Long domaineId,
        @NotBlank String niveau
) {}