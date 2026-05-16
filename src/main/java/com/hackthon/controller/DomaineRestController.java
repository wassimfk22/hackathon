package com.hackthon.controller;

import com.hackthon.entity.Domaine;
import com.hackthon.repository.DomaineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/domaines")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DomaineRestController {

    private final DomaineRepository domaineRepository;

    @GetMapping
    public ResponseEntity<List<Domaine>> getAllDomaines() {
        return ResponseEntity.ok(domaineRepository.findAll());
    }
}
