package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserRestController.class)
@Import(MvcExceptionHandler.class)
@WithMockUser
class UserApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void shouldReturnCreatedLocationVersionAndReplayMetadata() throws Exception {
        when(userService.createUser(eq("request-123"), any(CreateUserRequest.class)))
                .thenReturn(new UserCreation(
                        new UserResponse(7L, "alice", "alice@example.com", 0),
                        false
                ));

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .header("Idempotency-Key", "request-123")
                        .contentType("application/json")
                        .content("""
                                {"username":"alice","email":"alice@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/7"))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void shouldRequireIfMatchForAReplacement() throws Exception {
        mockMvc.perform(put("/api/users/7")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"alice","email":"alice@example.com"}
                                """))
                .andExpect(status().isPreconditionRequired())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("IF_MATCH_REQUIRED"));
    }

    @Test
    void shouldMapAStaleVersionToPreconditionFailed() throws Exception {
        when(userService.replaceUser(eq(7L), eq(0L), any(UpdateUserRequest.class)))
                .thenThrow(new PreconditionFailedException("Resource changed"));

        mockMvc.perform(put("/api/users/7")
                        .with(csrf())
                        .header("If-Match", "\"0\"")
                        .contentType("application/json")
                        .content("""
                                {"username":"alice","email":"alice@example.com"}
                                """))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
    }

    @Test
    void shouldReturnStructuredFieldErrors() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .header("Idempotency-Key", "request-123")
                        .contentType("application/json")
                        .content("""
                                {"username":"","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.username").exists())
                .andExpect(jsonPath("$.fields.email").exists());
    }
}
