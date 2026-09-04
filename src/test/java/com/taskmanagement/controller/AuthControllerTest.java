package com.taskmanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.dto.AuthResponseDto;
import com.taskmanagement.dto.LoginRequestDto;
import com.taskmanagement.dto.RegisterRequestDto;
import com.taskmanagement.exception.BadRequestException;
import com.taskmanagement.model.Role;
import com.taskmanagement.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link AuthController}; see {@link TaskControllerTest}
 * for the rationale behind the slice setup. Security filters are disabled here
 * on purpose — the point of this class is the HTTP contract of the two
 * endpoints, not whether the filter chain lets the request through, which is
 * instead covered end-to-end (real filters, real database) by
 * {@code AuthenticationIntegrationTest}.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final AuthResponseDto SAMPLE_SESSION =
            new AuthResponseDto("signed.jwt.token", "Bearer", 3600L, "mfigueroa", Role.USER);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void shouldRegisterAndReturnAToken() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto("mfigueroa", "marinellys.figueroa@gmail.com", "S3curePass!23");
        given(authService.register(any(RegisterRequestDto.class))).willReturn(SAMPLE_SESSION);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void shouldRejectRegistrationWithoutRoleField() throws Exception {
        // RegisterRequestDto has no `role` property at all: this is a compile-time
        // guarantee, not a runtime one, but sending it anyway must not blow up —
        // Jackson silently ignores the unknown field rather than failing the
        // request, and the response still comes back as a USER account.
        String payloadWithRole = """
                {"username":"mfigueroa","email":"marinellys.figueroa@gmail.com","password":"S3curePass!23","role":"ADMIN"}
                """;
        given(authService.register(any(RegisterRequestDto.class))).willReturn(SAMPLE_SESSION);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithRole))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void shouldRejectRegistrationWithInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/validation-error"))
                .andExpect(jsonPath("$.errors[*].field",
                        org.hamcrest.Matchers.containsInAnyOrder("username", "email", "password")));
    }

    @Test
    void shouldReturnBadRequestForADuplicateUsername() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto("mfigueroa", "marinellys.figueroa@gmail.com", "S3curePass!23");
        given(authService.register(any(RegisterRequestDto.class)))
                .willThrow(new BadRequestException("Username already in use: mfigueroa"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/bad-request"))
                .andExpect(jsonPath("$.detail").value("Username already in use: mfigueroa"));
    }

    @Test
    void shouldLoginAndReturnAToken() throws Exception {
        LoginRequestDto request = new LoginRequestDto("mfigueroa", "S3curePass!23");
        given(authService.login(any(LoginRequestDto.class))).willReturn(SAMPLE_SESSION);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.username").value("mfigueroa"));
    }

    @Test
    void shouldReturnUnauthorizedForBadCredentials() throws Exception {
        LoginRequestDto request = new LoginRequestDto("mfigueroa", "wrong-password");
        given(authService.login(any(LoginRequestDto.class)))
                .willThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void shouldRejectLoginWithBlankCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder("username", "password")));
    }
}
