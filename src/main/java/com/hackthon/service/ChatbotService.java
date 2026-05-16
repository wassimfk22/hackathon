package com.hackthon.service;

import com.hackthon.dto.ChatRequest;
import com.hackthon.dto.ChatResponse;
import com.hackthon.dto.MessageDTO;
import com.hackthon.entity.Cours;
import com.hackthon.repository.CoursRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final GroqService groqService;
    private final CoursRepository coursRepository;

    // Historique en mémoire : clé = "etudiantId_coursId"
    private final Map<String, List<GroqService.ChatMessage>> historiqueMap = new ConcurrentHashMap<>();

    public ChatResponse poserQuestion(Long coursId, ChatRequest request) {
        Cours cours = coursRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé: " + coursId));

        if (cours.getContenu() == null || cours.getContenu().isBlank()) {
            throw new RuntimeException("Le cours n'a pas encore de contenu généré. Accède d'abord au cours.");
        }

        String sessionKey = request.etudiantId() + "_" + coursId;
        List<GroqService.ChatMessage> historique = historiqueMap
                .computeIfAbsent(sessionKey, k -> new ArrayList<>());

        // System prompt avec le contexte du cours injecté
        String systemPrompt = String.format("""
                Tu es un assistant pédagogique expert. Tu aides un étudiant à comprendre le cours suivant.
                
                === COURS : %s ===
                %s
                === FIN DU COURS ===
                
                Règles importantes :
                - Réponds UNIQUEMENT aux questions relatives à ce cours.
                - Si la question n'est pas liée au cours, dis poliment que tu ne peux répondre qu'aux questions sur ce cours.
                - Sois pédagogique, clair et encourage l'étudiant.
                - Si tu donnes du code, assure-toi qu'il est correct et commenté.
                """, cours.getTitre(), cours.getContenu());

        // Ajouter la question de l'étudiant à l'historique
        historique.add(new GroqService.ChatMessage("user", request.question()));

        // Appel Groq avec historique
        String reponse = groqService.askWithHistory(systemPrompt, historique);

        // Ajouter la réponse à l'historique pour les prochains tours
        historique.add(new GroqService.ChatMessage("assistant", reponse));

        // Limiter l'historique à 20 messages pour éviter de dépasser le contexte
        if (historique.size() > 20) {
            historiqueMap.put(sessionKey, new ArrayList<>(historique.subList(historique.size() - 20, historique.size())));
        }

        log.info("Chatbot: étudiant {} question sur cours {}", request.etudiantId(), coursId);

        return new ChatResponse(
                reponse,
                cours.getTitre(),
                LocalDateTime.now()
        );
    }

    public List<MessageDTO> getHistorique(Long coursId, Long etudiantId) {
        String sessionKey = etudiantId + "_" + coursId;
        List<GroqService.ChatMessage> historique = historiqueMap.getOrDefault(sessionKey, List.of());

        return historique.stream()
                .map(msg -> new MessageDTO(msg.role(), msg.content()))
                .toList();
    }

    public void clearHistorique(Long coursId, Long etudiantId) {
        String sessionKey = etudiantId + "_" + coursId;
        historiqueMap.remove(sessionKey);
        log.info("Historique chatbot effacé pour étudiant {} cours {}", etudiantId, coursId);
    }
    
    
    
}