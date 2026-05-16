package com.hackthon.controller;

import com.hackthon.entity.Cours;
import com.hackthon.service.CoursService;
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

    private final CoursService coursService;

    /**
     * POST /api/cours/{coursId}/generate
     * Génère le contenu IA d'un cours (lazy generation à la première ouverture)
     */
    @PostMapping("/cours/{coursId}/generate")
    public ResponseEntity<Cours> genererCours(@PathVariable Long coursId) {
        return ResponseEntity.ok(coursService.genererContenuCours(coursId));
    }

    /**
     * GET /api/cours/{coursId}
     * Retourne le cours — génère le contenu si pas encore fait
     */
    @GetMapping("/cours/{coursId}")
    public ResponseEntity<Cours> getCours(@PathVariable Long coursId) {
        Cours cours = coursService.getCours(coursId);
        // Lazy generation : on génère à la première consultation
        if (cours.getContenu() == null || cours.getContenu().isBlank()) {
            cours = coursService.genererContenuCours(coursId);
        }
        return ResponseEntity.ok(cours);
    }

    /**
     * GET /api/phases/{phaseId}/cours
     * Liste tous les cours d'une phase
     */
    @GetMapping("/phases/{phaseId}/cours")
    public ResponseEntity<List<Cours>> getCoursByPhase(@PathVariable Long phaseId) {
        return ResponseEntity.ok(coursService.getCoursByPhase(phaseId));
    }

    /**
     * POST /api/cours/{coursId}/complete
     * Marque un cours comme complété par l'étudiant — met à jour la progression
     */
    @PostMapping("/cours/{coursId}/complete")
    public ResponseEntity<Map<String, Object>> marquerComplete(
            @PathVariable Long coursId,
            @RequestParam Long etudiantId) {
        coursService.marquerCoursComplete(coursId, etudiantId);
        return ResponseEntity.ok(Map.of(
                "message", "Cours marqué comme complété",
                "coursId", coursId,
                "etudiantId", etudiantId
        ));
    }
    
    
    
}