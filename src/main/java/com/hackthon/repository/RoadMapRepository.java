package com.hackthon.repository;

import com.hackthon.entity.RoadMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoadMapRepository extends JpaRepository<RoadMap, Long> {
    Optional<RoadMap> findTopByEtudiantIdOrderByDateCreationDesc(Long etudiantId);
}