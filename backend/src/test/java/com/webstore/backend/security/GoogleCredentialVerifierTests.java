package com.webstore.backend.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class GoogleCredentialVerifierTests {
    private static RSAKey key;
    private static NimbusJwtDecoder decoder;
    @BeforeAll static void setup() throws Exception {
        key = new RSAKeyGenerator(2048).generate();
        decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        GoogleCredentialVerifier.configure(decoder, "web-client");
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer("https://accounts.google.com")
                .audience("web-client").subject("google-sub")
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }
    private String sign(JWTClaimsSet.Builder claims, RSAKey signer) throws Exception {
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        token.sign(new RSASSASigner(signer));
        return token.serialize();
    }
    @Test void validSignatureAndBothGoogleIssuers() throws Exception {
        assertEquals("google-sub", decoder.decode(sign(claims(), key)).getSubject());
        assertEquals("google-sub", decoder.decode(sign(claims().issuer("accounts.google.com"), key)).getSubject());
    }
    @Test void rejectsSignatureFromAnotherKey() throws Exception {
        String token = sign(claims(), new RSAKeyGenerator(2048).generate());
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
    @Test void rejectsWrongAudienceIssuerAndAuthorizedParty() throws Exception {
        for (var invalid : new JWTClaimsSet.Builder[] {claims().audience("other-client"),
                claims().issuer("https://attacker.example"), claims().claim("azp", "other-client"),
                claims().audience((String) null)}) {
            String token = sign(invalid, key);
            assertThrows(JwtException.class, () -> decoder.decode(token));
        }
    }
    @Test void rejectsExpiredMissingExpirationAndMissingSubject() throws Exception {
        for (var invalid : new JWTClaimsSet.Builder[] {
                claims().expirationTime(Date.from(Instant.now().minusSeconds(5))),
                claims().expirationTime(null), claims().subject(null)}) {
            String token = sign(invalid, key);
            assertThrows(JwtException.class, () -> decoder.decode(token));
        }
    }
    @Test void unconfiguredLoginFailsClosedWithoutNetwork() {
        var verifier = new GoogleCredentialVerifier("");
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> verifier.verify("anything")).getStatusCode().value());
    }
}
