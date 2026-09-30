package com.receipttrust.security;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.config.TrustProperties;
import com.receipttrust.user.AuthProvider;
import com.receipttrust.user.User;
import com.receipttrust.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TrustProperties trustProperties;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       TrustProperties trustProperties,
                       GoogleTokenVerifier googleTokenVerifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.trustProperties = trustProperties;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    @Transactional
    public User register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiExceptions.ConflictException("Username already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiExceptions.ConflictException("Email already registered");
        }
        User user = new User(
                request.fullName(),
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                trustProperties.getInitialScore());
        return userRepository.save(user);
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null) {
            throw new ApiExceptions.AuthenticationFailure();
        }
        // Social accounts without a local password can only sign in via their provider.
        if (!user.hasPassword()) {
            throw new ApiExceptions.SocialLoginRequired(user.getProvider().name());
        }
        // Generic failure; do not reveal which field was wrong.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiExceptions.AuthenticationFailure();
        }
        return issueTokens(user);
    }

    /**
     * Signs in with a Google ID token. Finds an existing account by email or
     * creates a new password-less GOOGLE account (auto-generated username).
     */
    @Transactional
    public AuthDtos.TokenResponse loginWithGoogle(String idToken) {
        GoogleTokenVerifier.GoogleProfile profile = googleTokenVerifier.verify(idToken);

        User user = userRepository.findByEmail(profile.email()).orElse(null);
        if (user == null) {
            user = new User(
                    profile.name(),
                    generateUniqueUsername(profile.email()),
                    profile.email(),
                    trustProperties.getInitialScore(),
                    AuthProvider.GOOGLE,
                    profile.subject());
            user = userRepository.save(user);
        } else if (user.getProviderSubject() == null && user.getProvider() != AuthProvider.LOCAL) {
            // Backfill subject for an existing social account.
            user.setProviderSubject(profile.subject());
            userRepository.save(user);
        }
        // Existing LOCAL users signing in with Google (same email) are allowed in;
        // their password still works too. Provider stays LOCAL so login options are unchanged.
        return issueTokens(user);
    }

    /**
     * Facebook login — prepared for later. Facebook requires an App id/secret and
     * server-side token validation against the Graph API. Until those are added,
     * this returns a clear "not configured" error. The find-or-create flow will
     * mirror {@link #loginWithGoogle} using AuthProvider.FACEBOOK.
     */
    @Transactional
    public AuthDtos.TokenResponse loginWithFacebook(String accessToken) {
        throw new ApiExceptions.ValidationException(
                "Facebook sign-in is not configured yet. Set FACEBOOK_APP_ID/FACEBOOK_APP_SECRET to enable it.");
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenService.validate(refreshToken);
        if (stored == null) {
            throw new ApiExceptions.AuthenticationFailure();
        }
        refreshTokenService.revoke(refreshToken);
        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * Sets (or changes) a local password for the current user. Enables normal
     * username/password login for social accounts.
     */
    @Transactional
    public void setPassword(User user, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new ApiExceptions.ValidationException("Password must be at least 8 characters");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private String generateUniqueUsername(String email) {
        String base = email.split("@")[0]
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "");
        if (base.isBlank()) {
            base = "user";
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private AuthDtos.TokenResponse issueTokens(User user) {
        String access = jwtService.generateAccessToken(user.getId(), user.getUsername());
        RefreshToken refresh = refreshTokenService.issue(user);
        return new AuthDtos.TokenResponse(access, refresh.getToken());
    }
}
