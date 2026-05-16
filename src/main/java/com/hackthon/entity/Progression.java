package com.hackthon.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "progressions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Progression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double progressionGlobale;
    private Integer phasesTerminees;
    private Integer coursTermines;
    private Integer quizReussis;
    private int xp;
    private int niveau;
    private String titreRank;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", unique = true)
    private Etudiant etudiant;
}
