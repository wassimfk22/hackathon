package com.hackthon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.hackthon.dto.ChatResponse;
import com.hackthon.dto.CoursResume;
import com.hackthon.dto.PhaseDetailDTO;
import com.hackthon.dto.ProgressionDTO;
import com.hackthon.dto.RoadMapFullDTO;
import com.hackthon.entity.*;
import com.hackthon.enums.Niveau;
import com.hackthon.enums.StatutRoadMap;
import com.hackthon.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadMapService {

    private final RoadMapRepository roadMapRepository;
    private final PhaseRepository phaseRepository;
    private final EtudiantRepository etudiantRepository;
    private final DomaineRepository domaineRepository;
    private final ProgressionRepository progressionRepository;
    private final CoursRepository coursRepository;
    private final AsyncCourseService asyncCourseService;
    private final GroqService groqService;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Etudiant creerEtudiantAvecIdForce(Long etudiantId) {
        log.info("Insertion SQL forcée de l'étudiant {}...", etudiantId);
        String sql = "INSERT INTO etudiants (id, nom, prenom, email, mot_de_passe, date_inscription, score_global) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        entityManager.createNativeQuery(sql)
                .setParameter(1, etudiantId)
                .setParameter(2, "Hackathon")
                .setParameter(3, "Étudiant " + etudiantId)
                .setParameter(4, "etudiant" + etudiantId + "@example.com")
                .setParameter(5, "123456")
                .setParameter(6, java.sql.Date.valueOf(LocalDate.now()))
                .setParameter(7, 0.0)
                .executeUpdate();
        
        entityManager.flush();
        return etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Échec création étudiant forcé"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // ENREGISTREMENT DEPUIS LE CHAT
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public RoadMap enregistrerRoadMapIA(Long etudiantId, String niveauStr,
                                        List<ChatResponse.PhaseDTO> phasesIA) {
        log.info("enregistrerRoadMapIA() etudiantId={} niveau={} phases={}",
                etudiantId, niveauStr, phasesIA.size());

        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé: " + etudiantId));

        Domaine domaine = etudiant.getDomaine();
        if (domaine == null) {
            domaine = domaineRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("Aucun domaine en BDD"));
            log.warn("Fallback domaine id={} pour étudiant {}", domaine.getId(), etudiantId);
        }

        Niveau niveau = parseNiveau(niveauStr);
        etudiant.setNiveau(niveau);
        etudiantRepository.save(etudiant);

        // Marquer l'ancienne EN_COURS → TERMINEE (pas de delete = pas de risque rollback)
        roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiantId)
                .filter(old -> old.getStatut() == StatutRoadMap.EN_COURS)
                .ifPresent(old -> {
                    old.setStatut(StatutRoadMap.TERMINEE);
                    roadMapRepository.save(old);
                    log.info("Ancienne roadmap {} → TERMINEE", old.getId());
                });

        // Créer la RoadMap
        String titre = "Roadmap " + domaine.getNom() + " — " + niveau.name();
        RoadMap roadMap = roadMapRepository.save(RoadMap.builder()
                .titre(titre)
                .dateCreation(LocalDate.now())
                .statut(StatutRoadMap.EN_COURS)
                .etudiant(etudiant)
                .domaine(domaine)
                .phases(new ArrayList<>())
                .build());

        log.info("RoadMap créée id={} '{}'", roadMap.getId(), titre);

        // Créer phases + cours (contenu vide)
        List<Long> phaseIds = new ArrayList<>();
        for (ChatResponse.PhaseDTO phaseDTO : phasesIA) {
            Phase phase = phaseRepository.save(Phase.builder()
                    .titre(phaseDTO.titre())
                    .ordrePhase(phaseDTO.ordre())
                    .notePhase(0.0)
                    .estValidee(false)
                    .roadMap(roadMap)
                    .cours(new ArrayList<>())
                    .build());
            phaseIds.add(phase.getId());

            for (String coursTitre : phaseDTO.cours()) {
                coursRepository.save(Cours.builder()
                        .titre(coursTitre)
                        .contenu("")
                        .phase(phase)
                        .noteCours(0.0)
                        .build());
            }
            log.info("  Phase '{}' — {} cours", phaseDTO.titre(), phaseDTO.cours().size());
        }

        // Reset progression
        Progression progression = progressionRepository.findByEtudiant(etudiant)
                .orElse(Progression.builder().etudiant(etudiant).build());
        progression.setProgressionGlobale(0.0);
        progression.setPhasesTerminees(0);
        progression.setCoursTermines(0);
        progression.setQuizReussis(0);
        progressionRepository.save(progression);

        log.info("RoadMap id={} enregistrée — lancement génération async", roadMap.getId());
        asyncCourseService.genererCoursEnArrierePlan(phaseIds);

        return roadMap;
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONSULTATION — DTO COMPLET
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Retourne la roadmap COMPLÈTE avec toutes les relations chargées en DTO.
     * Plus de null : étudiant, domaine, phases, cours — tout est là.
     */
    @Transactional
    public RoadMapFullDTO getRoadMapFull(Long etudiantId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId).orElse(null);
        if (etudiant == null) {
            log.info("Étudiant {} non trouvé. Création de l'étudiant en BDD...", etudiantId);
            etudiant = creerEtudiantAvecIdForce(etudiantId);
        }

        RoadMap roadMap = roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiant.getId())
                .orElse(null);

        if (roadMap == null) {
            log.info("Aucune roadmap pour l'étudiant {}. Il doit d'abord passer l'onboarding et le quiz.", etudiant.getId());
            return null;
        }

        Etudiant e = roadMap.getEtudiant();
        Domaine d  = roadMap.getDomaine();

        // ── Étudiant DTO
        RoadMapFullDTO.EtudiantDTO etudiantDTO = e == null ? null : new RoadMapFullDTO.EtudiantDTO(
                e.getId(), e.getNom(), e.getPrenom(), e.getEmail(),
                e.getNiveau(), e.getScoreGlobal()
        );

        // ── Domaine DTO
        RoadMapFullDTO.DomaineDTO domaineDTO = d == null ? null : new RoadMapFullDTO.DomaineDTO(
                d.getId(), d.getNom(), d.getDescription()
        );

        // ── Phases + Cours
        List<Phase> phases = phaseRepository.findByRoadMapIdOrderByOrdrePhase(roadMap.getId());

        List<RoadMapFullDTO.PhaseFullDTO> phasesDTO = phases.stream().map(phase -> {
            List<Cours> coursList = coursRepository.findByPhaseIdOrderById(phase.getId());

            List<RoadMapFullDTO.CoursDTO> coursDTO = coursList.stream().map(cours ->
                    new RoadMapFullDTO.CoursDTO(
                            cours.getId(),
                            cours.getTitre(),
                            cours.getContenu(),
                            cours.getTypeContenu(),
                            cours.getDateGeneration(),
                            cours.getNoteCours() != null ? cours.getNoteCours() : 0.0,
                            cours.getContenu() != null && !cours.getContenu().isBlank()
                    )
            ).toList();

            return new RoadMapFullDTO.PhaseFullDTO(
                    phase.getId(),
                    phase.getTitre(),
                    phase.getOrdrePhase() != null ? phase.getOrdrePhase() : 0,
                    phase.getNotePhase() != null ? phase.getNotePhase() : 0.0,
                    Boolean.TRUE.equals(phase.getEstValidee()),
                    coursDTO
            );
        }).toList();

        // ── Métriques
        int totalCours    = phasesDTO.stream().mapToInt(p -> p.cours().size()).sum();
        int phasesValidees = (int) phasesDTO.stream().filter(RoadMapFullDTO.PhaseFullDTO::estValidee).count();

        Progression progression = progressionRepository.findByEtudiant(roadMap.getEtudiant()).orElse(null);
        double progressionGlobale = progression != null ? progression.getProgressionGlobale() : 0.0;

        return new RoadMapFullDTO(
                roadMap.getId(),
                roadMap.getTitre(),
                roadMap.getDateCreation(),
                roadMap.getStatut(),
                etudiantDTO,
                domaineDTO,
                phasesDTO,
                progressionGlobale,
                phases.size(),
                phasesValidees,
                totalCours
        );
    }

    // ── Ancienne méthode conservée pour compatibilité
    @Transactional
    public RoadMap getRoadMapByEtudiant(Long etudiantId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId).orElse(null);
        if (etudiant == null) {
            etudiant = creerEtudiantAvecIdForce(etudiantId);
        }
        return roadMapRepository.findTopByEtudiantIdOrderByDateCreationDesc(etudiant.getId())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PhaseDetailDTO> getPhasesByRoadMap(Long roadMapId) {
        return phaseRepository.findByRoadMapIdOrderByOrdrePhase(roadMapId).stream()
                .map(phase -> {
                    List<CoursResume> coursResumes = coursRepository.findByPhaseIdOrderById(phase.getId()).stream()
                            .map(cours -> new CoursResume(
                                    cours.getId(),
                                    cours.getTitre(),
                                    cours.getNoteCours() != null ? cours.getNoteCours() : 0.0,
                                    cours.getContenu() != null && !cours.getContenu().isBlank()
                            ))
                            .toList();

                    return new PhaseDetailDTO(
                            phase.getId(),
                            phase.getTitre(),
                            phase.getOrdrePhase() != null ? phase.getOrdrePhase() : 0,
                            phase.getNotePhase() != null ? phase.getNotePhase() : 0.0,
                            Boolean.TRUE.equals(phase.getEstValidee()),
                            coursResumes
                    );
                })
                .toList();
    }

    @Transactional
    public ProgressionDTO getProgression(Long roadMapId) {
        RoadMap roadMap = roadMapRepository.findById(roadMapId)
                .orElseThrow(() -> new RuntimeException("RoadMap non trouvée: " + roadMapId));

        long totalCours  = coursRepository.countByPhase_RoadMap(roadMap);
        long totalPhases = phaseRepository.countByRoadMapId(roadMapId);

        Progression p = progressionRepository.findByEtudiant(roadMap.getEtudiant())
                .orElse(Progression.builder().coursTermines(0).quizReussis(0).phasesTerminees(0).build());

        double taux = 0.0;
        if (totalCours > 0)
            taux = Math.min(((p.getCoursTermines() * 0.5) + (p.getQuizReussis() * 0.5)) / totalCours * 100.0, 100.0);

        p.setProgressionGlobale(taux);
        progressionRepository.save(p);

        return new ProgressionDTO(taux, p.getCoursTermines(), (int) totalCours,
                p.getQuizReussis(), p.getPhasesTerminees(), (int) totalPhases);
    }

    @Transactional
    public double getNotePhase(Long phaseId) {
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new RuntimeException("Phase non trouvée: " + phaseId));

        double moyenne = coursRepository.findByPhaseId(phaseId).stream()
                .filter(c -> c.getNoteCours() != null)
                .mapToDouble(Cours::getNoteCours)
                .average().orElse(0.0);

        phase.setNotePhase(moyenne);
        if (moyenne >= 60.0) phase.setEstValidee(true);
        phaseRepository.save(phase);
        return moyenne;
    }

    // ══════════════════════════════════════════════════════════════════════
    // UTILITAIRE
    // ══════════════════════════════════════════════════════════════════════

    private Niveau parseNiveau(String s) {
        if (s == null) return Niveau.DEBUTANT;
        String clean = s.toUpperCase().trim()
                .replace("É", "E").replace("Ê", "E")
                .replace("Â", "A").replace("È", "E");
        return switch (clean) {
            case "INTERMEDIAIRE", "INTERMEDIATE" -> Niveau.INTERMEDIAIRE;
            case "AVANCE", "ADVANCED"            -> Niveau.AVANCE;
            case "EXPERT"                        -> Niveau.EXPERT;
            default                              -> Niveau.DEBUTANT;
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // AUTO-GÉNÉRATION ET FALLBACK
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    public RoadMap genererEtEnregistrerRoadMapPourEtudiant(Long etudiantId) {
        log.info("Génération automatique d'une roadmap pour l'étudiant {}...", etudiantId);
        
        Etudiant etudiant = etudiantRepository.findById(etudiantId).orElse(null);
        if (etudiant == null) {
            log.warn("⚠️ Étudiant {} non trouvé en BDD. Tentative de récupération du premier étudiant existant...", etudiantId);
            etudiant = etudiantRepository.findAll().stream().findFirst().orElse(null);
            
            if (etudiant == null) {
                log.info("Aucun étudiant en BDD. Création d'un étudiant par défaut...");
                etudiant = Etudiant.builder()
                        .nom("Hackathon")
                        .prenom("Étudiant")
                        .email("etudiant" + etudiantId + "@example.com")
                        .motDePasse("123456")
                        .dateInscription(LocalDate.now())
                        .scoreGlobal(0.0)
                        .build();
                etudiant = etudiantRepository.save(etudiant);
            }
        }
        
        Domaine domaine = etudiant.getDomaine();
        if (domaine == null) {
            domaine = domaineRepository.findAll().stream().findFirst().orElse(null);
            if (domaine != null) {
                etudiant.setDomaine(domaine);
                etudiant = etudiantRepository.save(etudiant);
                log.info("Domaine par défaut '{}' associé à l'étudiant {}", domaine.getNom(), etudiant.getId());
            }
        }
        
        Niveau niveau = etudiant.getNiveau();
        if (niveau == null) {
            niveau = Niveau.DEBUTANT;
            etudiant.setNiveau(niveau);
            etudiant = etudiantRepository.save(etudiant);
            log.info("Niveau par défaut DEBUTANT associé à l'étudiant {}", etudiant.getId());
        }
        
        String systemPrompt = """
            Tu es un Tuteur IA spécialisé en Software Engineering et Développement IT.
            Génère une roadmap d'apprentissage structurée de 4 phases, adaptée au niveau et au domaine de l'étudiant.
            Chaque phase doit comporter entre 2 et 3 cours (titres simples).
            
            Réponds UNIQUEMENT avec ce format JSON strict (sans markdown, sans aucun texte avant ou après) :
            [
              {"ordre": 1, "titre": "Phase 1 : ...", "cours": ["Titre Cours A", "Titre Cours B"]},
              {"ordre": 2, "titre": "Phase 2 : ...", "cours": ["Titre Cours A", "Titre Cours B", "Titre Cours C"]},
              {"ordre": 3, "titre": "Phase 3 : ...", "cours": ["Titre Cours A", "Titre Cours B"]},
              {"ordre": 4, "titre": "Phase 4 : ...", "cours": ["Titre Cours A", "Titre Cours B"]}
            ]
            """;
        
        String userMessage = String.format("Génère une roadmap d'apprentissage pour le domaine \"%s\" et le niveau \"%s\".", domaine.getNom(), niveau.name());
        
        List<ChatResponse.PhaseDTO> phases = null;
        try {
            log.info("Appel Groq pour générer la roadmap...");
            String raw = groqService.ask(systemPrompt, userMessage);
            String clean = raw.trim();
            int start = clean.indexOf("[");
            int end = clean.lastIndexOf("]");
            if (start != -1 && end != -1 && end > start) {
                clean = clean.substring(start, end + 1);
            }
            
            phases = objectMapper.readValue(clean, new TypeReference<List<ChatResponse.PhaseDTO>>() {});
            log.info("✅ Roadmap générée avec succès via l'IA.");
        } catch (Exception e) {
            log.warn("⚠️ Impossible de générer la roadmap via l'IA ({}), utilisation des templates par défaut.", e.getMessage());
        }
        
        String domaineNom = domaine != null ? domaine.getNom() : "Java";
        if (phases == null || phases.isEmpty()) {
            phases = genererPhasesParDefaut(domaineNom, niveau);
            log.info("✅ Utilisation du template de roadmap statique pour le domaine {}", domaineNom);
        }
        
        return enregistrerRoadMapIA(etudiant.getId(), niveau.name(), phases);
    }

    private List<ChatResponse.PhaseDTO> genererPhasesParDefaut(String domaineNom, Niveau niveau) {
        List<ChatResponse.PhaseDTO> phases = new ArrayList<>();
        String d = domaineNom != null ? domaineNom.toLowerCase() : "";

        if (d.contains("java")) {
            if (niveau == Niveau.DEBUTANT) {
                phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Bases de Java", List.of("Variables et Conditions en Java", "Boucles et Fonctions en Java")));
                phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Programmation Orientée Objet", List.of("Classes et Objets", "Héritage et Polymorphisme")));
                phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Concepts Avancés", List.of("Gestion des Exceptions en Java", "Collections et Generics")));
                phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Projet Pratique", List.of("Développement d'une application Console")));
            } else if (niveau == Niveau.INTERMEDIAIRE) {
                phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Java Moderne & Streams", List.of("Expressions Lambda et Optionals", "Stream API et manipulation des collections")));
                phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Concurrence & Threads", List.of("Programmation multithreadée", "Executors et Synchronisation")));
                phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Accès aux Données", List.of("Concepts JDBC et introduction à JPA/Hibernate")));
                phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Projet API REST", List.of("Création d'une API REST avec Spring Boot")));
            } else {
                phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Architecture Spring Boot", List.of("Spring Security et JWT", "Spring AOP et Transactions")));
                phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Design Patterns en Java", List.of("Patterns de Création et Structure", "Patterns de Comportement")));
                phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : JVM et Optimisation", List.of("Gestion de la mémoire et Garbage Collection", "Profiling et Tuning de performance")));
                phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Projet Microservices", List.of("Architecture Microservices", "Orchestration avec Docker et Eureka")));
            }
        } else if (d.contains("python")) {
            if (niveau == Niveau.DEBUTANT) {
                phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Syntaxe et Bases de Python", List.of("Variables et Types de Données", "Structures de contrôle et Boucles")));
                phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Structures de Données", List.of("Listes, Dictionnaires et Tuples", "Fonctions et Modules")));
                phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Fichiers et Exceptions", List.of("Lecture/Écriture de fichiers", "Gestion des exceptions en Python")));
                phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Projet Pratique", List.of("Mini projet d'automatisation")));
            } else if (niveau == Niveau.INTERMEDIAIRE) {
                phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Programmation Orientée Objet", List.of("Classes, Méthodes et Attributs", "Héritage et Surcharge")));
                phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Outils et Bibliothèques", List.of("Expression régulières (regex)", "Manipulation de fichiers CSV et JSON")));
                phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Introduction à la Data", List.of("Base de NumPy et Pandas", "Visualisation avec Matplotlib")));
                phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Projet Web Scraping", List.of("Scraping de sites avec BeautifulSoup")));
            } else {
                phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Concepts Python Avancés", List.of("Décorateurs et Générateurs", "Gestionnaires de contexte")));
                phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Concurrence et Async", List.of("Programmation asynchrone avec Asyncio", "Multiprocessing en Python")));
                phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Clean Code & Tests", List.of("Tests unitaires avec Pytest", "Bonnes pratiques PEP 8 et Typage")));
                phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Projet API avec FastAPI", List.of("Création d'une API performante avec FastAPI")));
            }
        } else if (d.contains("git")) {
            phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Introduction à Git", List.of("Concepts de contrôle de version", "Configuration et initialisation de dépôt")));
            phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Gestion des Commits", List.of("Staging Area et Commits", "Historique et retours en arrière (Reset/Revert)")));
            phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Branches et Fusion", List.of("Création et fusion de branches", "Résolution de conflits de fusion")));
            phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Collaboration & GitHub", List.of("Dépôts distants (Push/Pull)", "Pull Requests et Workflow collaboratif")));
        } else if (d.contains("artificielle") || d.contains("ia") || d.contains("intelligence")) {
            if (niveau == Niveau.DEBUTANT) {
                phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Introduction à l'IA", List.of("Concepts généraux de l'IA", "Différence entre IA, Machine Learning et Deep Learning")));
                phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Fondations de ML", List.of("Algorithmes de Régression", "Algorithmes de Classification")));
                phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Préparation des Données", List.of("Nettoyage et standardisation", "Sélection des caractéristiques (Feature Engineering)")));
                phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Projet ML Simple", List.of("Entraînement d'un modèle avec Scikit-Learn")));
            } else if (niveau == Niveau.INTERMEDIAIRE) {
                phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Réseaux de Neurones", List.of("Perceptron et Fonctions d'activation", "Réseaux de neurones artificiels (ANN)")));
                phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Deep Learning pour l'Image", List.of("Réseaux de neurones convolutifs (CNN)", "Traitement d'images de base")));
                phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Traitement du Langage (NLP)", List.of("Représentation de mots (Word Embeddings)", "Réseaux récurrents (RNN et LSTM)")));
                phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Projet Deep Learning", List.of("Entraînement d'un modèle d'image avec TensorFlow/PyTorch")));
            } else {
                phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Architectures Transformers", List.of("Mécanisme d'attention et Transformers", "Architecture GPT et BERT")));
                phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Modèles de Langage (LLMs)", List.of("Fine-tuning de modèles pré-entraînés", "Techniques d'ingénierie de prompt")));
                phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Déploiement d'IA", List.of("Mise en production de modèles", "Optimisation et quantification de modèles")));
                phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Projet LLM et RAG", List.of("Création d'un système de questions-réponses RAG")));
            }
        } else if (d.contains("devops") || d.contains("déploiement")) {
            phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Concepts DevOps & Linux", List.of("Culture DevOps et automatisation", "Administration système Linux de base")));
            phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Conteneurisation avec Docker", List.of("Création d'images et Dockerfile", "Orchestration multi-conteneurs avec Docker Compose")));
            phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Intégration Continue (CI/CD)", List.of("Pipelines CI/CD (GitHub Actions / GitLab CI)", "Tests automatisés et qualité de code")));
            phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Déploiement Cloud", List.of("Déploiement sur AWS/GCP", "Supervision et monitoring")));
        } else if (d.contains("conception") || d.contains("architecture")) {
            phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Modélisation UML", List.of("Diagrammes de classes et de cas d'utilisation", "Diagrammes de séquence et d'activités")));
            phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Design Patterns", List.of("Patterns de création (Factory, Builder)", "Patterns structurels et de comportement")));
            phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Principes SOLID", List.of("Principes de responsabilité et d'extension", "Inversion de dépendance et interfaces")));
            phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Architecture Logicielle", List.of("Architecture hexagonale / Clean Architecture", "Conception orientée domaine (DDD)")));
        } else {
            // Fallback universel ultra propre si le domaine est inconnu
            phases.add(new ChatResponse.PhaseDTO(1, "Phase 1 : Fondations du domaine " + domaineNom, List.of("Introduction et concepts généraux", "Outils de développement indispensables")));
            phases.add(new ChatResponse.PhaseDTO(2, "Phase 2 : Pratiques clés de " + domaineNom, List.of("Syntaxe, commandes ou méthodes de base", "Gestion des cas d'erreurs récurrents")));
            phases.add(new ChatResponse.PhaseDTO(3, "Phase 3 : Approfondissement thématique", List.of("Utilisation avancée et patterns courants", "Optimisation et bonnes pratiques de codage")));
            phases.add(new ChatResponse.PhaseDTO(4, "Phase 4 : Projet final d'application", List.of("Mise en pratique sur un cas réel complet")));
        }

        return phases;
    }
}