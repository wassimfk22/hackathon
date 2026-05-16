package com.hackthon.service;

import com.hackthon.dto.ExplicationRequest;
import com.hackthon.dto.ExplicationResponse;
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
public class CoursExplicationService {

    private final GroqService groqService;
    private final CoursRepository coursRepository;

    // Historique par session : clé = "etudiantId_coursId"
    // Chaque session garde le contexte du cours + l'historique de la conversation
    private final Map<String, List<GroqService.ChatMessage>> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> coursContextCache = new ConcurrentHashMap<>();

    private static final int MAX_TOUR = 20;

    /**
     * L'étudiant envoie un extrait du cours + sa question.
     * L'IA répond en tenant compte du cours complet ET de l'historique de la conversation.
     */
    public ExplicationResponse expliquer(Long coursId, ExplicationRequest request) {
        Cours cours = coursRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé: " + coursId));

        if (cours.getContenu() == null || cours.getContenu().isBlank()) {
            throw new RuntimeException("Ce cours n'a pas encore de contenu. Consulte d'abord le cours.");
        }

        String sessionKey = request.etudiantId() + "_" + coursId;

        // Mettre en cache le contexte du cours pour cette session
        coursContextCache.putIfAbsent(sessionKey, cours.getContenu());

        // Récupérer ou créer l'historique de la session
        List<GroqService.ChatMessage> historique = sessions.computeIfAbsent(sessionKey, k -> new ArrayList<>());

        // Construire le message utilisateur :
        // On inclut l'extrait sélectionné + la question dans le premier tour,
        // puis uniquement la question dans les tours suivants (le contexte est déjà dans l'historique)
        String messageUtilisateur;
        if (historique.isEmpty()) {
            // Premier tour : on précise l'extrait sélectionné
            messageUtilisateur = String.format("""
                    J'ai sélectionné cet extrait du cours :
                    ---
                    %s
                    ---
                    Ma question : %s
                    """, request.extraitCours().trim(), request.question().trim());
        } else {
            // Tours suivants : l'extrait peut changer (nouvelle sélection) ou l'étudiant pose une question de suivi
            if (request.extraitCours() != null && !request.extraitCours().isBlank()) {
                messageUtilisateur = String.format("""
                        J'ai maintenant sélectionné cet autre extrait :
                        ---
                        %s
                        ---
                        Ma question : %s
                        """, request.extraitCours().trim(), request.question().trim());
            } else {
                // Question de suivi pure, sans nouvel extrait
                messageUtilisateur = request.question().trim();
            }
        }

        // Ajouter le message de l'étudiant à l'historique
        historique.add(new GroqService.ChatMessage("user", messageUtilisateur));

        // System prompt avec le cours complet injecté
        String systemPrompt = buildSystemPrompt(cours, coursContextCache.get(sessionKey));

        // Appel Groq avec historique complet
        String reponse = groqService.askWithHistory(systemPrompt, historique);

        // Ajouter la réponse à l'historique
        historique.add(new GroqService.ChatMessage("assistant", reponse));

        // Trim de l'historique si trop long
        if (historique.size() > MAX_TOUR) {
            // Garder les 2 premiers messages (contexte initial) + les 18 derniers
            List<GroqService.ChatMessage> trimmed = new ArrayList<>();
            trimmed.addAll(historique.subList(0, 2));
            trimmed.addAll(historique.subList(historique.size() - 18, historique.size()));
            sessions.put(sessionKey, trimmed);
        }

        int tour = (historique.size() / 2);
        log.info("Explication tour {} | étudiant={} cours='{}'", tour, request.etudiantId(), cours.getTitre());

        return new ExplicationResponse(
                reponse,
                request.extraitCours(),
                tour,
                LocalDateTime.now()
        );
    }

    /**
     * Retourne l'historique complet de la session de questions sur un cours
     */
    public List<GroqService.ChatMessage> getHistorique(Long coursId, Long etudiantId) {
        String sessionKey = etudiantId + "_" + coursId;
        return sessions.getOrDefault(sessionKey, List.of());
    }

    /**
     * Réinitialise la session (l'étudiant repart de zéro sur ce cours)
     */
    public void clearSession(Long coursId, Long etudiantId) {
        String sessionKey = etudiantId + "_" + coursId;
        sessions.remove(sessionKey);
        coursContextCache.remove(sessionKey);
        log.info("Session explication réinitialisée | étudiant={} cours={}", etudiantId, coursId);
    }

    // ─── Privé ───────────────────────────────────────────────────────────────

    private String buildSystemPrompt(Cours cours, String contenuCours) {
        return String.format("""
                Tu es un tuteur pédagogique expert en développement logiciel.
                Un étudiant est en train de lire le cours suivant et te pose des questions.
                
                ════════════════════════════════════════════
                COURS : %s
                ════════════════════════════════════════════
                %s
                ════════════════════════════════════════════
                
                TES RÈGLES :
                1. Réponds UNIQUEMENT aux questions liées à ce cours ou au sujet de ce cours.
                2. Si l'étudiant sélectionne un extrait, explique cet extrait précisément.
                3. Si l'étudiant pose une question de suivi, garde le contexte de la conversation.
                4. Utilise des exemples simples et progressifs.
                5. Si une explication nécessite du code, donne un exemple clair et commenté.
                6. Encourage l'étudiant, reste bienveillant.
                7. Réponds en français.
                8. Si la question n'est pas liée au cours, dis : "Cette question dépasse le cadre de ce cours. Concentrons-nous sur [TITRE DU COURS] 😊"
                """,
                cours.getTitre(),
                contenuCours
        );
    }
    
    
    
}