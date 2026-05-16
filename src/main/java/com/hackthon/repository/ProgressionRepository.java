package com.hackthon.repository;

import com.hackthon.entity.Etudiant;
import com.hackthon.entity.Progression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProgressionRepository extends JpaRepository<Progression, Long> {
    Optional<Progression> findByEtudiant(Etudiant etudiant);
    Optional<Progression> findByEtudiantId(Long etudiantId);
}