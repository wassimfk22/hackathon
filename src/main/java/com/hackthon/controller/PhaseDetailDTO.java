package com.hackthon.controller;

import java.util.List;

//─── Phase avec ses cours ─────────────────────────────────────────────────────

record PhaseDetailDTO(
     Long id,
     String titre,
     int ordrePhase,
     double notePhase,
     boolean estValidee,
     List<CoursResume> cours
) {}