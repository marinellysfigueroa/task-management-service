package com.taskmanagement.security;

import com.taskmanagement.security.jwt.JwtAuthenticationFilter;
import com.taskmanagement.security.jwt.JwtProperties;
import com.taskmanagement.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT authentication backed by {@link CustomUserDetailsService}.
 *
 * <p>Design decisions:
 * <ul>
 *   <li><b>No {@code WebSecurityConfigurerAdapter}.</b> That class has been
 *       deprecated (and removed as of Spring Security 6) in favor of exposing a
 *       {@link SecurityFilterChain} bean directly, which is what this class
 *       does.</li>
 *   <li><b>{@code STATELESS} sessions, no CSRF.</b> There is no server-side
 *       session and no browser form submission to protect: every request
 *       authenticates itself via the bearer token, so CSRF protection (designed
 *       for cookie-based auth) does not apply.</li>
 *   <li><b>Coarse-grained rules here, fine-grained via {@code @PreAuthorize}.</b>
 *       {@code authorizeHttpRequests} only draws the public/authenticated line;
 *       which specific role a specific operation needs (ADMIN-only user
 *       management, ADMIN-only project deletion) is declared as
 *       {@code @PreAuthorize} on the controller method it protects, right next
 *       to the code it guards, via {@link EnableMethodSecurity}.</li>
 *   <li><b>The JWT filter is built inline inside {@code securityFilterChain},
 *       not exposed as its own {@code @Bean}.</b> A {@link jakarta.servlet.Filter}
 *       that also happens to be a Spring bean gets auto-registered a second time
 *       as a generic servlet filter by Spring Boot, running once outside the
 *       security chain and once inside it. Constructing it as a plain object
 *       here sidesteps that trap entirely.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/info"
    };

    private final CustomUserDetailsService userDetailsService;
    private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemDetailAccessDeniedHandler accessDeniedHandler;
    private final JwtService jwtService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Used by {@code AuthServiceImpl} to verify username/password on login. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtService, userDetailsService, authenticationEntryPoint);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Failures raised inside the filter chain never reach
                // @RestControllerAdvice, so they get their own ProblemDetail
                // writers to keep one single error contract across the API.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }
}
