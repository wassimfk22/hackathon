package com.hackthon.dto;

public record MessageDTO(
        String role,   // "user" ou "assistant"
        String content
) {}