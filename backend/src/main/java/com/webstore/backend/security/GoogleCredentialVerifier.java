package com.webstore.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Component
public class GoogleCredentialVerifier {
    private final JwtDecoder decoder;
    private final boolean enabled;

    public GoogleCredentialVerifier(@Value("${GOOGLE_CLIENT_ID:}") String clientId) {
        this.enabled = !clientId.isBlank();
        NimbusJwtDecoder google = NimbusJwtDecoder
                .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        configure(google, clientId);
        this.decoder = google;
    }

    // The same claim policy is exercised with locally signed RSA tokens in tests.
    static void configure(NimbusJwtDecoder decoder, String clientId) {
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            String issuer = jwt.getClaimAsString("iss");
            String azp = jwt.getClaimAsString("azp");
            boolean valid = issuer != null && Set.of("https://accounts.google.com", "accounts.google.com").contains(issuer)
                    && !clientId.isBlank() && jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                    && (azp == null || clientId.equals(azp))
                    && jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(Instant.now())
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank() && jwt.getSubject().length() <= 255;
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO), claims));
    }

    public Jwt verify(String credential) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Login Google não configurado.");
        if (credential == null || credential.isBlank() || credential.length() > 16384) throw invalid();
        try {
            return decoder.decode(credential);
        } catch (JwtException | IllegalArgumentException failure) {
            // Never include the credential or the decoder exception in logs/responses.
            throw invalid();
        }
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial Google inválida ou expirada.");
    }
}
