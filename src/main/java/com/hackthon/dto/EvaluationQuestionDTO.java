package com.hackthon.dto;

import java.util.List;

public record EvaluationQuestionDTO(
    String question,
    List<String> options
) {}
