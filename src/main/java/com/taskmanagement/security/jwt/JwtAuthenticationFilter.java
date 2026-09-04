package com.taskmanagement.security.jwt;

import com.taskmanagement.security.CustomUserDetailsService;
import com.taskmanagement.security.ProblemDetailAuthenticationEntryPoint;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the {@code Authorization: Bearer <token>} header, verifies it, and — on
 * success — populates the {@link SecurityContextHolder} for the rest of the
 * request.
 *
 * <p>Design decisions:
 * <ul>
 *   <li><b>No header at all → pass through untouched.</b> The filter must not
 *       reject requests to public routes ({@code /api/v1/auth/**}, Swagger).
 *       Leaving the context empty and calling the chain lets
 *       {@code authorizeHttpRequests} make that call: {@code permitAll()} routes
 *       proceed, everything else is rejected downstream with the standard
 *       "authentication required" 401.</li>
 *   <li><b>A present-but-broken token is handled inline, not rethrown.</b> This
 *       filter is registered with
 *       {@code addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)},
 *       which places it <em>before</em> {@code ExceptionTranslationFilter} in
 *       Spring Security's chain. {@code ExceptionTranslationFilter} only catches
 *       exceptions thrown by filters that run <em>after</em> it — an exception
 *       thrown from here would propagate past it uncaught and surface as a raw
 *       500. So instead of throwing, failures are handed directly to the same
 *       {@link ProblemDetailAuthenticationEntryPoint} used everywhere else,
 *       which keeps the error contract identical either way.</li>
 *   <li><b>Authorities are re-read from the database, not trusted from the
 *       token's {@code role} claim.</b> A role change or account deletion then
 *       takes effect on the next request instead of only once the (short-lived)
 *       token expires.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtService.parseClaims(token);
            authenticate(claims.getSubject(), request);
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException ex) {
            reject(request, response, "The access token has expired. Log in again to obtain a new one.");
        } catch (JwtException | IllegalArgumentException ex) {
            reject(request, response, "The access token is invalid.");
        } catch (UsernameNotFoundException ex) {
            // Well-formed, unexpired token for an account that no longer exists.
            reject(request, response, "The account associated with this token no longer exists.");
        }
    }

    private void authenticate(String username, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String reason) throws IOException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response, new InvalidJwtAuthenticationException(reason));
    }
}
