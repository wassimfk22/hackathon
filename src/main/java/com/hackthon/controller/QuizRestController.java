package com.hackthon.controller;

import com.hackthon.dto.SubmitQuizRequest;
import com.hackthon.entity.Quiz;
import com.hackthon.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QuizRestController {

    private final QuizService quizService;

    @GetMapping("/generer")
    public ResponseEntity<Quiz> genererQuiz(@RequestParam Long coursId) {
        return ResponseEntity.ok(quizService.genererQuiz(coursId));
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitQuiz(@RequestBody SubmitQuizRequest request) {
        return ResponseEntity.ok(quizService.soumettreQuiz(request));
    }
}
