package com.webstore.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTests {
    @Test
    void rejeitaTokenDeVersaoAnteriorDaSessao() {
        JwtUtil jwt = new JwtUtil("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 60_000);
        jwt.init();
        var usuario = User.withUsername("cliente@exemplo.com").password("hash").roles("CLIENTE").build();
        String token = jwt.generateToken(usuario, 42L, 3L);

        assertEquals(42L, jwt.extractUserId(token));
        assertTrue(jwt.validateToken(token, 42L, 3L));
        assertFalse(jwt.validateToken(token, 42L, 4L));
        assertFalse(jwt.validateToken(token, 43L, 3L));
    }
}
