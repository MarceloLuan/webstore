package com.webstore.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RecuperacaoSenhaRateLimiter {
    private final Map<String, ArrayDeque<Instant>> tentativas = new ConcurrentHashMap<>();
    private final int limite;
    private final Duration janela;
    public RecuperacaoSenhaRateLimiter(@Value("${email.recuperacao.limite-ip:5}") int limite,
                                       @Value("${email.recuperacao.janela-minutos:15}") long minutos) {
        this.limite = Math.max(1, limite); this.janela = Duration.ofMinutes(Math.max(1, minutos));
    }
    public void verificar(String enderecoIp) {
        String chave = enderecoIp == null || enderecoIp.isBlank() ? "desconhecido" : enderecoIp;
        Instant agora = Instant.now();
        ArrayDeque<Instant> fila = tentativas.computeIfAbsent(chave, ignorado -> new ArrayDeque<>());
        synchronized (fila) {
            Instant inicio = agora.minus(janela);
            while (!fila.isEmpty() && fila.peekFirst().isBefore(inicio)) fila.removeFirst();
            if (fila.size() >= limite) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas solicitações. Aguarde alguns minutos e tente novamente.");
            fila.addLast(agora);
        }
        if (tentativas.size() > 10_000) tentativas.entrySet().removeIf(e -> {
            synchronized (e.getValue()) { return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(agora.minus(janela)); }
        });
    }
}
