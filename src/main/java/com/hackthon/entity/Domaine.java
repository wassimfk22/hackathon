package com.hackthon.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "domaines")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Domaine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom;

    @Column(length = 1000)
    private String description;
    
}
