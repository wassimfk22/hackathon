package com.hackthon.repository;

import com.hackthon.entity.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {
    long countByEtudiantId(Long etudiantId);
}
