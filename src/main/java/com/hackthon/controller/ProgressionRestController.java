package com.hackthon.controller;

import com.hackthon.entity.Progression;
import com.hackthon.service.ProgressionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/progression")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ProgressionRestController {

    private final ProgressionService progressionService;

    @GetMapping("/{etudiantId}")
    public ResponseEntity<Progression> getProgression(@PathVariable Long etudiantId) {
        return ResponseEntity.ok(progressionService.getProgression(etudiantId));
    }

    @PostMapping("/{etudiantId}/add-xp")
    public ResponseEntity<Progression> addXp(@PathVariable Long etudiantId, @RequestParam int xp) {
        Progression prog = progressionService.addXp(etudiantId, xp);
        return ResponseEntity.ok(prog);
    }

    @GetMapping("/{etudiantId}/level-up")
    public ResponseEntity<String> checkLevelUp(@PathVariable Long etudiantId) {
        Progression p = progressionService.getProgression(etudiantId);
        return ResponseEntity.ok("Niveau : " + p.getNiveau() + " - " + p.getTitreRank() + " | XP: " + p.getXp());
    }
}