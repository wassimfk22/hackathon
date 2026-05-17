package com.hackthon.dto;

public record LeaderboardUserDTO(
        Long id,
        String nom,
        String prenom,
        Double xp,
        String niveau,
        String domaineNom
) {}
