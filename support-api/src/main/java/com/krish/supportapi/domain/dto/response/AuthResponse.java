package com.krish.supportapi.domain.dto.response;

import com.krish.supportapi.domain.enums.UserRole;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private UUID userId;

    private String email;

    private String fullName;

    private UserRole role;

    private String accessToken;

    private String refreshToken;
}
