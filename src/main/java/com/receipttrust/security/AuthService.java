package com.receipttrust.security;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.config.TrustProperties;
import com.receipttrust.user.User;
import com.receipttrust.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TrustProperties trustProperties;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       TrustProperties trustProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.trustProperties = trustProperties;
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
        User user = userRepository.findByUsername(request.username())
                .orElse(null);
        // Generic failure message; do not reveal which field was wrong.
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiExceptions.AuthenticationFailure();
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenService.validate(refreshToken);
        if (stored == null) {
            throw new ApiExceptions.AuthenticationFailure();
        }
        // Rotate: revoke the used token and issue a fresh pair.
        refreshTokenService.revoke(refreshToken);
        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthDtos.TokenResponse issueTokens(User user) {
        String access = jwtService.generateAccessToken(user.getId(), user.getUsername());
        RefreshToken refresh = refreshTokenService.issue(user);
        return new AuthDtos.TokenResponse(access, refresh.getToken());
    }
}
