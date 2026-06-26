package com.transithub.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenResponse {
    private String token;
    private String email;
    private String role;
    private String name;
    private String phone;
    private String photoUrl;
}