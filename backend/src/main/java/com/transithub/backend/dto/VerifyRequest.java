package com.transithub.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyRequest {
    private String email;
    private String code;
}
