package com.stagepass.common.error;

import com.stagepass.support.PostgresIntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GlobalExceptionHandlerTest.Config.class)
class GlobalExceptionHandlerTest extends PostgresIntegrationTest {

    @Test
    void apiExceptionBecomesProblem() throws Exception {
        mvc.perform(get("/test/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Event not found"));
    }

    @Test
    void validationListsFields() throws Exception {
        mvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void unexpectedErrorHidesDetails() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.detail").value("Unexpected error"));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void concurrentEditIsConflictNotServerError() throws Exception {
        mvc.perform(get("/test/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("reload")));
    }

    @Test
    void constraintViolationIsConflictWithoutLeakingSql() throws Exception {
        mvc.perform(get("/test/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("constraint"))));
    }

    record NamedRequest(@NotBlank String name) {
    }

    @RestController
    @RequestMapping("/test")
    static class ThrowingController {

        @GetMapping("/missing")
        String missing() {
            throw ApiException.notFound("Event");
        }

        @PostMapping("/validated")
        String validated(@Valid @RequestBody NamedRequest request) {
            return request.name();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/stale")
        String stale() {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException("Event", "e1");
        }

        @GetMapping("/duplicate")
        String duplicate() {
            throw new org.springframework.dao.DataIntegrityViolationException("duplicate key value violates unique constraint");
        }
    }

    @TestConfiguration
    static class Config {

        @Bean
        ThrowingController throwingController() {
            return new ThrowingController();
        }

        @Bean
        @Order(0)
        SecurityFilterChain testEndpoints(HttpSecurity http) throws Exception {
            http.securityMatcher("/test/**")
                    .csrf(c -> c.disable())
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }
    }
}
