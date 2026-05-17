package com.hackthon.service;

import com.hackthon.dto.*;
import com.hackthon.entity.*;
import com.hackthon.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityPostService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final EtudiantRepository etudiantRepository;

    @Transactional(readOnly = true)
    public List<CommunityPostDTO> getAllPosts(Long currentEtudiantId) {
        return communityPostRepository.findAllByOrderByDateCreationDesc()
                .stream()
                .map(post -> convertToPostDTO(post, currentEtudiantId))
                .collect(Collectors.toList());
    }

    @Transactional
    public CommunityPostDTO createPost(CommunityPostRequest request) {
        Etudiant etudiant = etudiantRepository.findById(request.etudiantId())
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        // Limit to 3 posts per day
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        long todayPostsCount = communityPostRepository.countByEtudiantIdAndDateCreationAfter(etudiant.getId(), startOfDay);
        if (todayPostsCount >= 3) {
            throw new RuntimeException("Limite de 3 publications par jour atteinte !");
        }

        CommunityPost post = CommunityPost.builder()
                .titre(request.titre())
                .contenu(request.contenu())
                .dateCreation(LocalDateTime.now())
                .etudiant(etudiant)
                .build();

        post = communityPostRepository.save(post);

        // Earn 10 XP for posting
        etudiant.setScoreGlobal((etudiant.getScoreGlobal() != null ? etudiant.getScoreGlobal() : 0.0) + 10.0);
        etudiantRepository.save(etudiant);

        return convertToPostDTO(post, etudiant.getId());
    }

    @Transactional
    public CommunityPostDTO toggleLikePost(Long postId, Long currentEtudiantId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Publication non trouvée"));
        Etudiant currentEtudiant = etudiantRepository.findById(currentEtudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        Etudiant publisher = post.getEtudiant();

        boolean alreadyLiked = post.getLikedBy().stream().anyMatch(e -> e.getId().equals(currentEtudiantId));

        if (alreadyLiked) {
            // Unlike post
            post.getLikedBy().removeIf(e -> e.getId().equals(currentEtudiantId));
            if (publisher != null) {
                publisher.setScoreGlobal(Math.max(0.0, (publisher.getScoreGlobal() != null ? publisher.getScoreGlobal() : 0.0) - 10.0));
                etudiantRepository.save(publisher);
            }
        } else {
            // Like post
            post.getLikedBy().add(currentEtudiant);
            if (publisher != null) {
                publisher.setScoreGlobal((publisher.getScoreGlobal() != null ? publisher.getScoreGlobal() : 0.0) + 10.0);
                etudiantRepository.save(publisher);
            }
        }

        post = communityPostRepository.save(post);
        return convertToPostDTO(post, currentEtudiantId);
    }

    @Transactional
    public CommunityPostDTO addComment(Long postId, CommunityCommentRequest request) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Publication non trouvée"));
        Etudiant commenter = etudiantRepository.findById(request.etudiantId())
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));

        CommunityComment comment = CommunityComment.builder()
                .contenu(request.contenu())
                .dateCreation(LocalDateTime.now())
                .etudiant(commenter)
                .post(post)
                .build();

        communityCommentRepository.save(comment);

        // Replying gives 10 XP to commenter
        commenter.setScoreGlobal((commenter.getScoreGlobal() != null ? commenter.getScoreGlobal() : 0.0) + 10.0);
        etudiantRepository.save(commenter);

        // Refresh post to get new comment
        post = communityPostRepository.findById(postId).orElse(post);

        return convertToPostDTO(post, commenter.getId());
    }

    private CommunityPostDTO convertToPostDTO(CommunityPost post, Long currentEtudiantId) {
        boolean likedByMe = post.getLikedBy().stream().anyMatch(e -> e.getId().equals(currentEtudiantId));
        
        List<CommunityCommentDTO> commentDTOs = post.getComments().stream()
                .map(this::convertToCommentDTO)
                .collect(Collectors.toList());

        return new CommunityPostDTO(
                post.getId(),
                post.getTitre(),
                post.getContenu(),
                post.getDateCreation(),
                post.getEtudiant() != null ? post.getEtudiant().getId() : null,
                post.getEtudiant() != null ? post.getEtudiant().getNom() : "Inconnu",
                post.getEtudiant() != null ? post.getEtudiant().getPrenom() : "Étudiant",
                post.getLikedBy().size(),
                likedByMe,
                commentDTOs
        );
    }

    private CommunityCommentDTO convertToCommentDTO(CommunityComment comment) {
        return new CommunityCommentDTO(
                comment.getId(),
                comment.getContenu(),
                comment.getDateCreation(),
                comment.getEtudiant() != null ? comment.getEtudiant().getId() : null,
                comment.getEtudiant() != null ? comment.getEtudiant().getNom() : "Inconnu",
                comment.getEtudiant() != null ? comment.getEtudiant().getPrenom() : "Étudiant"
        );
    }
}
