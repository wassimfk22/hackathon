package com.hackthon.service;

import com.hackthon.entity.Domaine;
import com.hackthon.repository.DomaineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final DomaineRepository domaineRepository;

    @Override
    public void run(String... args) throws Exception {
        if (domaineRepository.count() == 0) {
            log.info("Initialisation des domaines par défaut en BDD...");
            
            List<Domaine> domaines = List.of(
                    Domaine.builder().nom("Java").description("Programmation orientée objet avec Java").build(),
                    Domaine.builder().nom("Python").description("Développement d'applications et Data Science avec Python").build(),
                    Domaine.builder().nom("Git").description("Gestion de version et collaboration avec Git").build(),
                    Domaine.builder().nom("Intelligence Artificielle").description("Apprentissage automatique, réseaux de neurones et LLMs").build(),
                    Domaine.builder().nom("DevOps").description("Déploiement, intégration continue et infrastructures").build(),
                    Domaine.builder().nom("Déploiement").description("Déploiement d'applications et orchestration").build(),
                    Domaine.builder().nom("Conception").description("Architecture et conception logicielle").build()
            );
            
            domaineRepository.saveAll(domaines);
            log.info("Domaines initialisés avec succès !");
        }
    }
}
