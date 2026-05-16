package com.hackthon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "quizs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titre;
    private Double score;
    private LocalDateTime datePassage;
    private Boolean estReussi;

    @Column(length = 2000)
    private String feedbackIA;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cours_id", unique = true)
    private Cours cours;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReponseEtudiant> reponses;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Amelioration> ameliorations;
}
