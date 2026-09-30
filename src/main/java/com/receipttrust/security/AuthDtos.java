package com.receipttrust.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank String fullName,
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record LogoutRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken
    ) {
    }

    public record RegisterResponse(
            Long id,
            String username,
            String email
    ) {
    }

    /** Google Identity Services ID token from the frontend. */
    public record GoogleLoginRequest(
            @NotBlank String idToken
    ) {
    }

    /** Facebook access token from the frontend (prepared; needs app keys). */
    public record FacebookLoginRequest(
            @NotBlank String accessToken
    ) {
    }

    public record SetPasswordRequest(
            @NotBlank @Size(min = 8, max = 100) String password
    ) {
    }
}
