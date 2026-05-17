package com.hackthon.controller;

import com.hackthon.dto.*;
import com.hackthon.service.CommunityPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    @GetMapping
    public ResponseEntity<List<CommunityPostDTO>> getAllPosts(@RequestParam Long currentEtudiantId) {
        return ResponseEntity.ok(communityPostService.getAllPosts(currentEtudiantId));
    }

    @PostMapping
    public ResponseEntity<?> createPost(@RequestBody CommunityPostRequest request) {
        try {
            return ResponseEntity.ok(communityPostService.createPost(request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(new com.hackthon.controller.ErrorResponse(ex.getMessage()));
        }
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<CommunityPostDTO> toggleLikePost(
            @PathVariable Long postId,
            @RequestParam Long currentEtudiantId) {
        return ResponseEntity.ok(communityPostService.toggleLikePost(postId, currentEtudiantId));
    }

    @PostMapping("/{postId}/comment")
    public ResponseEntity<CommunityPostDTO> addComment(
            @PathVariable Long postId,
            @RequestBody CommunityCommentRequest request) {
        return ResponseEntity.ok(communityPostService.addComment(postId, request));
    }
}

// Simple local record for cleaner error responses
record ErrorResponse(String error) {}
