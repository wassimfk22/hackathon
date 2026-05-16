package com.hackthon.dto;

//─── Résumé d'un cours (sans le contenu complet) ─────────────────────────────

public record CoursResume(
     Long id,
     String titre,
     double noteCours,
     boolean contenuGenere  // false si pas encore généré
) {}
