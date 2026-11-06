package com.stagepass.catalog.service;

import com.stagepass.catalog.api.CategoryRequest;
import com.stagepass.catalog.api.CategoryResponse;
import com.stagepass.catalog.domain.Category;
import com.stagepass.catalog.repository.CategoryRepository;
import com.stagepass.common.audit.AuditLogService;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.FieldErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final AuditLogService audit;

    public CategoryService(CategoryRepository categories, AuditLogService audit) {
        this.categories = categories;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> active() {
        return categories.findByActiveTrueOrderBySortOrderAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> all() {
        return categories.findAllByOrderBySortOrderAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(UUID adminId, CategoryRequest request) {
        FieldErrors.start().require(request.name(), "name").require(request.slug(), "slug").throwIfAny();
        if (categories.existsBySlug(request.slug())) {
            throw ApiException.conflict("A category with slug '" + request.slug() + "' already exists.");
        }
        Category category = new Category();
        apply(category, request);
        categories.saveAndFlush(category);
        audit.record(adminId, "CATEGORY_CHANGED", "category", category.getId().toString(), null,
                CategoryResponse.from(category));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse update(UUID adminId, UUID id, CategoryRequest request) {
        Category category = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        CategoryResponse before = CategoryResponse.from(category);
        if (request.slug() != null && !request.slug().equals(category.getSlug())
                && categories.existsBySlug(request.slug())) {
            throw ApiException.conflict("A category with slug '" + request.slug() + "' already exists.");
        }
        apply(category, request);
        categories.flush();
        audit.record(adminId, "CATEGORY_CHANGED", "category", id.toString(), before, CategoryResponse.from(category));
        return CategoryResponse.from(category);
    }

    private static void apply(Category c, CategoryRequest r) {
        if (r.name() != null) {
            c.setName(r.name().trim());
        }
        if (r.slug() != null) {
            c.setSlug(r.slug());
        }
        if (r.icon() != null) {
            c.setIcon(r.icon());
        }
        if (r.sortOrder() != null) {
            c.setSortOrder(r.sortOrder());
        }
        if (r.active() != null) {
            c.setActive(r.active());
        }
    }
}
