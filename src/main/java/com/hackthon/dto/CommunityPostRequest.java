package com.hackthon.dto;

public record CommunityPostRequest(
        String titre,
        String contenu,
        Long etudiantId
) {}
