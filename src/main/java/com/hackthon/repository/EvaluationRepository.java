package com.hackthon.repository;

import com.hackthon.entity.EvaluationIA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationRepository extends JpaRepository<EvaluationIA, Long> {
}
