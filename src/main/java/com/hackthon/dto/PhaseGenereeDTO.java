package com.hackthon.dto;

import java.util.List;

public record PhaseGenereeDTO(
        String titre,
        int ordrePhase,
        List<String> cours
) {}