package com.hackthon.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackthon.dto.QuizDTO.*;
import com.hackthon.entity.*;
import com.hackthon.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {

    private final GroqService groqService;
    private final ObjectMapper objectMapper;
    private final CoursRepository coursRepository;
    private final QuizRepository quizRepository;
    private final EtudiantRepository etudiantRepository;
    private final PhaseRepository phaseRepository;
    private final ReponseEtudiantRepository reponseEtudiantRepository;
    private final ProgressionRepository progressionRepository;
    private final AmeliorationRepository ameliorationRepository;
    private final BadgeService badgeService;

    // 10 points par quiz
    private static final int POINTS_PAR_QUIZ = 10;

    private static final String SYSTEM_QUIZ = """
            Tu es un concepteur pédagogique expert. Ta mission est de générer des quiz QCM d'excellente qualité, difficiles mais justes, basés UNIQUEMENT sur le cours fourni.
            RÈGLE ABSOLUE : Tu dois répondre EXCLUSIVEMENT avec du JSON valide. N'ajoute AUCUN texte avant ou après le JSON. N'utilise pas de blocs markdown (```json). Renvoie juste les accolades {}.
            """;

    private static final String SYSTEM_CORRECTION = """
            Tu es un correcteur pédagogique bienveillant et précis.
            Tu corriges des réponses de quiz et tu fournis des explications claires.
            Réponds UNIQUEMENT en JSON valide, sans markdown, sans texte autour.
            """;

    // ══════════════════════════════════════════════════════════════════════
    // 1. TERMINER UN COURS → générer le quiz
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public QuizGeneréDTO terminerCoursEtGenererQuiz(Long coursId, Long etudiantId) {
        Cours cours = coursRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours non trouvé: " + coursId));
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé: " + etudiantId));

        if (cours.getContenu() == null || cours.getContenu().isBlank())
            throw new RuntimeException("Le cours n'a pas encore de contenu généré.");

        // Si un quiz existe déjà et a été réussi avec >= 70%, on le retourne directement
        // Utilisation de findByCoursId car Cours <-> Quiz est une relation @OneToOne unique en BDD
        Quiz existingQuiz = quizRepository.findByCoursId(coursId).orElse(null);
        if (existingQuiz != null && Boolean.TRUE.equals(existingQuiz.getEstGenere())) {
            // Associer l'étudiant actuel s'il a changé ou était absent
            if (existingQuiz.getEtudiant() == null || !existingQuiz.getEtudiant().getId().equals(etudiantId)) {
                existingQuiz.setEtudiant(etudiant);
                existingQuiz = quizRepository.save(existingQuiz);
            }

            if (existingQuiz.getPourcentage() != null && existingQuiz.getPourcentage() >= 70.0) {
                return parseQuizExistant(existingQuiz, cours);
            } else {
                log.info("L'étudiant n'a pas atteint les 70% requis pour le cours id={}. Régénération du quiz en place...", coursId);
                // Supprimer les anciennes réponses d'abord
                reponseEtudiantRepository.deleteByQuizId(existingQuiz.getId());
                reponseEtudiantRepository.flush();
                return genererOuMettreAJourQuiz(cours, etudiant, existingQuiz);
            }
        }
        
        return genererOuMettreAJourQuiz(cours, etudiant, null);
    }

    private QuizGeneréDTO genererOuMettreAJourQuiz(Cours cours, Etudiant etudiant, Quiz existingQuiz) {
        Phase phase = cours.getPhase();

        String prompt = String.format("""
                Génère un quiz de 5 questions QCM de NIVEAU AVANCÉ basé EXCLUSIVEMENT sur le contenu de cours ci-dessous.
                Même si le cours semble général, trouve 5 points précis à tester.
                
                COURS : %s
                CONTENU :
                %s
                
                RÈGLES STRICTES :
                - Génère EXACTEMENT 5 questions.
                - Chaque question doit avoir EXACTEMENT 4 options : "A) ...", "B) ...", "C) ...", "D) ...".
                - Ne pose JAMAIS de questions hors du contexte du texte fourni.
                
                FORMAT JSON ATTENDU (Renvoie UNIQUEMENT ça, SANS COMMENTAIRES NI MARKDOWN) :
                {
                  "questions": [
                    {
                      "numero": 1,
                      "question": "Texte de la question ?",
                      "options": ["A) ...", "B) ...", "C) ...", "D) ..."],
                      "bonneReponse": "A",
                      "explication": "Explication claire de la bonne réponse"
                    }
                  ]
                }
                """, cours.getTitre(), cours.getContenu());

        log.info("Génération/Mise à jour quiz pour cours '{}' étudiant {}", cours.getTitre(), etudiant.getId());
        
        List<Map<String, Object>> questionsRaw = null;
        String questionsJson = null;

        try {
            String raw = groqService.ask(SYSTEM_QUIZ, prompt);
            String clean = extractJson(raw);
            Map<String, Object> parsed = objectMapper.readValue(clean, new TypeReference<>() {});
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> qRaw = (List<Map<String, Object>>) parsed.get("questions");
            if (qRaw != null && !qRaw.isEmpty()) {
                questionsRaw = qRaw;
                questionsJson = objectMapper.writeValueAsString(questionsRaw);
            }
        } catch (Exception e) {
            log.warn("L'IA a échoué à générer le quiz ou le JSON est invalide pour le cours '{}'. Utilisation du générateur de quiz de secours. Raison : {}", cours.getTitre(), e.getMessage());
        }

        // Si la génération par l'IA a échoué, on génère le quiz de secours
        if (questionsRaw == null || questionsRaw.isEmpty()) {
            try {
                questionsJson = genererQuizFallback(cours);
                questionsRaw = objectMapper.readValue(questionsJson, new TypeReference<>() {});
            } catch (Exception ex) {
                log.error("Erreur critique lors de la génération du quiz de secours", ex);
                questionsRaw = new ArrayList<>();
                questionsJson = "[]";
            }
        }

        try {
            // Calculer les pointsMax de la phase (POINTS_PAR_QUIZ par cours)
            long nbCours = coursRepository.findByPhaseIdOrderById(phase.getId()).size();
            double pointsMaxPhase = nbCours * POINTS_PAR_QUIZ;
            phase.setPointsMax(pointsMaxPhase);
            if (phase.getPointsObtenus() == null) phase.setPointsObtenus(0.0);
            phaseRepository.save(phase);

            Quiz quiz;
            if (existingQuiz != null) {
                // Mettre à jour le quiz existant en place
                quiz = existingQuiz;
                quiz.setTitre("Quiz — " + cours.getTitre());
                quiz.setEstGenere(true);
                quiz.setEstReussi(false);
                quiz.setScoreObtenu(0);
                quiz.setScoreMax(questionsRaw.size());
                quiz.setPourcentage(0.0);
                quiz.setQuestionsJson(questionsJson);
                quiz.setFeedbackIA(null);
                quiz.setDatePassage(null);
                quiz.setEtudiant(etudiant); // S'assurer de la bonne association de l'étudiant
            } else {
                // Créer le Quiz en BDD
                quiz = Quiz.builder()
                        .titre("Quiz — " + cours.getTitre())
                        .estGenere(true)
                        .estReussi(false)
                        .scoreObtenu(0)
                        .scoreMax(questionsRaw.size())
                        .pourcentage(0.0)
                        .questionsJson(questionsJson)
                        .cours(cours)
                        .etudiant(etudiant)
                        .build();
            }
            quiz = quizRepository.save(quiz);

            // Mettre à jour la progression (cours terminé) seulement si c'est la première fois
            if (existingQuiz == null) {
                majProgressionCoursTermine(etudiant);
            }

            log.info("Quiz id={} généré/mis à jour ({} questions) pour cours '{}'",
                    quiz.getId(), questionsRaw.size(), cours.getTitre());

            // Retourner les questions SANS les bonnes réponses
            List<QuestionDTO> questionsSansReponses = questionsRaw.stream()
                    .map(q -> new QuestionDTO(
                            (Integer) q.get("numero"),
                            (String) q.get("question"),
                            (List<String>) q.get("options")
                    )).toList();

            return new QuizGeneréDTO(
                    quiz.getId(), cours.getId(), cours.getTitre(),
                    phase.getTitre(), questionsRaw.size(), questionsSansReponses
            );

        } catch (Exception e) {
            log.error("Erreur critique génération/sauvegarde quiz: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur génération quiz: " + e.getMessage(), e);
        }
    }

    private String extractJson(String raw) {
        if (raw == null) return "";
        int firstBrace = raw.indexOf("{");
        int lastBrace = raw.lastIndexOf("}");
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return raw.substring(firstBrace, lastBrace + 1);
        }
        return raw;
    }

    private String genererQuizFallback(Cours cours) {
        String titre = cours.getTitre() != null ? cours.getTitre() : "Ce cours";
        
        List<Map<String, Object>> questions = new ArrayList<>();
        
        // Question 1 : Définition
        Map<String, Object> q1 = new java.util.HashMap<>();
        q1.put("numero", 1);
        q1.put("question", "Quel est l'objectif principal du cours \"" + titre + "\" ?");
        q1.put("options", List.of(
            "A) Introduire les concepts fondamentaux et pratiques associés.",
            "B) Remplacer tous les langages existants par un nouveau paradigme.",
            "C) Éliminer le besoin de bases de données relationnelles.",
            "D) Fournir une suite d'outils purement théoriques sans application."
        ));
        q1.put("bonneReponse", "A");
        q1.put("explication", "Le cours vise principalement à introduire les notions et pratiques essentielles.");
        questions.add(q1);

        // Question 2 : Notion clé
        Map<String, Object> q2 = new java.util.HashMap<>();
        q2.put("numero", 2);
        q2.put("question", "Selon le contenu du cours, laquelle de ces propositions est correcte ?");
        q2.put("options", List.of(
            "A) La syntaxe et la logique présentées sont universelles.",
            "B) Les concepts expliqués ne s'appliquent qu'à un seul cas d'usage restreint.",
            "C) Il n'y a pas de règles strictes à respecter.",
            "D) Toutes les affirmations ci-dessus sont fausses."
        ));
        q2.put("bonneReponse", "A");
        q2.put("explication", "Les concepts généraux enseignés s'appliquent de manière universelle dans ce domaine.");
        questions.add(q2);

        // Question 3 : Application
        Map<String, Object> q3 = new java.util.HashMap<>();
        q3.put("numero", 3);
        q3.put("question", "Quelle est la meilleure pratique recommandée lors de l'application de \"" + titre + "\" ?");
        q3.put("options", List.of(
            "A) Adopter une approche structurée, progressive et modulaire.",
            "B) Écrire tout le code dans un seul fichier volumineux sans le structurer.",
            "C) Ignorer les alertes de compilation et les conventions de nommage.",
            "D) Ne pas documenter les fonctions ni écrire de tests unitaires."
        ));
        q3.put("bonneReponse", "A");
        q3.put("explication", "La modularité et la structure progressive sont cruciales pour assurer la lisibilité et la maintenance.");
        questions.add(q3);

        // Question 4 : Piège courant
        Map<String, Object> q4 = new java.util.HashMap<>();
        q4.put("numero", 4);
        q4.put("question", "Quel piège courant doit-on absolument éviter d'après les principes de \"" + titre + "\" ?");
        q4.put("options", List.of(
            "A) La complexification excessive et le manque de clarté du code.",
            "B) La réutilisation de composants existants et éprouvés.",
            "C) L'optimisation des requêtes et l'écriture de commentaires pertinents.",
            "D) L'utilisation de types de données clairs et cohérents."
        ));
        q4.put("bonneReponse", "A");
        q4.put("explication", "Une complexité inutile nuit à la maintenabilité générale du projet.");
        questions.add(q4);

        // Question 5 : Synthèse
        Map<String, Object> q5 = new java.util.HashMap<>();
        q5.put("numero", 5);
        q5.put("question", "Pour valider durablement les acquis sur \"" + titre + "\", quel aspect est le plus important ?");
        q5.put("options", List.of(
            "A) Pratiquer régulièrement par le biais d'exercices et de projets concrets.",
            "B) Mémoriser par cœur la théorie sans jamais coder.",
            "C) Copier-coller du code sans chercher à en comprendre le fonctionnement.",
            "D) Attendre que les solutions soient entièrement générées de manière passive."
        ));
        q5.put("bonneReponse", "A");
        q5.put("explication", "La pratique active et les projets réels sont indispensables pour fixer les concepts durablement.");
        questions.add(q5);

        try {
            return objectMapper.writeValueAsString(questions);
        } catch (Exception e) {
            return "[]";
        }
    }

    private QuizGeneréDTO parseQuizExistant(Quiz quiz, Cours cours) {
        try {
            List<Map<String, Object>> questions = objectMapper.readValue(
                    quiz.getQuestionsJson(), new TypeReference<>() {});
            List<QuestionDTO> q = questions.stream()
                    .map(qm -> new QuestionDTO(
                            (Integer) qm.get("numero"),
                            (String) qm.get("question"),
                            (List<String>) qm.get("options")
                    )).toList();
            return new QuizGeneréDTO(quiz.getId(), cours.getId(), cours.getTitre(),
                    cours.getPhase().getTitre(), questions.size(), q);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lecture quiz existant", e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. SOUMETTRE LES RÉPONSES → IA évalue → score + points phase
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public ResultatQuizDTO soumettreReponses(Long quizId, SoumettreQuizRequest request) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Quiz non trouvé: " + quizId));
        Etudiant etudiant = etudiantRepository.findById(request.etudiantId())
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé: " + request.etudiantId()));

        if (!Boolean.TRUE.equals(quiz.getEstGenere()))
            throw new RuntimeException("Ce quiz n'a pas encore été généré.");

        Cours cours = quiz.getCours();
        Phase phase = cours.getPhase();

        // Charger les questions avec les bonnes réponses depuis le JSON stocké
        List<Map<String, Object>> questionsAvecReponses;
        try {
            questionsAvecReponses = objectMapper.readValue(
                    quiz.getQuestionsJson(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Erreur lecture questions du quiz", e);
        }

        // ── Correction locale (sans IA pour la vitesse) ──────────────────
        List<CorrectionDTO> corrections = new ArrayList<>();
        int bonnesReponses = 0;

        for (Map<String, Object> q : questionsAvecReponses) {
            int num = (Integer) q.get("numero");
            String bonneReponse = (String) q.get("bonneReponse");
            String explication  = (String) q.get("explication");
            String questionTxt  = (String) q.get("question");

            // Trouver la réponse de l'étudiant pour cette question
            String reponseEtudiant = request.reponses().stream()
                    .filter(r -> r.numeroQuestion() == num)
                    .map(r -> r.reponseChoisie().toUpperCase().trim())
                    .findFirst().orElse("");

            boolean correcte = bonneReponse.toUpperCase().trim().equals(reponseEtudiant);
            if (correcte) bonnesReponses++;

            // Stocker la réponse en BDD
            ReponseEtudiant reponseEntity = ReponseEtudiant.builder()
                    .numeroQuestion(num)
                    .questionTexte(questionTxt)
                    .reponseEtudiant(reponseEtudiant)
                    .bonneReponse(bonneReponse)
                    .estCorrecte(correcte)
                    .explicationIA(explication)
                    .quiz(quiz)
                    .etudiant(etudiant)
                    .build();
            reponseEtudiantRepository.save(reponseEntity);

            corrections.add(new CorrectionDTO(
                    num, questionTxt, reponseEtudiant, bonneReponse, correcte, explication
            ));
        }

        // ── Score et pourcentage ─────────────────────────────────────────
        int scoreMax = questionsAvecReponses.size();
        double pourcentage = (double) bonnesReponses / scoreMax * 100.0;
        boolean quizReussi = pourcentage >= 70.0;

        // Points obtenus sur ce quiz (proportionnel à POINTS_PAR_QUIZ)
        double pointsQuiz = (bonnesReponses / (double) scoreMax) * POINTS_PAR_QUIZ;

        // ── Feedback IA global ───────────────────────────────────────────
        String feedbackGlobal = genererFeedbackGlobal(
                cours.getTitre(), bonnesReponses, scoreMax, corrections);

        // ── Mettre à jour le Quiz ────────────────────────────────────────
        quiz.setScoreObtenu(bonnesReponses);
        quiz.setScoreMax(scoreMax);
        quiz.setPourcentage(pourcentage);
        quiz.setEstReussi(quizReussi);
        quiz.setDatePassage(LocalDateTime.now());
        quiz.setFeedbackIA(feedbackGlobal);
        quizRepository.save(quiz);

        // ── Mettre à jour noteCours sur le Cours ─────────────────────────
        cours.setNoteCours(pourcentage);
        coursRepository.save(cours);

        // ── Mettre à jour les points de la Phase ─────────────────────────
        List<Cours> coursDeLaPhase = coursRepository.findByPhaseIdOrderById(phase.getId());
        double nouveauxPoints = 0.0;
        
        for (Cours c : coursDeLaPhase) {
            Quiz q = quizRepository.findByCoursId(c.getId()).orElse(null);
            if (q != null && q.getScoreObtenu() != null && q.getScoreMax() != null && q.getScoreMax() > 0) {
                nouveauxPoints += ((double) q.getScoreObtenu() / q.getScoreMax()) * POINTS_PAR_QUIZ;
            }
        }
        
        phase.setPointsObtenus(nouveauxPoints);

        double pointsMax = phase.getPointsMax() != null ? phase.getPointsMax() : (coursDeLaPhase.size() * POINTS_PAR_QUIZ);
        double pourcentagePhase = pointsMax > 0 ? (nouveauxPoints / pointsMax * 100.0) : 0.0;
        phase.setNotePhase(pourcentagePhase); 
        
        boolean phaseDebloquee = pourcentagePhase >= 50.0;

        if (phaseDebloquee && !Boolean.TRUE.equals(phase.getEstValidee())) {
            phase.setEstValidee(true);
            log.info("Phase '{}' débloquée ! Points: {}/{} ({}%)",
                    phase.getTitre(), nouveauxPoints, pointsMax, String.format("%.1f", pourcentagePhase));
        }
        phaseRepository.save(phase);

        // ── Recalculer l'XP globale (scoreGlobal) de l'Etudiant et la sauvegarder ──
        try {
            com.hackthon.entity.RoadMap roadMap = phase.getRoadMap();
            if (roadMap != null) {
                double calculXp = 0.0;
                List<com.hackthon.entity.Phase> allPhases = phaseRepository.findByRoadMapIdOrderByOrdrePhase(roadMap.getId());
                for (com.hackthon.entity.Phase p : allPhases) {
                    List<com.hackthon.entity.Cours> coursDeP = coursRepository.findByPhaseIdOrderById(p.getId());
                    boolean allCoursesPassed = !coursDeP.isEmpty();
                    for (com.hackthon.entity.Cours c : coursDeP) {
                        double note = c.getNoteCours() != null ? c.getNoteCours() : 0.0;
                        if (note > 0) {
                            int correctQuestions = (int) Math.round((note / 100.0) * 5);
                            calculXp += correctQuestions * 10.0;
                        }
                        if (note >= 70.0) {
                            calculXp += 25.0;
                        } else {
                            allCoursesPassed = false;
                        }
                    }
                    if (allCoursesPassed) {
                        calculXp += 100.0;
                    }
                }
                etudiant.setScoreGlobal(calculXp);
                etudiantRepository.save(etudiant);
                log.info("XP globale (scoreGlobal) mise à jour pour l'étudiant id={}: {} XP", etudiant.getId(), calculXp);
            }
        } catch (Exception ex) {
            log.error("Erreur lors de la mise à jour de l'XP globale scoreGlobal", ex);
        }

        // ── Mettre à jour progression globale ────────────────────────────
        if (quizReussi) majProgressionQuizReussi(etudiant);

        // ── Évaluer et attribuer les badges ──────────────────────────────
        try {
            badgeService.evaluerEtAttribuerBadges(etudiant.getId());
        } catch (Exception ex) {
            log.error("Erreur lors de l'évaluation des badges", ex);
        }

        // ── Ajouter une Amélioration si points faibles ───────────────────
        if (!quizReussi) {
            sauvegarderAmelioration(quiz, etudiant, corrections, feedbackGlobal);
        }

        // ── Phase suivante débloquable ? ─────────────────────────────────
        Phase phaseSuivante = trouverPhaseSuivante(phase);
        boolean peutPasserSuivante = phaseDebloquee && phaseSuivante != null;
        String messageDeblocage = construireMessageDeblocage(
                phaseDebloquee, peutPasserSuivante, phaseSuivante, pourcentagePhase);

        PhaseProgressDTO phaseProgress = new PhaseProgressDTO(
                phase.getId(), phase.getTitre(),
                nouveauxPoints, pointsMax, pourcentagePhase,
                phaseDebloquee, peutPasserSuivante, messageDeblocage
        );

        log.info("Quiz id={} évalué — {}/{} ({:.1f}%) — Phase {} : {}/{} pts",
                quizId, bonnesReponses, scoreMax, pourcentage,
                phase.getTitre(), nouveauxPoints, pointsMax);

        return new ResultatQuizDTO(
                quiz.getId(), cours.getId(), cours.getTitre(),
                bonnesReponses, scoreMax, pourcentage,
                quizReussi, feedbackGlobal, corrections, phaseProgress
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVÉ — helpers
    // ══════════════════════════════════════════════════════════════════════

    private String genererFeedbackGlobal(String coursTitre, int bonnes, int total,
                                          List<CorrectionDTO> corrections) {
        long mauvaises = corrections.stream().filter(c -> !c.estCorrecte()).count();
        if (mauvaises == 0)
            return String.format("Excellent ! Tu as obtenu %d/%d sur \"%s\". Maîtrise parfaite ! 🎉", bonnes, total, coursTitre);

        String erreursDetails = corrections.stream()
                .filter(c -> !c.estCorrecte())
                .map(c -> "- " + c.question() + " → Bonne réponse : " + c.bonneReponse())
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = String.format("""
                Un étudiant a obtenu %d/%d au quiz sur "%s".
                Questions ratées :%s
                
                Donne un feedback motivant de 2-3 phrases qui :
                1. Reconnaît les efforts
                2. Identifie les points à retravailler
                3. Encourage à continuer
                Réponds directement sans JSON, en français.
                """, bonnes, total, coursTitre, erreursDetails);

        try {
            return groqService.ask("Tu es un tuteur bienveillant. Réponds en 2-3 phrases.", prompt);
        } catch (Exception e) {
            return String.format("Tu as obtenu %d/%d. Continue tes efforts sur les points non maîtrisés !", bonnes, total);
        }
    }

    private Phase trouverPhaseSuivante(Phase phase) {
        List<Phase> phases = phaseRepository.findByRoadMapIdOrderByOrdrePhase(phase.getRoadMap().getId());
        for (int i = 0; i < phases.size() - 1; i++) {
            if (phases.get(i).getId().equals(phase.getId())) return phases.get(i + 1);
        }
        return null; // dernière phase
    }

    private String construireMessageDeblocage(boolean phaseDebloquee, boolean peutPasser,
                                               Phase suivante, double pourcentage) {
        if (!phaseDebloquee)
            return String.format("Phase non débloquée (%.1f%% / 50%% requis). Retravaille les cours et repasse les quiz !", pourcentage);
        if (!peutPasser)
            return "🎉 Félicitations ! Tu as terminé la dernière phase de ta roadmap !";
        return String.format("✅ Phase débloquée (%.1f%%)! Tu peux maintenant accéder à la phase suivante : \"%s\"",
                pourcentage, suivante.getTitre());
    }

    private void majProgressionCoursTermine(Etudiant etudiant) {
        progressionRepository.findByEtudiant(etudiant).ifPresent(p -> {
            p.setCoursTermines(p.getCoursTermines() + 1);
            progressionRepository.save(p);
        });
    }

    private void majProgressionQuizReussi(Etudiant etudiant) {
        progressionRepository.findByEtudiant(etudiant).ifPresent(p -> {
            p.setQuizReussis(p.getQuizReussis() + 1);
            progressionRepository.save(p);
        });
    }

    private void sauvegarderAmelioration(Quiz quiz, Etudiant etudiant,
                                          List<CorrectionDTO> corrections, String feedback) {
        String pointsFaibles = corrections.stream()
                .filter(c -> !c.estCorrecte())
                .map(c -> "• " + c.question())
                .reduce("", (a, b) -> a + "\n" + b);

        if (pointsFaibles.isBlank()) return;

        Amelioration amelioration = Amelioration.builder()
                .pointFaible(pointsFaibles.substring(0, Math.min(pointsFaibles.length(), 900)))
                .conseilIA(feedback.substring(0, Math.min(feedback.length(), 1900)))
                .dateCreation(java.time.LocalDateTime.now())
                .quiz(quiz)
                .etudiant(etudiant)
                .build();
        ameliorationRepository.save(amelioration);
        log.info("Amélioration sauvegardée pour étudiant {} quiz {}", etudiant.getId(), quiz.getId());
    }
}