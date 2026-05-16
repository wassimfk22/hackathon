package com.hackthon.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackthon.dto.*;
import com.hackthon.dto.ChatResponse.PhaseDTO;
import com.hackthon.dto.ChatResponse.QuizQuestion;
import com.hackthon.enums.ChatResponseType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIOrchestratorService {

    private final GroqService groqService;
    private final ObjectMapper objectMapper;

    // ─── System prompt principal ──────────────────────────────────────────

    public String buildSystemPrompt() {
        return """
            Tu es un Tuteur IA spécialisé en Software Engineering et Développement IT.
            Tu DOIS suivre ce flow séquentiel de manière stricte et SANS JAMAIS sauter d'étape.
            Tu réponds TOUJOURS en JSON pur, sans aucun markdown, sans texte avant ou après le JSON.

            ═══════════════════════════════════════════
            ÉTAPE 1 — ACCUEIL (PREMIER message de l'utilisateur)
            ═══════════════════════════════════════════
            Peu importe ce que dit l'utilisateur, tu réponds TOUJOURS par :
            - Un accueil chaleureux
            - La liste des domaines
            - Tu demandes UNIQUEMENT quel domaine il veut apprendre

            JSON attendu :
            {"type":"TEXT","message":"Bonjour ! 👋 Je suis ton tuteur IA en Software Engineering.\\n\\nVoici les domaines disponibles :\\n1. Java\\n2. Python\\n3. Git\\n4. Intelligence Artificielle\\n5. Déploiement DevOps\\n6. Conception Logicielle\\n\\nQuel domaine veux-tu apprendre ?"}

            ═══════════════════════════════════════════
            ÉTAPE 2 — DOMAINE CHOISI → Générer le Quiz
            ═══════════════════════════════════════════
            Dès que l'utilisateur mentionne un domaine, tu génères immédiatement un quiz de 5 questions QCM.
            Tu ne proposes plus le choix de donner son niveau manuellement.

            JSON attendu :
            {
              "type": "QUIZ",
              "message": "Excellent choix ! Voici un quiz de 5 questions pour évaluer ton niveau en [DOMAINE]. Réponds avec A, B, C ou D pour chaque question.",
              "questions": [
                {"numero": 1, "question": "...", "options": ["A) ...", "B) ...", "C) ...", "D) ..."]},
                ...
              ]
            }

            ═══════════════════════════════════════════
            ÉTAPE 3 — Correction du Quiz + Roadmap
            ═══════════════════════════════════════════
            Lorsque l'utilisateur donne ses réponses au quiz, tu dois :
            1. Corriger chaque question
            2. Calculer le score (/5)
            3. Déterminer le niveau :
               - 0-1 → DEBUTANT
               - 2-3 → INTERMEDIAIRE
               - 4-5 → AVANCE
            4. Générer une roadmap adaptée au niveau

            JSON attendu :
            {
              "type": "SCORE",
              "score": 3,
              "niveau": "INTERMEDIAIRE",
              "message": "Quiz terminé ! Tu as obtenu 3/5 ✅\\n\\nNiveau détecté : Intermédiaire\\n\\nVoici ta roadmap d'apprentissage personnalisée pour [DOMAINE] 🚀",
              "roadmap": [
                {"ordre": 1, "titre": "Phase 1 : Fondamentaux", "cours": ["Cours 1", "Cours 2", "Cours 3"]},
                {"ordre": 2, "titre": "Phase 2 : ...", "cours": ["Cours 1", "Cours 2"]},
                {"ordre": 3, "titre": "Phase 3 : ...", "cours": ["Cours 1", "Cours 2", "Cours 3"]},
                {"ordre": 4, "titre": "Phase 4 : Projets pratiques", "cours": ["Cours 1", "Cours 2"]}
              ]
            }

            ═══════════════════════════════════════════
            RÈGLES ABSOLUES
            ═══════════════════════════════════════════
            - Toujours respecter l'ordre : Accueil → Quiz → Correction + Roadmap
            - Jamais proposer le niveau manuel
            - Jamais donner la roadmap avant d'avoir corrigé le quiz
            - Répondre uniquement en JSON valide
            - Toujours répondre en français
            - Garder en mémoire le domaine choisi
            """;
    }

    // ─── Parsing de la réponse Groq → ChatResponse ────────────────────────

    public ChatResponse parseGroqResponse(String raw) {
        try {
            String clean = raw.trim()
                    .replaceAll("```json\\n?", "")
                    .replaceAll("```\\n?", "")
                    .trim();

            JsonNode root = objectMapper.readTree(clean);
            String typeStr = root.path("type").asText("TEXT");
            String message = root.path("message").asText("");

            return switch (typeStr) {

                case "QUIZ" -> {
                    List<QuizQuestion> questions = objectMapper.convertValue(
                            root.path("questions"),
                            new TypeReference<>() {}
                    );
                    yield ChatResponse.quiz(message, questions);
                }

                case "SCORE" -> {
                    int score = root.path("score").asInt(0);
                    String niveau = root.path("niveau").asText("DEBUTANT");

                    // Si la réponse contient aussi une roadmap
                    if (root.has("roadmap")) {
                        List<PhaseDTO> phases = objectMapper.convertValue(
                                root.path("roadmap"),
                                new TypeReference<>() {}
                        );
                        // On retourne ROADMAP avec le score en message
                        yield ChatResponse.roadmap(
                                message + " | Score : " + score + "/5",
                                niveau,
                                phases
                        );
                    }
                    yield ChatResponse.score(message, score, niveau);
                }

                case "ROADMAP" -> {
                    String niveau = root.path("niveau").asText("DEBUTANT");
                    List<PhaseDTO> phases = objectMapper.convertValue(
                            root.path("roadmap"),
                            new TypeReference<>() {}
                    );
                    yield ChatResponse.roadmap(message, niveau, phases);
                }

                case "NIVEAU" -> {
                    String niveau = root.path("niveau").asText("DEBUTANT");
                    yield ChatResponse.score(message, -1, niveau);
                }

                default -> ChatResponse.text(message.isBlank() ? raw : message);
            };

        } catch (Exception e) {
            log.warn("Impossible de parser le JSON Groq, retour en TEXT brut. Raw: {}", raw);
            // Si l'IA n'a pas respecté le format JSON, on retourne le texte brut
            return ChatResponse.text(raw);
        }
    }
}