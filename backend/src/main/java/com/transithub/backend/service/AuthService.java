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
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), "USER");
        return TokenResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role("USER")
                .name(user.getName())
                .phone(user.getPhone())
                .photoUrl(user.getPhotoUrl())
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
                .name(user.getName())
                .phone(user.getPhone())
                .photoUrl(user.getPhotoUrl())
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

    public java.util.Map<String, Object> getProfile(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("photoUrl", u.getPhotoUrl());
        return m;
    }

    public java.util.Map<String, Object> updateProfile(String email, java.util.Map<String, Object> body) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (body.get("name") != null) u.setName((String) body.get("name"));
        if (body.get("phone") != null) u.setPhone((String) body.get("phone"));
        if (body.get("photoUrl") != null) u.setPhotoUrl((String) body.get("photoUrl"));
        userRepository.save(u);
        return getProfile(email);
    }
}