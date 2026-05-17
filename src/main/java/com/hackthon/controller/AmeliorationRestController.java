package com.hackthon.controller;

import com.hackthon.entity.Amelioration;
import com.hackthon.repository.AmeliorationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ameliorations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AmeliorationRestController {

    private final AmeliorationRepository ameliorationRepository;

    @GetMapping("/{etudiantId}")
    public ResponseEntity<List<Amelioration>> getAmeliorations(@PathVariable Long etudiantId) {
        return ResponseEntity.ok(ameliorationRepository.findByEtudiantIdOrderByDateCreationDesc(etudiantId));
    }
}
