//package com.hackthon.controller;
//
//import com.hackthon.dto.ChatRequest;
//import com.hackthon.dto.ChatResponse;
//import com.hackthon.dto.MessageDTO;
//import com.hackthon.service.ChatbotService;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/chatbot")
//@RequiredArgsConstructor
//@CrossOrigin(origins = "*")
//public class ChatbotController {
//
//    private final ChatbotService chatbotService;
//
//    /**
//     * POST /api/chatbot/{coursId}/ask
//     * Pose une question à l'IA dans le contexte du cours
//     */
//    @PostMapping("/{coursId}/ask")
//    public ResponseEntity<ChatResponse> ask(
//            @PathVariable Long coursId,
//            @Valid @RequestBody ChatRequest request) {
//        return ResponseEntity.ok(chatbotService.poserQuestion(coursId, request));
//    }
//
//    /**
//     * GET /api/chatbot/{coursId}/history/{etudiantId}
//     * Retourne l'historique de la session chatbot
//     */
//    @GetMapping("/{coursId}/history/{etudiantId}")
//    public ResponseEntity<List<MessageDTO>> getHistorique(
//            @PathVariable Long coursId,
//            @PathVariable Long etudiantId) {
//        return ResponseEntity.ok(chatbotService.getHistorique(coursId, etudiantId));
//    }
//
//    /**
//     * DELETE /api/chatbot/{coursId}/history/{etudiantId}
//     * Efface l'historique (nouvelle session)
//     */
//    @DeleteMapping("/{coursId}/history/{etudiantId}")
//    public ResponseEntity<Map<String, String>> clearHistorique(
//            @PathVariable Long coursId,
//            @PathVariable Long etudiantId) {
//        chatbotService.clearHistorique(coursId, etudiantId);
//        return ResponseEntity.ok(Map.of("message", "Historique effacé"));
//    }
//    
//    
//    
//}