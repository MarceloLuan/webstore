package com.webstore.backend.service.frete;

import java.math.BigDecimal;
import java.util.List;

/** Porta para regras locais ou um futuro adaptador de transportadora. Não faz pagamentos. */
public interface CalculadoraFrete {
    Resultado calcular(Solicitacao solicitacao);
    record Item(Long produtoTamanhoId, int quantidade, BigDecimal precoUnitario) {}
    record Solicitacao(String cep, BigDecimal mercadorias, List<Item> itens) {}
    record Resultado(BigDecimal valor, int diasUteis, String origem) {}
}
