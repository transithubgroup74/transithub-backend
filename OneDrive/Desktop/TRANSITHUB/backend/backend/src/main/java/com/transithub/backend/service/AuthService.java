package com.transithub.backend.service;

import com.transithub.backend.config.JwtUtil;
import com.transithub.backend.dto.*;
import com.transithub.backend.model.Operator;
import com.transithub.backend.model.User;
import com.transithub.backend.repository.OperatorRepository;
import com.transithub.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OperatorRepository operatorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       OperatorRepository operatorRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.operatorRepository = operatorRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public TokenResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), "USER");
        return TokenResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role("USER")
                .build();
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getEmail(), "USER");
        return TokenResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role("USER")
                .build();
    }

    public TokenResponse operatorLogin(LoginRequest request) {
        Operator operator = operatorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), operator.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(operator.getEmail(), "OPERATOR");
        return TokenResponse.builder()
                .token(token)
                .email(operator.getEmail())
                .role("OPERATOR")
                .build();
    }

    public void updateFcmToken(String email, String token) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setFcmToken(token);
        userRepository.save(user);
    }
}