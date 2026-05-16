package com.hackthon.repository;

import com.hackthon.entity.Amelioration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AmeliorationRepository extends JpaRepository<Amelioration, Long> {
    List<Amelioration> findByEtudiantId(Long etudiantId);
}
