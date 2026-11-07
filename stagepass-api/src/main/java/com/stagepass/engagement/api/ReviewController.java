package com.stagepass.engagement.api;

import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.engagement.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ReviewController {

    private final ReviewService reviews;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviews, CurrentUser currentUser) {
        this.reviews = reviews;
        this.currentUser = currentUser;
    }

    @PutMapping("/api/v1/events/{eventId}/review")
    public ResponseEntity<PublicReview> upsert(@PathVariable UUID eventId, @Valid @RequestBody ReviewRequest request) {
        ReviewService.Saved s = reviews.upsert(currentUser.id(), eventId, request);
        return ResponseEntity.status(s.created() ? HttpStatus.CREATED : HttpStatus.OK).body(s.review());
    }

    @DeleteMapping("/api/v1/events/{eventId}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID eventId) {
        reviews.delete(currentUser.id(), eventId);
    }

    @GetMapping("/api/v1/events/{eventId}/review/mine")
    public MyReview mine(@PathVariable UUID eventId) {
        return reviews.mine(currentUser.id(), eventId);
    }

    @GetMapping("/api/v1/public/events/{slug}/reviews")
    public PageResponse<PublicReview> forEvent(@PathVariable String slug, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return reviews.forEvent(slug, page, size);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/v1/admin/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminDelete(@PathVariable UUID id) {
        reviews.adminDelete(currentUser.id(), id);
    }
}
