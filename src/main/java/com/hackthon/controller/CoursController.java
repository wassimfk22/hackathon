package com.hackthon.controller;

import com.hackthon.dto.CoursDetailDTO;
import com.hackthon.dto.CoursResume;
import com.hackthon.dto.RoadMapDetailDTO;
import com.hackthon.dto.ExplicationResponse;
import com.hackthon.service.CoursExplicationService;
import com.hackthon.service.CoursGenerationService;
import com.hackthon.service.GroqService;
import com.hackthon.service.RoadMapConsultationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CoursController {

    private final RoadMapConsultationService consultationService;
    private final CoursGenerationService generationService;
    private final CoursExplicationService explicationService;

    // ══════════════════════════════════════════════════════════════════════
    // ROADMAP
    // ══════════════════════════════════════════════════════════════════════

    /**
     * GET /api/roadmap/etudiant/{etudiantId}
     * Roadmap complète : phases + liste des cours (sans contenu)
     */
    @GetMapping("/roadmap/etudiant/{etudiantId}")
    public ResponseEntity<RoadMapDetailDTO> getRoadMap(@PathVariable Long etudiantId) {
        return ResponseEntity.ok(consultationService.getRoadMapComplete(etudiantId));
    }

    /**
     * GET /api/phases/{phaseId}/cours
     * Liste des cours d'une phase (résumés, sans contenu)
     */
    @GetMapping("/phases/{phaseId}/cours")
    public ResponseEntity<List<CoursResume>> getCoursByPhase(@PathVariable Long phaseId) {
        return ResponseEntity.ok(consultationService.getCoursByPhase(phaseId));
    }

    // ══════════════════════════════════════════════════════════════════════
    // COURS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * GET /api/cours/{coursId}
     * Contenu complet du cours — génération lazy si pas encore fait
     */
    @GetMapping("/cours/{coursId}")
    public ResponseEntity<CoursDetailDTO> getCours(@PathVariable Long coursId) {
        return ResponseEntity.ok(consultationService.getCoursDetail(coursId));
    }

    /**
     * POST /api/cours/generate/phase/{phaseId}
     * Génère tous les cours d'une phase en une fois
     */
    @PostMapping("/cours/generate/phase/{phaseId}")
    public ResponseEntity<Map<String, String>> generateCoursPhase(@PathVariable Long phaseId) {
        generationService.genererTousLesCoursDePhase(phaseId);
        return ResponseEntity.ok(Map.of("message", "Génération des cours de la phase " + phaseId + " terminée"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPLICATION IA
    // ══════════════════════════════════════════════════════════════════════

    /**
     * POST /api/cours/{coursId}/expliquer
     * Body : { "etudiantId": 1, "extraitCours": "...", "question": "..." }
     */
    @PostMapping("/cours/{coursId}/expliquer")
    public ResponseEntity<ExplicationResponse> expliquer(
            @PathVariable Long coursId,
            @Valid @RequestBody com.hackthon.dto.ExplicationRequest request) {
        return ResponseEntity.ok(explicationService.expliquer(coursId, request));
    }

    /**
     * GET /api/cours/{coursId}/expliquer/historique/{etudiantId}
     */
    @GetMapping("/cours/{coursId}/expliquer/historique/{etudiantId}")
    public ResponseEntity<List<GroqService.ChatMessage>> getHistoriqueExplication(
            @PathVariable Long coursId,
            @PathVariable Long etudiantId) {
        return ResponseEntity.ok(explicationService.getHistorique(coursId, etudiantId));
    }

    /**
     * DELETE /api/cours/{coursId}/expliquer/reset/{etudiantId}
     */
    @DeleteMapping("/cours/{coursId}/expliquer/reset/{etudiantId}")
    public ResponseEntity<Map<String, String>> resetSession(
            @PathVariable Long coursId,
            @PathVariable Long etudiantId) {
        explicationService.clearSession(coursId, etudiantId);
        return ResponseEntity.ok(Map.of("message", "Session réinitialisée"));
    }
    
    
    
}