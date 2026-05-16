package com.hackthon.controller;

import com.hackthon.entity.Phase;
import com.hackthon.entity.RoadMap;
import com.hackthon.service.RoadMapService;
import jakarta.validation.Valid;
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
     * POST /api/roadmaps/generate
     * Déclenché automatiquement après l'évaluation initiale
     */
    @PostMapping("/generate")
    public ResponseEntity<RoadMap> genererRoadMap(@Valid @RequestBody com.hackthon.dto.GenererRoadMapRequest request) {
        RoadMap roadMap = roadMapService.genererRoadMap(
                request.etudiantId(),
                request.domaineId(),
                request.niveau()
        );
        return ResponseEntity.ok(roadMap);
    }

    /**
     * GET /api/roadmaps/{etudiantId}
     * Retourne la roadmap active de l'étudiant
     */
    @GetMapping("/{etudiantId}")
    public ResponseEntity<RoadMap> getRoadMap(@PathVariable Long etudiantId) {
        return ResponseEntity.ok(roadMapService.getRoadMapByEtudiant(etudiantId));
    }

    /**
     * GET /api/roadmaps/{roadmapId}/phases
     * Liste les phases d'une roadmap, triées par ordre
     */
    @GetMapping("/{roadmapId}/phases")
    public ResponseEntity<List<Phase>> getPhases(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(roadMapService.getPhasesByRoadMap(roadmapId));
    }

    /**
     * GET /api/roadmaps/{roadmapId}/progression
     * Calcule et retourne le taux de progression global
     */
    @GetMapping("/{roadmapId}/progression")
    public ResponseEntity<com.hackthon.dto.ProgressionDTO> getProgression(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(roadMapService.getProgression(roadmapId));
    }

    /**
     * GET /api/roadmaps/{roadmapId}/phases/{phaseId}/note
     * Note globale d'une phase (moyenne des quizs)
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