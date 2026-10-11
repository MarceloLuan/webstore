package com.webstore.backend.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTests {
    @Test
    void limitaTemporariamentePorIpEInformaQuandoTentarNovamente() {
        RateLimitService limiter = limiter(2);
        limiter.verificarCadastro("192.0.2.10");
        limiter.verificarCadastro("192.0.2.10");

        RateLimitException erro = assertThrows(RateLimitException.class,
                () -> limiter.verificarCadastro("192.0.2.10"));

        assertTrue(erro.getRetryAfterSeconds() > 0);
        assertTrue(erro.getRetryAfterSeconds() <= 3600);
        assertDoesNotThrow(() -> limiter.verificarCadastro("192.0.2.11"));
    }

    @Test
    void limitaContaMesmoQuandoOrigemMuda() {
        RateLimitService limiter = limiter(2);
        limiter.verificarRecuperacao("192.0.2.1", "cliente@exemplo.com");
        limiter.verificarRecuperacao("192.0.2.2", " CLIENTE@EXEMPLO.COM ");

        assertThrows(RateLimitException.class,
                () -> limiter.verificarRecuperacao("192.0.2.3", "cliente@exemplo.com"));
        assertDoesNotThrow(() -> limiter.verificarRecuperacao("192.0.2.3", "outra@exemplo.com"));
    }

    @Test
    void googleCompartilhaLimiteDeIpEMantemLimitePorIdentidade() {
        var limiter = limiter(2);
        limiter.verificarLogin("192.0.2.10", "local@example.com");
        limiter.verificarGoogleIp("192.0.2.10");
        assertThrows(RateLimitException.class, () -> limiter.verificarGoogleIp("192.0.2.10"));
        limiter.verificarGoogleIdentidade("sub-1");
        limiter.verificarGoogleIdentidade("sub-1");
        assertThrows(RateLimitException.class, () -> limiter.verificarGoogleIdentidade("sub-1"));
        assertDoesNotThrow(() -> limiter.verificarGoogleIdentidade("sub-2"));
    }

    private RateLimitService limiter(int maximo) {
        return new RateLimitService(true, "segredo-de-teste-para-identificadores",
                maximo, maximo, 10,
                maximo, 60,
                maximo, maximo, 15,
                maximo, maximo, 15);
    }
}
