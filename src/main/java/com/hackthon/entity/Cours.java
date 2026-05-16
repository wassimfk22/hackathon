package com.hackthon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cours")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titre;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String contenu;

    private LocalDateTime dateGeneration;

    @Enumerated(EnumType.STRING)
    private com.hackthon.enums.TypeContenu typeContenu;

    private Double noteCours;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id")
    private Phase phase;

    @OneToOne(mappedBy = "cours", cascade = CascadeType.ALL, orphanRemoval = true)
    private Quiz quiz;
}
