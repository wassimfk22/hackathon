package com.hackthon.dto;

import java.time.LocalDateTime;

public record ChatResponse(
        String reponse,
        String coursTitre,
        LocalDateTime timestamp
) {}