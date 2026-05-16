package com.hackthon.repository;

import com.hackthon.entity.Phase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PhaseRepository extends JpaRepository<Phase, Long> {
    List<Phase> findByRoadMapIdOrderByOrdrePhase(Long roadMapId);
    long countByRoadMapId(Long roadMapId);
}