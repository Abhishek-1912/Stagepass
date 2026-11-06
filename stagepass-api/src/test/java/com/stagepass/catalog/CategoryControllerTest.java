package com.stagepass.catalog;

import com.jayway.jsonpath.JsonPath;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import com.stagepass.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Test
    void publicCategoriesAreActiveOrderedAndCacheable() throws Exception {
        mvc.perform(get("/api/v1/public/categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=300")))
                .andExpect(jsonPath("$[0].slug").value("music"))
                .andExpect(jsonPath("$[*].slug", hasItem("food-lifestyle")));
    }

    @Test
    void adminCreatesAndDeactivatesCategory() throws Exception {
        TestUser admin = users.admin();
        String slug = "cat-" + UUID.randomUUID().toString().substring(0, 8);
        String id = JsonPath.read(mvc.perform(post("/api/v1/admin/categories").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Theatre\",\"slug\":\"" + slug + "\",\"icon\":\"drama\",\"sortOrder\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/public/categories")).andExpect(jsonPath("$[*].slug", hasItem(slug)));

        mvc.perform(patch("/api/v1/admin/categories/" + id).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Theatre"))
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/v1/public/categories")).andExpect(jsonPath("$[*].slug", not(hasItem(slug))));
        mvc.perform(get("/api/v1/admin/categories").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$[*].slug", hasItem(slug)));
    }

    @Test
    void duplicateCategorySlugIs409() throws Exception {
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", users.admin().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Music again\",\"slug\":\"music\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidCategorySlugIs400() throws Exception {
        mvc.perform(post("/api/v1/admin/categories").header("Authorization", users.admin().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bad\",\"slug\":\"Bad Slug!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("slug"));
    }
}
