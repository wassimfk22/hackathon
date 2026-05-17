package com.hackthon.dto;

import java.time.LocalDateTime;

public record CommunityCommentDTO(
        Long id,
        String contenu,
        LocalDateTime dateCreation,
        Long etudiantId,
        String etudiantNom,
        String etudiantPrenom
) {}
