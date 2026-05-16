package com.hackthon.controller;

import com.hackthon.dto.PhaseDetailDTO;
import com.hackthon.dto.ProgressionDTO;
import com.hackthon.dto.RoadMapFullDTO;
import com.hackthon.service.RoadMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roadmaps")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoadMapController {

    private final RoadMapService roadMapService;

    /**
     * GET /api/roadmaps/{etudiantId}/full
     * ✅ Retourne TOUT : étudiant, domaine, phases, cours, progression
     * C'est l'endpoint principal à utiliser côté frontend
     */
    @GetMapping("/{etudiantId}/full")
    public ResponseEntity<RoadMapFullDTO> getRoadMapFull(@PathVariable Long etudiantId) {
        return ResponseEntity.ok(roadMapService.getRoadMapFull(etudiantId));
    }

    /**
     * GET /api/roadmaps/{roadmapId}/phases
     * Liste les phases d'une roadmap triées par ordre
     */
    @GetMapping("/{roadmapId}/phases")
    public ResponseEntity<List<PhaseDetailDTO>> getPhases(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(roadMapService.getPhasesByRoadMap(roadmapId));
    }

    /**
     * GET /api/roadmaps/{roadmapId}/progression
     * Taux de progression global
     */
    @GetMapping("/{roadmapId}/progression")
    public ResponseEntity<ProgressionDTO> getProgression(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(roadMapService.getProgression(roadmapId));
    }

    /**
     * GET /api/roadmaps/{roadmapId}/phases/{phaseId}/note
     * Note globale d'une phase
     */
    @GetMapping("/{roadmapId}/phases/{phaseId}/note")
    public ResponseEntity<Map<String, Object>> getNotePhase(
            @PathVariable Long roadmapId,
            @PathVariable Long phaseId) {
        double note = roadMapService.getNotePhase(phaseId);
        return ResponseEntity.ok(Map.of(
                "phaseId", phaseId,
                "noteGlobale", note,
                "estValidee", note >= 60.0
        ));
    }
}