package com.transithub.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FcmTokenRequest {
    private String token;
}