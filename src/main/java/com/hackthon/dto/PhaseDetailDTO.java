package com.hackthon.dto;

import java.util.List;

//─── Phase avec ses cours ─────────────────────────────────────────────────────

public record PhaseDetailDTO(
     Long id,
     String titre,
     int ordrePhase,
     double notePhase,
     boolean estValidee,
     List<CoursResume> cours
) {}
