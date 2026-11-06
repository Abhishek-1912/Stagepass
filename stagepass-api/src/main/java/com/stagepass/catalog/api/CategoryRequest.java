package com.stagepass.catalog.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create (name and slug required) or partial update of a category. */
public record CategoryRequest(
        @Size(min = 1, max = 60) String name,
        @Pattern(regexp = "^[a-z0-9-]{2,40}$", message = "use 2–40 lowercase letters, digits or dashes") String slug,
        @Size(max = 40) String icon,
        @Min(0) Integer sortOrder,
        Boolean active) {
}
