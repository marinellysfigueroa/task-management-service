package com.taskmanagement.service.impl;

import com.taskmanagement.dto.AuthResponseDto;
import com.taskmanagement.dto.LoginRequestDto;
import com.taskmanagement.dto.RegisterRequestDto;
import com.taskmanagement.exception.BadRequestException;
import com.taskmanagement.model.Role;
import com.taskmanagement.model.User;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.security.jwt.JwtService;
import com.taskmanagement.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link AuthService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BadRequestException("Username already in use: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email already in use: " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();
        userRepository.save(user);

        return issueToken(user);
    }

    @Override
    public AuthResponseDto login(LoginRequestDto request) {
        // Delegates credential checking to the AuthenticationManager (backed by
        // DaoAuthenticationProvider + BCrypt) instead of comparing passwords by
        // hand, so hashing/lockout policy stays centralized in one place rather
        // than duplicated between the HTTP-Basic path this replaced and this one.
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            // Deliberately the same message whether the username does not exist or
            // the password is wrong: distinguishing the two would let a caller
            // enumerate valid usernames.
            throw new BadCredentialsException("Invalid username or password");
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalStateException(
                        "AuthenticationManager accepted credentials for a username that does not exist: "
                                + request.username()));

        return issueToken(user);
    }

    private AuthResponseDto issueToken(User user) {
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponseDto(
                token, "Bearer", jwtService.getExpirationSeconds(), user.getUsername(), user.getRole());
    }
}
