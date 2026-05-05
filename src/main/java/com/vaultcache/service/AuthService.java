package com.vaultcache.service;

import com.vaultcache.entity.UserEntity;
import com.vaultcache.model.*;
import com.vaultcache.repository.UserRepository;
import com.vaultcache.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j @Service @RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider      jwtProvider;
    private final UserRepository        userRepo;
    private final PasswordEncoder       encoder;

    public JwtResponse login(JwtRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        String token = jwtProvider.generateToken(auth);
        UserEntity user = userRepo.findByUsername(req.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return new JwtResponse(token, user.getUsername(), user.getRole());
    }

    public JwtResponse register(RegisterRequest req) {
        if (userRepo.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username '" + req.getUsername() + "' already taken");
        }
        userRepo.save(UserEntity.builder()
                .username(req.getUsername())
                .password(encoder.encode(req.getPassword()))
                .role("USER").build());
        return login(new JwtRequest(req.getUsername(), req.getPassword()));
    }
}
