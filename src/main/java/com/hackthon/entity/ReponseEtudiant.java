package com.hackthon.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reponses_etudiant")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReponseEtudiant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer numeroQuestion;

    @Column(length = 1000)
    private String questionTexte;

    @Column(length = 1000)
    private String reponseEtudiant;

    @Column(length = 1000)
    private String bonneReponse;

    private Boolean estCorrecte;

    @Column(length = 2000)
    private String explicationIA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id")
    private Etudiant etudiant;
}
