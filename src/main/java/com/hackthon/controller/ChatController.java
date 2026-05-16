package com.hackthon.controller;

import com.hackthon.enums.ChatResponseType;
import com.hackthon.service.AIOrchestratorService;
import com.hackthon.service.ConversationStore;
import com.hackthon.service.GroqService;
import com.hackthon.service.RoadMapService;
import com.hackthon.repository.EtudiantRepository;
import com.hackthon.repository.DomaineRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ChatController {

    private final GroqService groqService;
    private final AIOrchestratorService orchestrator;
    private final ConversationStore conversationStore;
    private final RoadMapService roadMapService;
    private final EtudiantRepository etudiantRepository;
    private final DomaineRepository domaineRepository;

    @PostMapping
    public ResponseEntity<com.hackthon.dto.ChatResponse> chat(@Valid @RequestBody com.hackthon.dto.ChatRequest request) {
        Long userId = request.userId();
        String userMessage = request.message();

        log.info("Chat userId={} | message={}", userId, userMessage);

        conversationStore.addUserMessage(userId, userMessage);

        String rawResponse = groqService.askWithHistory(
                orchestrator.buildSystemPrompt(),
                conversationStore.getHistory(userId)
        );

        conversationStore.addAssistantMessage(userId, rawResponse);

        com.hackthon.dto.ChatResponse response = orchestrator.parseGroqResponse(rawResponse);
        log.info("Response type={} | niveau={} | roadmap={}",
                response.type(),
                response.niveau(),
                response.roadmap() != null ? response.roadmap().size() + " phases" : "null");

        // Associer le domaine dès le quiz
        if (response.type() == ChatResponseType.QUIZ) {
            associerDomaineEtudiant(userId, userMessage, response.message());
        }

        // Roadmap présente (type ROADMAP ou SCORE avec roadmap) → enregistrer en BDD
        if (response.roadmap() != null && !response.roadmap().isEmpty()) {
            log.info("Roadmap détectée ({} phases) — enregistrement BDD pour userId={}",
                    response.roadmap().size(), userId);
            try {
                roadMapService.enregistrerRoadMapIA(userId, response.niveau(), response.roadmap());
                log.info("✅ RoadMap enregistrée pour userId={}", userId);
            } catch (Exception e) {
                log.error("❌ Erreur enregistrement roadmap userId={} : {}", userId, e.getMessage(), e);
            }
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}/reset")
    public ResponseEntity<Map<String, String>> reset(@PathVariable Long userId) {
        conversationStore.clear(userId);
        return ResponseEntity.ok(Map.of("message", "Conversation réinitialisée"));
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<GroqService.ChatMessage>> history(@PathVariable Long userId) {
        return ResponseEntity.ok(conversationStore.getHistory(userId));
    }

    private void associerDomaineEtudiant(Long userId, String userMessage, String iaMessage) {
        etudiantRepository.findById(userId).ifPresent(etudiant -> {
            if (etudiant.getDomaine() != null) return;

            List<String> domaines = List.of("Java", "Python", "Git", "Intelligence Artificielle",
                    "DevOps", "Déploiement", "Conception");
            String combined = (userMessage + " " + iaMessage).toLowerCase();

            for (String nom : domaines) {
                if (combined.contains(nom.toLowerCase())) {
                    domaineRepository.findByNomContainingIgnoreCase(nom).ifPresent(domaine -> {
                        etudiant.setDomaine(domaine);
                        etudiantRepository.save(etudiant);
                        log.info("Domaine '{}' associé à étudiant {}", domaine.getNom(), userId);
                    });
                    break;
                }
            }
        });
    }
}