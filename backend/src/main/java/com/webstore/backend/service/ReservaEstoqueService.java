package com.webstore.backend.service;

import com.webstore.backend.model.*;
import com.webstore.backend.repository.PedidoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ReservaEstoqueService {
    private final EntityManager em;
    private final PedidoRepository pedidos;
    private final Clock clock;
    private final Duration prazo;
    private final TransactionTemplate transaction;

    public ReservaEstoqueService(EntityManager em, PedidoRepository pedidos, Clock clock,
            PlatformTransactionManager manager, @Value("${estoque.reserva-minutos:15}") long minutos) {
        if (minutos < 1) throw new IllegalArgumentException("O prazo de reserva deve ser positivo.");
        this.em = em; this.pedidos = pedidos; this.clock = clock; this.prazo = Duration.ofMinutes(minutos);
        transaction = new TransactionTemplate(manager);
    }

    // O chamador bloqueia primeiro o pedido. Todas as variações são bloqueadas por ID crescente.
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reservar(Pedido pedido) {
        if (ativa(pedido)) return true; // Repetição não renova o prazo.
        expirar(pedido);
        if (pedido.isEstoqueBaixado() || pedido.getTentativaConcluida() != null) return false;
        Map<ProdutoTamanho, Integer> itens = bloquearItens(pedido);
        if (itens.isEmpty() || itens.entrySet().stream().anyMatch(e -> e.getValue() < 1
                || !Boolean.TRUE.equals(e.getKey().getAtivo())
                || Boolean.FALSE.equals(e.getKey().getProduto().getAtivo())
                || e.getKey().getQuantidadeDisponivel() < e.getValue())) return false;
        itens.forEach((t, qtd) -> t.setQuantidadeReservada(t.getQuantidadeReservada() + qtd));
        pedido.setReservaStatus(ReservaStatus.ATIVA);
        pedido.setReservaExpiraEm(clock.instant().plus(prazo));
        return true;
    }

    public boolean ativa(Pedido pedido) {
        return pedido.getReservaStatus() == ReservaStatus.ATIVA && pedido.getReservaExpiraEm() != null
                && pedido.getReservaExpiraEm().isAfter(clock.instant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean consumir(Pedido pedido) {
        if (pedido.getReservaStatus() == null || pedido.getReservaStatus() == ReservaStatus.LIBERADA) {
            // Pedidos anteriores à implantação só concluem se ainda houver disponibilidade.
            if (!reservar(pedido)) return false;
        }
        expirar(pedido);
        if (!ativa(pedido)) return false;
        Map<ProdutoTamanho, Integer> itens = bloquearItens(pedido);
        if (!ativa(pedido)) {
            liberar(pedido, ReservaStatus.EXPIRADA);
            return false;
        }
        if (itens.entrySet().stream().anyMatch(e -> e.getKey().getQuantidadeReservada() < e.getValue()
                || e.getKey().getQuantidade() < e.getValue())) return false;
        itens.forEach((t, qtd) -> {
            t.setQuantidade(t.getQuantidade() - qtd);
            t.setQuantidadeReservada(t.getQuantidadeReservada() - qtd);
        });
        pedido.setReservaStatus(ReservaStatus.CONSUMIDA);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void liberar(Pedido pedido) { liberar(pedido, ReservaStatus.LIBERADA); }

    @Transactional(propagation = Propagation.MANDATORY)
    public void expirar(Pedido pedido) {
        if (pedido.getReservaStatus() == ReservaStatus.ATIVA && !ativa(pedido)) liberar(pedido, ReservaStatus.EXPIRADA);
    }

    private void liberar(Pedido pedido, ReservaStatus status) {
        if (pedido.getReservaStatus() != ReservaStatus.ATIVA) return;
        Map<ProdutoTamanho, Integer> itens = bloquearItens(pedido);
        for (var item : itens.entrySet()) {
            if (item.getKey().getQuantidadeReservada() < item.getValue()) {
                throw new IllegalStateException("Saldo de reserva inconsistente no pedido " + pedido.getId());
            }
        }
        itens.forEach((t, qtd) -> t.setQuantidadeReservada(t.getQuantidadeReservada() - qtd));
        pedido.setReservaStatus(status);
    }

    private Map<ProdutoTamanho, Integer> bloquearItens(Pedido pedido) {
        TreeMap<Long, Integer> quantidades = new TreeMap<>();
        pedido.getItens().forEach(item -> quantidades.merge(item.getProdutoTamanho().getId(), item.getQuantidade(), Integer::sum));
        Map<ProdutoTamanho, Integer> itens = new LinkedHashMap<>();
        // Flush antes de refresh preserva liberações realizadas nesta mesma transação.
        em.flush();
        quantidades.forEach((id, qtd) -> {
            ProdutoTamanho tamanho = em.find(ProdutoTamanho.class, id);
            em.refresh(tamanho, LockModeType.PESSIMISTIC_WRITE);
            itens.put(tamanho, qtd);
        });
        return itens;
    }

    public void expirarVencidas() {
        Instant agora = clock.instant();
        // Lote limitado: uma transação por pedido, seguro em várias instâncias.
        for (Long id : pedidos.findReservasVencidas(agora, PageRequest.of(0, 100))) {
            transaction.executeWithoutResult(status -> {
                pedidos.findWithItensForUpdateById(id).ifPresent(this::expirar);
            });
        }
    }
}
