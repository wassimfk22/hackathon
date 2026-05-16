package com.hackthon.controller;

//─── Résumé d'un cours (sans le contenu complet) ─────────────────────────────

record CoursResume(
     Long id,
     String titre,
     double noteCours,
     boolean contenuGenere  // false si pas encore généré
) {}
