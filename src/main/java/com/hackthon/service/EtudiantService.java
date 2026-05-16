package com.hackthon.service;

import com.hackthon.dto.AuthRequest;
import com.hackthon.dto.RegisterRequest;
import com.hackthon.entity.Domaine;
import com.hackthon.entity.Etudiant;
import com.hackthon.repository.DomaineRepository;
import com.hackthon.repository.EtudiantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class EtudiantService {

    private final EtudiantRepository etudiantRepository;
    private final DomaineRepository domaineRepository;

    @Transactional
    public Etudiant register(RegisterRequest request) {
        if (etudiantRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("Email déjà utilisé");
        }
        Etudiant etudiant = Etudiant.builder()
                .nom(request.nom())
                .prenom(request.prenom())
                .email(request.email())
                .motDePasse(request.motDePasse())
                .dateInscription(LocalDate.now())
                .scoreGlobal(0.0)
                .build();
        return etudiantRepository.save(etudiant);
    }

    public Etudiant login(AuthRequest request) {
        Etudiant etudiant = etudiantRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Identifiants incorrects"));
        if (!etudiant.getMotDePasse().equals(request.motDePasse())) {
            throw new RuntimeException("Identifiants incorrects");
        }
        return etudiant;
    }

    @Transactional
    public Etudiant choisirDomaine(Long etudiantId, Long domaineId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new RuntimeException("Étudiant non trouvé"));
        Domaine domaine = domaineRepository.findById(domaineId)
                .orElseThrow(() -> new RuntimeException("Domaine non trouvé"));
        etudiant.setDomaine(domaine);
        return etudiantRepository.save(etudiant);
    }
}
