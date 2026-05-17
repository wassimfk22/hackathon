package com.hackthon.dto;

public record CommunityCommentRequest(
        String contenu,
        Long etudiantId
) {}
