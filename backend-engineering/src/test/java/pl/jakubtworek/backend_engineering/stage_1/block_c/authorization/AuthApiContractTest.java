package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(AuthExceptionHandler.class)
@WithMockUser
class AuthApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void shouldReturnTheSameSafeErrorForInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"unknown","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void shouldRejectOversizedCredentialsBeforePasswordHashing() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"alice","password":"%s"}
                                """.formatted("x".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void shouldHideRefreshTokenReuseBehindTheGenericPublicError() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class)))
                .thenThrow(new RefreshTokenReuseException());

        mockMvc.perform(post("/auth/refresh")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"stolen-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.detail").value("Refresh token is invalid or expired"));
    }
}
