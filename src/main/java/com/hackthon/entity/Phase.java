package com.hackthon.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "phases")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Phase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titre;
    private Integer ordrePhase;
    private Double notePhase;
    private Boolean estValidee;

    // Points MAX de la phase = somme des points max de chaque quiz (10 pts par quiz)
    private Double pointsMax;

    // Points obtenus par l'étudiant sur cette phase (somme des scores de ses quiz)
    private Double pointsObtenus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id")
    private RoadMap roadMap;

    @OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Cours> cours;
}