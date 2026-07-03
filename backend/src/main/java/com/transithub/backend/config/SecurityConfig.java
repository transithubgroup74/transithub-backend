package com.transithub.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── Public: passenger auth, staff login, conductor QR verify ──
                        .requestMatchers("/api/auth/**", "/api/staff/login").permitAll()
                        .requestMatchers("/api/bookings/*/complete", "/api/bookings/*/verify", "/api/bookings/verify-qr").permitAll()
                        // ── Public: passenger browsing (read-only, no PII) ──
                        .requestMatchers(HttpMethod.GET, "/api/routes", "/api/routes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/schedules", "/api/schedules/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/alerts").permitAll()
                        // ── Staff only: all admin data (PII), fleet, and every write ──
                        .requestMatchers("/api/admin/**").hasAuthority("STAFF")
                        .requestMatchers("/api/staff", "/api/staff/**").hasAuthority("STAFF")
                        .requestMatchers("/api/buses", "/api/buses/**").hasAuthority("STAFF")
                        .requestMatchers("/api/drivers", "/api/drivers/**").hasAuthority("STAFF")
                        .requestMatchers("/api/operators", "/api/operators/**").hasAuthority("STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/routes", "/api/routes/**").hasAuthority("STAFF")
                        .requestMatchers(HttpMethod.PUT, "/api/routes/**").hasAuthority("STAFF")
                        .requestMatchers(HttpMethod.DELETE, "/api/routes/**").hasAuthority("STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/schedules", "/api/schedules/**").hasAuthority("STAFF")
                        .requestMatchers(HttpMethod.DELETE, "/api/schedules/**").hasAuthority("STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/alerts").hasAuthority("STAFF")
                        .requestMatchers(HttpMethod.DELETE, "/api/alerts/**").hasAuthority("STAFF")
                        // ── Everything else (passenger's own bookings/profile) needs a token ──
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}