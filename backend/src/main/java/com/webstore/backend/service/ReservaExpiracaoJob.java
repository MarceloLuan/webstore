package com.webstore.backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "estoque.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ReservaExpiracaoJob {
    private final ReservaEstoqueService reservas;
    public ReservaExpiracaoJob(ReservaEstoqueService reservas) { this.reservas = reservas; }
    @Scheduled(fixedDelayString = "${estoque.expiracao-intervalo-ms:30000}")
    public void executar() { reservas.expirarVencidas(); }
}
