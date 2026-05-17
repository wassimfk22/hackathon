package com.hackthon.repository;

import com.hackthon.entity.ReponseEtudiant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReponseEtudiantRepository extends JpaRepository<ReponseEtudiant, Long> {
    List<ReponseEtudiant> findByQuizIdAndEtudiantId(Long quizId, Long etudiantId);
    void deleteByQuizId(Long quizId);
}
