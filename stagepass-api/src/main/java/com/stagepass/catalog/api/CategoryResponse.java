package com.stagepass.catalog.api;

import com.stagepass.catalog.domain.Category;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String slug, String icon, int sortOrder, boolean active) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getIcon(), c.getSortOrder(), c.isActive());
    }
}
