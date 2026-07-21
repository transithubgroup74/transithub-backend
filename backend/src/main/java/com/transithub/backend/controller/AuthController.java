package com.transithub.backend.controller;

import com.transithub.backend.dto.*;
import com.transithub.backend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Creates the account unverified and emails a code — no token yet. */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /** Exchanges a valid emailed code for a token. */
    @PostMapping("/verify")
    public ResponseEntity<TokenResponse> verify(@RequestBody VerifyRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request));
    }

    @PostMapping("/resend-code")
    public ResponseEntity<Map<String, Object>> resendCode(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.resendCode(body.get("email")));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/operator/login")
    public ResponseEntity<TokenResponse> operatorLogin(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.operatorLogin(request));
    }
}
