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
        String token = jwt.generateToken(usuario, 3L);

        assertTrue(jwt.validateToken(token, usuario, 3L));
        assertFalse(jwt.validateToken(token, usuario, 4L));
    }
}
