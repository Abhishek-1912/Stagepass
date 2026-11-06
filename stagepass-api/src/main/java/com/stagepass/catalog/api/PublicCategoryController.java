package com.stagepass.catalog.api;

import com.stagepass.catalog.service.CategoryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
public class PublicCategoryController {

    private final CategoryService categories;

    public PublicCategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping("/api/v1/public/categories")
    public ResponseEntity<List<CategoryResponse>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(categories.active());
    }
}
