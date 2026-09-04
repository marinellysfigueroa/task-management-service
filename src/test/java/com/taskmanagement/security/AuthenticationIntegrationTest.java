package com.taskmanagement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.integration.AbstractIntegrationTest;
import com.taskmanagement.model.Role;
import com.taskmanagement.model.User;
import com.taskmanagement.repository.ProjectRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Spring Security filter chain end to end — real
 * {@code JwtAuthenticationFilter}, real {@code @PreAuthorize} interceptors,
 * real database. The web-layer slice tests ({@code TaskControllerTest} & co.)
 * disable security filters entirely to isolate the HTTP contract, so this is
 * the only place that actually proves a token gets you in, a missing one
 * doesn't, and the wrong role gets a 403 instead of a 401.
 */
@AutoConfigureMockMvc
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterLoginAndAccessAProtectedEndpointWithTheIssuedToken() throws Exception {
        String registerPayload = """
                {"username":"newuser","email":"newuser@example.com","password":"S3curePass!23"}
                """;

        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(registerResponse).get("accessToken").asText();

        // The token from registration works immediately, no separate login call needed.
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Logging in with the same credentials issues an equally valid token.
        String loginPayload = """
                {"username":"newuser","password":"S3curePass!23"}
                """;
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"));
    }

    @Test
    void shouldRejectRegistrationOfADuplicateUsername() throws Exception {
        String payload = """
                {"username":"dup","email":"dup1@example.com","password":"S3curePass!23"}
                """;
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        String samePayloadDifferentEmail = """
                {"username":"dup","email":"dup2@example.com","password":"S3curePass!23"}
                """;
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(samePayloadDifferentEmail))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/bad-request"))
                .andExpect(jsonPath("$.detail").value("Username already in use: dup"));
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        userRepository.save(User.builder()
                .username("carol").email("carol@example.com")
                .password(passwordEncoder.encode("correct-password"))
                .role(Role.USER).build());

        String payload = """
                {"username":"carol","password":"wrong-password"}
                """;
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void shouldRejectALoginForAUsernameThatDoesNotExist() throws Exception {
        String payload = """
                {"username":"ghost","password":"whatever123"}
                """;
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized())
                // Same message as a wrong password: this endpoint never reveals
                // whether the username itself exists.
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void shouldRejectAProtectedRequestWithNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/unauthorized"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void shouldRejectAMalformedBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/unauthorized"))
                .andExpect(jsonPath("$.detail").value("The access token is invalid."))
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Bearer")));
    }

    @Test
    void shouldRejectATokenForAnAccountThatNoLongerExists() throws Exception {
        // Signed correctly and unexpired, but naming an account that was never
        // persisted (or has since been deleted) — verifies the filter re-checks
        // the database instead of trusting the token's claims blindly.
        String token = jwtService.generateToken("nobody-by-this-name", Role.USER);

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("The account associated with this token no longer exists."));
    }

    @Test
    void shouldAllowAnAuthenticatedUserToCreateAProjectButNotDeleteOne() throws Exception {
        User user = userRepository.save(User.builder()
                .username("regularuser").email("regularuser@example.com")
                .password(passwordEncoder.encode("whatever123")).role(Role.USER).build());
        String userToken = jwtService.generateToken(user.getUsername(), user.getRole());

        String createPayload = """
                {"name":"Alpha","ownerId":%d}
                """.formatted(user.getId());

        String createResponse = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long projectId = objectMapper.readTree(createResponse).get("id").asLong();

        // @PreAuthorize("hasRole('ADMIN')") on the delete endpoint: a USER gets a
        // 403, distinctly different from the 401s above, since this caller *is*
        // authenticated — just not authorized for this specific action.
        mockMvc.perform(delete("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.taskmanagement.com/problems/access-denied"));
    }

    @Test
    void shouldAllowAnAdminToDeleteAProjectAndManageUsers() throws Exception {
        User owner = userRepository.save(User.builder()
                .username("projectowner").email("projectowner@example.com")
                .password(passwordEncoder.encode("whatever123")).role(Role.USER).build());
        User admin = userRepository.save(User.builder()
                .username("theadmin").email("theadmin@example.com")
                .password(passwordEncoder.encode("whatever123")).role(Role.ADMIN).build());
        String adminToken = jwtService.generateToken(admin.getUsername(), admin.getRole());

        String createPayload = """
                {"name":"Alpha","ownerId":%d}
                """.formatted(owner.getId());
        String createResponse = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long projectId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/v1/projects/" + projectId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // POST /api/v1/users is ADMIN-only now that self-registration exists
        // separately: this is where an admin grants a role other than USER.
        String createUserPayload = """
                {"username":"promoted","email":"promoted@example.com","password":"S3curePass!23","role":"MANAGER"}
                """;
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void shouldRejectANonAdminCreatingAUserThroughTheAdminEndpoint() throws Exception {
        User user = userRepository.save(User.builder()
                .username("notanadmin").email("notanadmin@example.com")
                .password(passwordEncoder.encode("whatever123")).role(Role.USER).build());
        String userToken = jwtService.generateToken(user.getUsername(), user.getRole());

        String payload = """
                {"username":"sneaky","email":"sneaky@example.com","password":"S3curePass!23","role":"ADMIN"}
                """;
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }
}
