package com.hackthon.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CommunityPostDTO(
        Long id,
        String titre,
        String contenu,
        LocalDateTime dateCreation,
        Long etudiantId,
        String etudiantNom,
        String etudiantPrenom,
        int likesCount,
        boolean likedByMe,
        List<CommunityCommentDTO> comments
) {}
