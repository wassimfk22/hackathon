package com.hackthon.repository;

import com.hackthon.entity.Cours;
import com.hackthon.entity.RoadMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoursRepository extends JpaRepository<Cours, Long> {
    List<Cours> findByPhaseIdOrderById(Long phaseId);
    List<Cours> findByPhaseId(Long phaseId);
    long countByPhase_RoadMap(RoadMap roadMap);
}