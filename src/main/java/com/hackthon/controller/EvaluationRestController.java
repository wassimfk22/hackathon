package com.hackthon.controller;

import com.hackthon.dto.EvaluationQuestionDTO;
import com.hackthon.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EvaluationRestController {

    private final EvaluationService evaluationService;

    @GetMapping("/questions")
    public ResponseEntity<List<EvaluationQuestionDTO>> getQuestions(@RequestParam Long etudiantId) {
        return ResponseEntity.ok(evaluationService.genererQuestions(etudiantId));
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, String>> submit(@RequestParam Long etudiantId, @RequestBody List<Map<String, String>> reponses) {
        String feedback = evaluationService.traiterResultats(etudiantId, reponses);
        return ResponseEntity.ok(Map.of("feedback", feedback));
    }
}
