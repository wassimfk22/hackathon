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

    // 10 points par quiz
    private static final int POINTS_PAR_QUIZ = 10;

    private static final String SYSTEM_QUIZ = """
            Tu es un expert pédagogique. Tu génères des quiz QCM précis basés UNIQUEMENT sur le contenu fourni.
            Réponds UNIQUEMENT en JSON valide, sans markdown, sans texte autour.
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

        // Si quiz déjà généré pour cet étudiant → le retourner directement
        return quizRepository.findByCoursIdAndEtudiantId(coursId, etudiantId)
                .filter(q -> Boolean.TRUE.equals(q.getEstGenere()))
                .map(q -> parseQuizExistant(q, cours))
                .orElseGet(() -> genererNouveauQuiz(cours, etudiant));
    }

    private QuizGeneréDTO genererNouveauQuiz(Cours cours, Etudiant etudiant) {
        Phase phase = cours.getPhase();

        String prompt = String.format("""
                Génère un quiz de 5 questions QCM basé EXCLUSIVEMENT sur ce contenu de cours.
                
                COURS : %s
                CONTENU :
                %s
                
                RÈGLES STRICTES :
                - 5 questions exactement
                - Chaque question a 4 options : A, B, C, D
                - Les questions doivent tester la compréhension réelle du cours
                - Ne pose PAS de questions hors du contenu du cours
                
                Réponds UNIQUEMENT avec ce JSON (sans markdown) :
                {
                  "questions": [
                    {
                      "numero": 1,
                      "question": "Texte de la question ?",
                      "options": ["A) ...", "B) ...", "C) ...", "D) ..."],
                      "bonneReponse": "A",
                      "explication": "Explication courte de la bonne réponse"
                    }
                  ]
                }
                """, cours.getTitre(), cours.getContenu());

        log.info("Génération quiz pour cours '{}' étudiant {}", cours.getTitre(), etudiant.getId());
        String raw = groqService.ask(SYSTEM_QUIZ, prompt);

        try {
            String clean = raw.trim().replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();
            Map<String, Object> parsed = objectMapper.readValue(clean, new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questionsRaw = (List<Map<String, Object>>) parsed.get("questions");

            // Stocker les questions en JSON dans le Quiz (avec bonnes réponses)
            String questionsJson = objectMapper.writeValueAsString(questionsRaw);

            // Calculer les pointsMax de la phase (POINTS_PAR_QUIZ par cours)
            long nbCours = coursRepository.findByPhaseIdOrderById(phase.getId()).size();
            double pointsMaxPhase = nbCours * POINTS_PAR_QUIZ;
            phase.setPointsMax(pointsMaxPhase);
            if (phase.getPointsObtenus() == null) phase.setPointsObtenus(0.0);
            phaseRepository.save(phase);

            // Créer le Quiz en BDD
            Quiz quiz = Quiz.builder()
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
            quiz = quizRepository.save(quiz);

            // Mettre à jour la progression (cours terminé)
            majProgressionCoursTermine(etudiant);

            log.info("Quiz id={} généré ({} questions) pour cours '{}'",
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
            log.error("Erreur parsing quiz: {}", raw, e);
            throw new RuntimeException("Erreur génération quiz: " + e.getMessage(), e);
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
        boolean quizReussi = pourcentage >= 50.0;

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
            Quiz q = quizRepository.findByCoursIdAndEtudiantId(c.getId(), etudiant.getId()).orElse(null);
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

        // ── Mettre à jour progression globale ────────────────────────────
        if (quizReussi) majProgressionQuizReussi(etudiant);

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