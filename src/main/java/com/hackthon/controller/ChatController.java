package com.hackthon.controller;

import com.hackthon.dto.ChatRequest;
import com.hackthon.dto.ChatResponse;
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
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        Long userId = request.userId();
        String userMessage = request.message();

        log.info("Chat userId={} | message={}", userId, userMessage);

        // 1. Ajouter le message utilisateur à l'historique isolé
        conversationStore.addUserMessage(userId, userMessage);

        // 2. Appel Groq avec historique complet
        String rawResponse = groqService.askWithHistory(
                orchestrator.buildSystemPrompt(),
                conversationStore.getHistory(userId)
        );

        // 3. Ajouter la réponse IA à l'historique
        conversationStore.addAssistantMessage(userId, rawResponse);

        // 4. Parser → ChatResponse structuré
        ChatResponse response = orchestrator.parseGroqResponse(rawResponse);

        // ── Logique métier automatique ────────────────────────────────────

        // A. Associer le domaine à l'étudiant dès la détection du quiz
        if (response.type() == ChatResponseType.QUIZ) {
            associerDomaineEtudiant(userId, userMessage, response.message());
        }

        // B. Roadmap détectée → on persiste TOUT automatiquement en BDD
        //    + génération des cours en arrière-plan (@Async)
        if (response.type() == ChatResponseType.ROADMAP && response.roadmap() != null && !response.roadmap().isEmpty()) {
            try {
                roadMapService.enregistrerRoadMapIA(userId, response.niveau(), response.roadmap());
                log.info("RoadMap + cours enregistrés automatiquement pour userId={}", userId);
            } catch (Exception e) {
                // On ne fait pas planter la réponse si la BDD échoue
                log.error("Erreur enregistrement roadmap BDD pour userId={}: {}", userId, e.getMessage());
            }
        }

        // C. Score avec roadmap intégrée (type SCORE qui contient aussi une roadmap)
        if (response.type() == ChatResponseType.SCORE && response.roadmap() != null && !response.roadmap().isEmpty()) {
            try {
                roadMapService.enregistrerRoadMapIA(userId, response.niveau(), response.roadmap());
                log.info("RoadMap depuis SCORE enregistrée pour userId={}", userId);
            } catch (Exception e) {
                log.error("Erreur enregistrement roadmap (depuis SCORE) pour userId={}: {}", userId, e.getMessage());
            }
        }

        log.info("Chat userId={} | responseType={}", userId, response.type());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}/reset")
    public ResponseEntity<Map<String, String>> reset(@PathVariable Long userId) {
        conversationStore.clear(userId);
        log.info("Conversation réinitialisée pour userId={}", userId);
        return ResponseEntity.ok(Map.of("message", "Conversation réinitialisée"));
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<GroqService.ChatMessage>> history(@PathVariable Long userId) {
        return ResponseEntity.ok(conversationStore.getHistory(userId));
    }

    // ─── Privé : association automatique du domaine ───────────────────────

    private void associerDomaineEtudiant(Long userId, String userMessage, String iaMessage) {
        etudiantRepository.findById(userId).ifPresent(etudiant -> {
            if (etudiant.getDomaine() != null) return; // déjà associé

            List<String> domainesPossibles = List.of(
                    "Java", "Python", "Git", "Intelligence Artificielle",
                    "DevOps", "Déploiement", "Conception"
            );
            String combined = (userMessage + " " + iaMessage).toLowerCase();

            for (String nom : domainesPossibles) {
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