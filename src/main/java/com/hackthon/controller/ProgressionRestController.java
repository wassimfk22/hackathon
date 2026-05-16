package com.hackthon.controller;

import com.hackthon.entity.Progression;
import com.hackthon.repository.ProgressionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/progression")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProgressionRestController {

    private final ProgressionRepository progressionRepository;

    @GetMapping("/{etudiantId}")
    public ResponseEntity<Progression> getProgression(@PathVariable Long etudiantId) {
        return ResponseEntity.ok(progressionRepository.findByEtudiantId(etudiantId)
                .orElseThrow(() -> new RuntimeException("Progression non trouvée")));
    }
}
