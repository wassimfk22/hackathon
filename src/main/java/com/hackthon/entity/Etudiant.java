package com.hackthon.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "etudiants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Etudiant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;
    private String prenom;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String motDePasse;

    private LocalDate dateInscription;
    private Double scoreGlobal;
    private Integer rangDomaine;

    @Enumerated(EnumType.STRING)
    private com.hackthon.enums.Niveau niveau;

    // Domaine choisi par l'étudiant
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domaine_id")
    private Domaine domaine;

    @OneToMany(mappedBy = "etudiant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EvaluationIA> evaluations;

    @OneToMany(mappedBy = "etudiant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoadMap> roadMaps;

    @JsonIgnore
    @OneToOne(mappedBy = "etudiant", cascade = CascadeType.ALL, orphanRemoval = true)
    private Progression progression;

    @ManyToMany
    @JoinTable(
        name = "etudiant_badges",
        joinColumns = @JoinColumn(name = "etudiant_id"),
        inverseJoinColumns = @JoinColumn(name = "badge_id")
    )
    private Set<Badge> badges = new HashSet<>();

    @OneToMany(mappedBy = "etudiant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReponseEtudiant> reponses;

    @OneToMany(mappedBy = "etudiant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Amelioration> ameliorations;
}
