package com.fieldops.shared.presentation.advice;

import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.ConflictException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.infrastructure.filter.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the {@link GlobalExceptionHandler}.
 *
 * <p>Uses a tiny fake controller declared as an inner class to trigger
 * each exception type without depending on real business controllers.</p>
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    // ── Fake controller used exclusively by these tests ─────────────────

    @RestController
    @RequestMapping("/test")
    static class FakeController {

        record FakeRequest(@NotBlank String name) {}

        @PostMapping("/validate")
        void validate(@Valid @RequestBody FakeRequest body) {
            // triggers MethodArgumentNotValidException when @NotBlank fails
        }

        @GetMapping("/not-found")
        void notFound() {
            throw ResourceNotFoundException.of("Equipment", "abc-123");
        }

        @PutMapping("/conflict")
        void conflict() {
            throw new ConflictException("EMAIL_ALREADY_EXISTS",
                    "A user with this email already exists");
        }

        @PostMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleException("INSPECTION_ALREADY_SUBMITTED",
                    "Cannot edit an inspection that has already been submitted");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new RuntimeException("Something went very wrong internally");
        }
    }

    // ── 400 Validation ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST with invalid body → 400 with fieldErrors populated")
    void validationError_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }

    // ── 404 Not Found ───────────────────────────────────────────────────

    @Test
    @DisplayName("ResourceNotFoundException → 404 with correct code")
    void resourceNotFound_returns404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("EQUIPMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("abc-123")))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }

    // ── 409 Conflict ────────────────────────────────────────────────────

    @Test
    @DisplayName("ConflictException → 409 with correct code")
    void conflict_returns409() throws Exception {
        mockMvc.perform(put("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }

    // ── 422 Business Rule ───────────────────────────────────────────────

    @Test
    @DisplayName("BusinessRuleException → 422 with correct code")
    void businessRule_returns422() throws Exception {
        mockMvc.perform(post("/test/business-rule")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("INSPECTION_ALREADY_SUBMITTED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }

    // ── 500 Fallback ────────────────────────────────────────────────────

    @Test
    @DisplayName("Unexpected RuntimeException → 500 without stack trace in body")
    void unexpected_returns500WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message", not(containsString("RuntimeException"))))
                .andExpect(jsonPath("$.message", not(containsString("stackTrace"))))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }

    // ── X-Request-Id passthrough ────────────────────────────────────────

    @Test
    @DisplayName("When client sends X-Request-Id, it is returned in the response")
    void requestId_passthrough() throws Exception {
        String customId = "my-custom-trace-id-12345";

        mockMvc.perform(get("/test/not-found")
                        .header("X-Request-Id", customId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", customId))
                .andExpect(jsonPath("$.requestId").value(customId));
    }
}
