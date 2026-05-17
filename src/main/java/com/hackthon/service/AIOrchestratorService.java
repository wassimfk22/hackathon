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
            Tu t'appelles Digintor, et tu es un Tuteur IA spécialisé en Software Engineering et Développement IT.
            Tu dois TOUJOURS t'exprimer exclusivement en FRANÇAIS, et tu dois inclure ou rappeler ton nom "Digintor" dans tes réponses de manière naturelle.
            Tu DOIS suivre ce flow séquentiel de manière stricte et SANS JAMAIS sauter d'étape.
            Tu réponds TOUJOURS en JSON pur, sans aucun markdown, sans texte avant ou après le JSON.

            ═══════════════════════════════════════════
            ÉTAPE 1 — ACCUEIL & ORIENTATION
            ═══════════════════════════════════════════
            Pour le premier message de l'utilisateur ou s'il s'agit d'une salutation :
            - Souhaite un accueil chaleureux et propose la liste des domaines principaux disponibles :
              1. Java
              2. Python
              3. Git
              4. Intelligence Artificielle
              5. Déploiement DevOps
              6. Conception Logicielle
            - Si l'utilisateur demande d'autres domaines (comme le développement Web, Mobile, Cloud, Cyber-sécurité, etc.), te demande conseil pour s'orienter, ou pose une question d'introduction, réponds-lui de manière ouverte et personnalisée. Propose-lui d'autres thématiques pertinentes en IT, conseille-le, et guide-le interactivement tout en conservant le format JSON de type "TEXT".
            - Demande-lui quel domaine il souhaite explorer pour lancer son évaluation.

            JSON attendu pour l'accueil de base :
            {"type":"TEXT","message":"Bonjour ! 👋 Je suis Digintor, ton tuteur IA expert en développement IT.\\n\\nVoici les domaines d'apprentissage que je te propose :\\n1. Java\\n2. Python\\n3. Git\\n4. Intelligence Artificielle\\n5. Déploiement DevOps\\n6. Conception Logicielle\\n\\nQuel domaine veux-tu explorer aujourd'hui avec moi ? Tu peux aussi me proposer d'autres thématiques !"}

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
            - Toujours répondre exclusivement en FRANÇAIS
            - Parler en tant que "Digintor"
            - Garder en mémoire le domaine choisi
            """;
    }

    // ─── Parsing de la réponse Groq → ChatResponse ────────────────────────

    public ChatResponse parseGroqResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ChatResponse.text("Désolé, je n'ai pas pu générer de réponse.");
        }

        try {
            String clean = cleanAndExtractJson(raw);
            JsonNode root = objectMapper.readTree(clean);
            return convertJsonToResponse(root, raw);

        } catch (Exception e) {
            log.warn("Premier parsing JSON échoué, tentative de secours... Error: {}", e.getMessage());
            // Tentative de secours : si c'est du texte qui contient un bloc JSON
            try {
                String rescue = extractJsonBlock(raw);
                if (rescue != null) {
                    JsonNode root = objectMapper.readTree(cleanAndExtractJson(rescue));
                    return convertJsonToResponse(root, raw);
                }
            } catch (Exception e2) {
                log.error("Échec total du parsing JSON Groq. Raw: {}", raw);
            }
            return ChatResponse.text(raw);
        }
    }

    private String cleanAndExtractJson(String raw) {
        String clean = raw.trim();

        // 1. Supprimer les balises Markdown
        if (clean.contains("```json")) {
            clean = clean.substring(clean.indexOf("```json") + 7);
        } else if (clean.contains("```")) {
            clean = clean.substring(clean.indexOf("```") + 3);
        }
        if (clean.contains("```")) {
            clean = clean.substring(0, clean.lastIndexOf("```"));
        }
        clean = clean.trim();

        // 2. Extraire le bloc { ... } le plus large
        clean = extractJsonBlock(clean);

        // 3. Nettoyer les échappements invalides (ex: \é, \à, \✅)
        // On supprime les backslashes qui ne sont pas suivis d'un caractère d'échappement JSON standard
        if (clean != null) {
            clean = clean.replaceAll("\\\\(?![\\\"\\\\\\/bfnrtu])", "");
        }

        return clean != null ? clean : raw;
    }

    private String extractJsonBlock(String text) {
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return null;
    }

    private ChatResponse convertJsonToResponse(JsonNode root, String raw) {
        String typeStr = root.path("type").asText("TEXT");
        String message = root.path("message").asText("");

        // Cas particulier : l'IA a mis un JSON stringifié dans le champ message
        if ("TEXT".equals(typeStr) && message.trim().startsWith("{")) {
            try {
                JsonNode nested = objectMapper.readTree(cleanAndExtractJson(message));
                return convertJsonToResponse(nested, raw);
            } catch (Exception e) {
                log.debug("Le message ressemblait à du JSON mais n'était pas parsable.");
            }
        }

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

                if (root.has("roadmap")) {
                    List<PhaseDTO> phases = objectMapper.convertValue(
                            root.path("roadmap"),
                            new TypeReference<>() {}
                    );
                    yield ChatResponse.roadmap(message + " | Score : " + score + "/5", niveau, phases);
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

            case "NIVEAU" -> ChatResponse.score(message, -1, root.path("niveau").asText("DEBUTANT"));

            default -> ChatResponse.text(message.isBlank() ? raw : message);
        };
    }
}