package com.receipttrust.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.config.SocialAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Verifies a Google ID token (from Google Identity Services on the frontend)
 * against Google's public keys and returns the verified profile.
 */
@Service
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private final SocialAuthProperties properties;
    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(SocialAuthProperties properties) {
        this.properties = properties;
        GoogleIdTokenVerifier v = null;
        if (properties.getGoogle().isEnabled()) {
            v = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(properties.getGoogle().getClientId()))
                    .build();
        }
        this.verifier = v;
    }

    public record GoogleProfile(String subject, String email, boolean emailVerified, String name) {
    }

    public GoogleProfile verify(String idTokenString) {
        if (!properties.getGoogle().isEnabled() || verifier == null) {
            throw new ApiExceptions.ValidationException("Google sign-in is not configured");
        }
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new ApiExceptions.AuthenticationFailure();
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            if (email == null) {
                throw new ApiExceptions.ValidationException("Google account has no email");
            }
            Boolean verified = payload.getEmailVerified();
            String name = (String) payload.get("name");
            return new GoogleProfile(payload.getSubject(), email,
                    Boolean.TRUE.equals(verified), name != null ? name : email);
        } catch (ApiExceptions.AuthenticationFailure | ApiExceptions.ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw new ApiExceptions.AuthenticationFailure();
        }
    }
}
