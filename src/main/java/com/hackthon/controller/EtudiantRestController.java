package com.hackthon.controller;

import com.hackthon.dto.AuthRequest;
import com.hackthon.dto.RegisterRequest;
import com.hackthon.entity.Etudiant;
import com.hackthon.service.EtudiantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EtudiantRestController {

    private final EtudiantService etudiantService;

    @PostMapping("/register")
    public ResponseEntity<Etudiant> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(etudiantService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<Etudiant> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(etudiantService.login(request));
    }

    @PostMapping("/{etudiantId}/choisir-domaine/{domaineId}")
    public ResponseEntity<Etudiant> choisirDomaine(
            @PathVariable Long etudiantId,
            @PathVariable Long domaineId) {
        return ResponseEntity.ok(etudiantService.choisirDomaine(etudiantId, domaineId));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<java.util.List<com.hackthon.dto.LeaderboardUserDTO>> getLeaderboard() {
        return ResponseEntity.ok(etudiantService.getLeaderboard());
    }
}
