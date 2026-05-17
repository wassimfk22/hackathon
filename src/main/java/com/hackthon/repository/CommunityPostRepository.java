package com.hackthon.repository;

import com.hackthon.entity.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    List<CommunityPost> findAllByOrderByDateCreationDesc();
    
    long countByEtudiantIdAndDateCreationAfter(Long etudiantId, LocalDateTime since);
    
    List<CommunityPost> findByEtudiantId(Long etudiantId);
}
