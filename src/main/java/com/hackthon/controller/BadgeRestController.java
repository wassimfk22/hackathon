package com.hackthon.controller;

import com.hackthon.entity.Badge;
import com.hackthon.entity.Etudiant;
import com.hackthon.repository.EtudiantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BadgeRestController {

    private final EtudiantRepository etudiantRepository;

    @GetMapping("/{etudiantId}")
    public ResponseEntity<Set<Badge>> getBadges(@PathVariable Long etudiantId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));
        return ResponseEntity.ok(etudiant.getBadges());
    }
}
