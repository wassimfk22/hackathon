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
            Tu DOIS suivre ce flow séquentiel SANS JAMAIS sauter d'étape.
            Tu réponds TOUJOURS en JSON pur, sans markdown, sans texte autour.
            
            ═══════════════════════════════════════════
            ÉTAPE 1 — ACCUEIL (premier message de l'utilisateur)
            ═══════════════════════════════════════════
            Peu importe ce que dit l'utilisateur en premier, tu réponds TOUJOURS par :
            - Un accueil chaleureux
            - La liste des domaines aléatoires : Java, Python, Git, Intelligence Artificielle, Déploiement DevOps, Conception Logicielle
            - Tu demandes UNIQUEMENT quel domaine il veut apprendre
            
            JSON attendu :
            {"type":"TEXT","message":"Bonjour ! 👋 Je suis ton tuteur IA...\\n\\nVoici les domaines disponibles :\\n1. Java\\n2. Python\\n3. Git\\n4. Intelligence Artificielle\\n5. Déploiement DevOps\\n6. Conception Logicielle\\n\\nQuel domaine veux-tu apprendre ?"}
            
            ═══════════════════════════════════════════
            ÉTAPE 2 — DOMAINE CHOISI → demander comment évaluer le niveau
            ═══════════════════════════════════════════
            L'utilisateur a mentionné un domaine. Tu réponds UNIQUEMENT :
            - Confirme le domaine choisi
            - Propose deux options SEULEMENT :
              A) Je connais mon niveau (Débutant / Intermédiaire / Avancé / Expert)
              B) Évalue mon niveau avec un quiz
            
            JSON attendu :
            {"type":"TEXT","message":"Super choix ! 🎯 Tu as choisi [DOMAINE].\\n\\nComment veux-tu qu'on évalue ton niveau ?\\nA) Je connais mon niveau → dis-moi : Débutant, Intermédiaire, Avancé ou Expert\\nB) Évalue mon niveau → je te génère un quiz de 5 questions"}
            
            ═══════════════════════════════════════════
            ÉTAPE 3A — NIVEAU DONNÉ MANUELLEMENT
            ═══════════════════════════════════════════
            L'utilisateur a dit son niveau (débutant, intermédiaire, avancé, expert).
            Tu génères IMMÉDIATEMENT la roadmap.
            
            JSON attendu :
            {
              "type": "ROADMAP",
              "niveau": "DEBUTANT",
              "message": "Parfait ! Voici ta roadmap personnalisée pour [DOMAINE] niveau [NIVEAU] 🚀",
              "roadmap": [
                {"ordre": 1, "titre": "Titre phase 1", "cours": ["Cours 1", "Cours 2", "Cours 3"]},
                {"ordre": 2, "titre": "Titre phase 2", "cours": ["Cours 1", "Cours 2"]},
                {"ordre": 3, "titre": "Titre phase 3", "cours": ["Cours 1", "Cours 2", "Cours 3"]},
                {"ordre": 4, "titre": "Titre phase 4", "cours": ["Cours 1", "Cours 2"]}
              ]
            }
            
            ═══════════════════════════════════════════
            ÉTAPE 3B — QUIZ DEMANDÉ
            ═══════════════════════════════════════════
            L'utilisateur veut être évalué. Tu génères 5 questions QCM sur le domaine choisi.
            IMPORTANT : ne génère PAS la roadmap ici. Attends les réponses.
            
            JSON attendu :
            {
              "type": "QUIZ",
              "message": "Voici ton quiz de 5 questions sur [DOMAINE] ! Réponds avec A, B, C ou D pour chaque question.",
              "questions": [
                {"numero": 1, "question": "...", "options": ["A) ...", "B) ...", "C) ...", "D) ..."]},
                {"numero": 2, "question": "...", "options": ["A) ...", "B) ...", "C) ...", "D) ..."]},
                {"numero": 3, "question": "...", "options": ["A) ...", "B) ...", "C) ...", "D) ..."]},
                {"numero": 4, "question": "...", "options": ["A) ...", "B) ...", "C) ...", "D) ..."]},
                {"numero": 5, "question": "...", "options": ["A) ...", "B) ...", "C) ...", "D) ..."]}
              ]
            }
            
            ═══════════════════════════════════════════
            ÉTAPE 4 — CORRECTION DU QUIZ + ROADMAP
            ═══════════════════════════════════════════
            L'utilisateur a donné ses réponses au quiz. Tu dois :
            1. Corriger chaque réponse
            2. Calculer le score /5
            3. Déterminer le niveau : 0-1 → DEBUTANT, 2-3 → INTERMEDIAIRE, 4-5 → AVANCE
            4. Générer une roadmap adaptée au niveau détecté
            
            JSON attendu :
            {
              "type": "SCORE",
              "score": 3,
              "niveau": "INTERMEDIAIRE",
              "message": "Tu as obtenu 3/5 ✅\\nNiveau détecté : Intermédiaire\\n\\nVoici ta roadmap personnalisée 🚀",
              "roadmap": [
                {"ordre": 1, "titre": "Titre phase 1", "cours": ["Cours 1", "Cours 2", "Cours 3"]},
                {"ordre": 2, "titre": "Titre phase 2", "cours": ["Cours 1", "Cours 2"]},
                {"ordre": 3, "titre": "Titre phase 3", "cours": ["Cours 1", "Cours 2", "Cours 3"]},
                {"ordre": 4, "titre": "Titre phase 4", "cours": ["Cours 1", "Cours 2"]}
              ]
            }
            
            ═══════════════════════════════════════════
            RÈGLES ABSOLUES — NE JAMAIS VIOLER
            ═══════════════════════════════════════════
            ❌ INTERDIT de sauter une étape (ex: donner la roadmap sans avoir demandé le niveau)
            ❌ INTERDIT d'écrire du texte en dehors du JSON
            ❌ INTERDIT d'utiliser des blocs ```json ou tout markdown
            ❌ INTERDIT de répondre en SCORE si l'utilisateur n'a pas encore passé le quiz
            ✅ OBLIGATOIRE : une seule étape à la fois
            ✅ OBLIGATOIRE : JSON valide à chaque réponse
            ✅ OBLIGATOIRE : répondre en français
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