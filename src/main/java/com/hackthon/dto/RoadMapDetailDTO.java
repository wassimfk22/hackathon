package com.hackthon.dto;
 
import com.hackthon.enums.StatutRoadMap;
 
import java.time.LocalDate;
import java.util.List;
 
// ─── RoadMap complète ─────────────────────────────────────────────────────────
 
public record RoadMapDetailDTO(
        Long id,
        String titre,
        String domaine,
        LocalDate dateCreation,
        StatutRoadMap statut,
        double progressionGlobale,
        int totalPhases,
        int phasesValidees,
        List<PhaseDetailDTO> phases
) {}