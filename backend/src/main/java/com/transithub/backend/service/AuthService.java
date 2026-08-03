package com.transithub.backend.service;

import com.transithub.backend.config.JwtUtil;
import com.transithub.backend.dto.*;
import com.transithub.backend.exception.ApiException;
import com.transithub.backend.model.Operator;
import com.transithub.backend.model.User;
import com.transithub.backend.repository.OperatorRepository;
import com.transithub.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL_RE =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final int CODE_TTL_MINUTES = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OperatorRepository operatorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final EmailAddressValidator emailAddressValidator;

    @org.springframework.beans.factory.annotation.Value("${app.require-email-verification:false}")
    private boolean requireEmailVerification;

    public AuthService(UserRepository userRepository,
                       OperatorRepository operatorRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       EmailService emailService,
                       EmailAddressValidator emailAddressValidator) {
        this.userRepository = userRepository;
        this.operatorRepository = operatorRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.emailAddressValidator = emailAddressValidator;
    }

    /**
     * Creates the account in an unverified state and emails a 6-digit code.
     * No token is returned here — the caller gets one from verifyEmail once
     * they prove they can open the inbox.
     */
    public Map<String, Object> register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (!EMAIL_RE.matcher(email).matches()) {
            throw new ApiException(400, "invalid_email", "That doesn't look like a valid email address.");
        }

        String domain = email.substring(email.indexOf('@') + 1);
        if (emailAddressValidator.isDisposable(domain)) {
            throw new ApiException(400, "disposable_email",
                    "Please use a permanent email address, not a temporary one.");
        }
        if (!emailAddressValidator.domainCanReceiveMail(domain)) {
            throw new ApiException(400, "unknown_domain",
                    "We couldn't find an email service at \"" + domain + "\". Please check the spelling.");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new ApiException(400, "weak_password", "Password must be at least 8 characters.");
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user != null && !isUnverified(user)) {
            throw new ApiException(409, "email_taken", "That email is already registered. Try logging in instead.");
        }
        if (user == null) {
            // Only new sign-ups get the normalised address; existing rows keep
            // whatever casing they were created with.
            user = new User();
            user.setEmail(email);
        }

        // Reaching here with an existing row means they started a sign-up but
        // never entered the code, so let them pick up where they left off.
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        if (!requireEmailVerification) {
            // Codes aren't deliverable yet, so don't strand people behind a
            // screen waiting for one. Everything else about the account is
            // unchanged — only the inbox-ownership check is skipped.
            user.setEmailVerified(true);
            user.setVerificationCode(null);
            user.setVerificationExpiry(null);
            userRepository.save(user);
            // Send a welcome email — fire-and-forget, never blocks sign-up.
            emailService.sendWelcome(user.getEmail(), user.getName());
            return tokenBody(issueToken(user));
        }

        user.setEmailVerified(false);
        String code = newCode();
        user.setVerificationCode(code);
        user.setVerificationExpiry(LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES));
        userRepository.save(user);

        sendCode(user.getEmail(), user.getName(), code);

        Map<String, Object> body = new HashMap<>();
        body.put("status", "verification_required");
        body.put("email", user.getEmail());
        body.put("message", "We sent a 6-digit code to " + user.getEmail());
        return body;
    }

    /** Checks the emailed code and, if it matches, activates the account. */
    public TokenResponse verifyEmail(VerifyRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new ApiException(404, "not_found", "No account found for that email."));

        if (!isUnverified(user)) {
            return issueToken(user);  // already verified — nothing to do
        }
        if (user.getVerificationCode() == null || user.getVerificationExpiry() == null) {
            throw new ApiException(400, "no_code", "No code is pending. Request a new one.");
        }
        if (user.getVerificationExpiry().isBefore(LocalDateTime.now())) {
            throw new ApiException(400, "code_expired", "That code has expired. Request a new one.");
        }
        String supplied = request.getCode() == null ? "" : request.getCode().trim();
        if (!user.getVerificationCode().equals(supplied)) {
            throw new ApiException(400, "code_invalid", "That code isn't right. Check it and try again.");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationExpiry(null);
        userRepository.save(user);
        // Account is now real — welcome them (fire-and-forget).
        emailService.sendWelcome(user.getEmail(), user.getName());
        return issueToken(user);
    }

    public Map<String, Object> resendCode(String rawEmail) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(rawEmail))
                .orElseThrow(() -> new ApiException(404, "not_found", "No account found for that email."));

        if (!isUnverified(user)) {
            throw new ApiException(400, "already_verified", "That email is already verified — just log in.");
        }

        String code = newCode();
        user.setVerificationCode(code);
        user.setVerificationExpiry(LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES));
        userRepository.save(user);

        sendCode(user.getEmail(), user.getName(), code);

        Map<String, Object> body = new HashMap<>();
        body.put("status", "verification_required");
        body.put("email", user.getEmail());
        body.put("message", "New code sent to " + user.getEmail());
        return body;
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new ApiException(401, "bad_credentials", "Incorrect email or password."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(401, "bad_credentials", "Incorrect email or password.");
        }
        if (requireEmailVerification && isUnverified(user)) {
            throw new ApiException(403, "email_unverified",
                    "Please verify your email before logging in.");
        }
        return issueToken(user);
    }

    public TokenResponse operatorLogin(LoginRequest request) {
        Operator operator = operatorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(401, "bad_credentials", "Incorrect email or password."));

        if (!passwordEncoder.matches(request.getPassword(), operator.getPasswordHash())) {
            throw new ApiException(401, "bad_credentials", "Incorrect email or password.");
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
                .orElseThrow(() -> new ApiException(404, "not_found", "User not found"));
        user.setFcmToken(token);
        userRepository.save(user);
    }

    public Map<String, Object> getProfile(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(404, "not_found", "User not found"));
        Map<String, Object> m = new HashMap<>();
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("photoUrl", u.getPhotoUrl());
        return m;
    }

    public Map<String, Object> updateProfile(String email, Map<String, Object> body) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(404, "not_found", "User not found"));
        if (body.get("name") != null) u.setName((String) body.get("name"));
        if (body.get("phone") != null) u.setPhone((String) body.get("phone"));
        if (body.get("photoUrl") != null) u.setPhotoUrl((String) body.get("photoUrl"));
        userRepository.save(u);
        return getProfile(email);
    }

    // --- helpers ---

    /** NULL means the account predates verification, so it counts as verified. */
    private boolean isUnverified(User user) {
        return Boolean.FALSE.equals(user.getEmailVerified());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String newCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private void sendCode(String email, String name, String code) {
        try {
            emailService.sendVerificationCode(email, name, code);
        } catch (Exception e) {
            System.err.println("TransitHub: verification email failed – " + e.getMessage());
            throw new ApiException(503, "email_failed",
                    "We couldn't send the code to that address. Check the email and try again.");
        }
    }

    /** Same fields the app reads off a login, so it can sign in immediately. */
    private Map<String, Object> tokenBody(TokenResponse t) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "registered");
        body.put("token", t.getToken());
        body.put("email", t.getEmail());
        body.put("role", t.getRole());
        body.put("name", t.getName());
        body.put("phone", t.getPhone());
        body.put("photoUrl", t.getPhotoUrl());
        return body;
    }

    private TokenResponse issueToken(User user) {
        return TokenResponse.builder()
                .token(jwtUtil.generateToken(user.getEmail(), "USER"))
                .email(user.getEmail())
                .role("USER")
                .name(user.getName())
                .phone(user.getPhone())
                .photoUrl(user.getPhotoUrl())
                .build();
    }
}
