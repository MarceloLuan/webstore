package com.webstore.backend.service.frete;

import com.webstore.backend.model.*;
import com.webstore.backend.repository.CotacaoFreteRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Service
public class FreteService {
    private final CalculadoraFrete calculadora;
    private final CotacaoFreteRepository cotacoes;
    private final Clock clock;
    private final Duration validade;
    public FreteService(CalculadoraFrete calculadora, CotacaoFreteRepository cotacoes, Clock clock,
                        @Value("${frete.cotacao-minutos:15}") long minutos) {
        if (minutos < 1) throw new IllegalArgumentException("Validade da cotação deve ser positiva.");
        this.calculadora = calculadora; this.cotacoes = cotacoes; this.clock = clock; this.validade = Duration.ofMinutes(minutos);
    }
    // Invocado fora de transação/locks de estoque, permitindo um futuro adaptador HTTP.
    public CotacaoFrete cotar(Pedido snapshot, String fingerprint) {
        BigDecimal valor = new BigDecimal("0.00");
        Integer dias = null;
        String prazo = "Retirada na loja: combine o dia e horário com a equipe.";
        String origem = "RETIRADA";
        if (snapshot.getModalidade() == ModalidadeRecebimento.ENTREGA) {
            CalculadoraFrete.Resultado resultado;
            try {
                resultado = calculadora.calcular(new CalculadoraFrete.Solicitacao(snapshot.getEnderecoEntrega().cep(), snapshot.getTotal(),
                        snapshot.getItens().stream().map(i -> new CalculadoraFrete.Item(i.getProdutoTamanho().getId(), i.getQuantidade(), i.getPrecoUnitario())).toList()));
                if (resultado == null || resultado.valor() == null || resultado.valor().signum() < 0 || resultado.valor().scale() > 2
                        || resultado.diasUteis() < 0 || resultado.origem() == null || resultado.origem().length() > 60) throw new IllegalStateException();
            } catch (FreteException e) { throw e; }
            catch (RuntimeException e) { throw new FreteException(HttpStatus.SERVICE_UNAVAILABLE, "FRETE_INDISPONIVEL", "Não foi possível calcular o frete. Tente novamente mais tarde."); }
            valor = resultado.valor(); dias = resultado.diasUteis(); origem = resultado.origem();
            prazo = "Até " + dias + " dia(s) útil(eis) após a confirmação do pagamento.";
        }
        return cotacoes.save(new CotacaoFrete(snapshot.getCliente().getId(), fingerprint, snapshot.getModalidade(), snapshot.getTotal(),
                valor, dias, prazo, origem, clock.instant().plus(validade)));
    }
    public CotacaoFrete validar(UUID id, Long clienteId, String fingerprint, boolean permitirExpirada) {
        if (id == null) throw new FreteException(HttpStatus.BAD_REQUEST, "COTACAO_OBRIGATORIA", "Calcule e confira o frete antes de pagar.");
        CotacaoFrete c = cotacoes.findById(id).filter(q -> q.getClienteId().equals(clienteId))
                .orElseThrow(() -> new FreteException(HttpStatus.NOT_FOUND, "COTACAO_INVALIDA", "Cotação não encontrada."));
        if (!c.getFingerprint().equals(fingerprint)) throw new FreteException(HttpStatus.CONFLICT, "COTACAO_DESATUALIZADA", "O carrinho ou endereço mudou. Calcule novamente antes de pagar.");
        if (!permitirExpirada && !c.getExpiraEm().isAfter(clock.instant())) throw new FreteException(HttpStatus.CONFLICT, "COTACAO_EXPIRADA", "A cotação expirou. Calcule novamente antes de pagar.");
        return c;
    }
}
