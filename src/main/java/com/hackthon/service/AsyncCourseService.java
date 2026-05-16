package com.hackthon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service séparé OBLIGATOIRE pour que @Async fonctionne.
 * Spring AOP ne peut pas intercepter un appel dans la même classe.
 * Si genererTousLesCourseAsync() reste dans RoadMapService,
 * l'appel interne court-circuite le proxy → @Async ignoré → bloquant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncCourseService {

    private final CoursGenerationService coursGenerationService;

    @Async
    public void genererCoursEnArrierePlan(List<Long> phaseIds) {
        log.info(">>> Début génération async — {} phases à traiter", phaseIds.size());
        for (Long phaseId : phaseIds) {
            try {
                coursGenerationService.genererTousLesCoursDePhase(phaseId);
                // Pause entre phases pour ne pas saturer l'API Groq (429)
                Thread.sleep(800);
            } catch (InterruptedException e) {
                log.warn("Génération interrompue à la phase {}", phaseId);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Erreur phase {} : {} — on continue", phaseId, e.getMessage());
            }
        }
        log.info("<<< Génération async terminée pour toutes les phases");
    }
    
    
    
}