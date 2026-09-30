package com.webstore.backend.service.frete;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class FretePorFaixaCep implements CalculadoraFrete {
    private record Regra(int inicio, int fim, BigDecimal valor, int dias) {}
    private final List<Regra> regras = new ArrayList<>();
    private final List<int[]> regioes = new ArrayList<>();

    public FretePorFaixaCep(@Value("${frete.regras:}") String configuracao,
                           @Value("${frete.regioes:80000000-87999999;01000000-19999999}") String cobertura) {
        for (String faixa : cobertura.split(";")) {
            if (!faixa.trim().matches("[0-9]{8}-[0-9]{8}")) throw new IllegalArgumentException("FRETE_REGIOES deve conter faixas de CEP com 8 dígitos.");
            String[] limites = faixa.trim().split("-");
            int inicio = Integer.parseInt(limites[0]), fim = Integer.parseInt(limites[1]);
            if (inicio > fim) throw new IllegalArgumentException("Faixa invertida em FRETE_REGIOES.");
            regioes.add(new int[]{inicio, fim});
        }
        if (configuracao == null || configuracao.isBlank()) return;
        for (String trecho : configuracao.split(";", -1)) {
            String regra = trecho.trim();
            if (!regra.matches("[0-9]{8}-[0-9]{8}:[0-9]{1,8}(\\.[0-9]{1,2})?:[0-9]{1,3}")) {
                throw new IllegalArgumentException("FRETE_REGRAS deve usar CEP_INICIAL-CEP_FINAL:VALOR:DIAS_UTEIS, separado por ponto e vírgula.");
            }
            String[] partes = regra.split(":");
            String[] ceps = partes[0].split("-");
            Regra r = new Regra(Integer.parseInt(ceps[0]), Integer.parseInt(ceps[1]), new BigDecimal(partes[1]).setScale(2), Integer.parseInt(partes[2]));
            if (r.inicio() > r.fim()) throw new IllegalArgumentException("Faixa de CEP invertida em FRETE_REGRAS.");
            regras.add(r);
        }
        regras.sort(Comparator.comparingInt(Regra::inicio));
        for (int i = 1; i < regras.size(); i++) {
            if (regras.get(i).inicio() <= regras.get(i - 1).fim()) throw new IllegalArgumentException("Faixas sobrepostas em FRETE_REGRAS.");
        }
    }

    @Override public Resultado calcular(Solicitacao solicitacao) {
        if (solicitacao.cep() == null || !solicitacao.cep().matches("[0-9]{8}") || "00000000".equals(solicitacao.cep())) {
            throw new FreteException(HttpStatus.BAD_REQUEST, "CEP_INVALIDO", "Informe um CEP válido com oito dígitos.");
        }
        int cep = Integer.parseInt(solicitacao.cep());
        if (regioes.stream().noneMatch(r -> cep >= r[0] && cep <= r[1])) throw new FreteException(HttpStatus.UNPROCESSABLE_ENTITY,
                "REGIAO_NAO_ATENDIDA", "Ainda não atendemos este CEP para entrega. Você pode escolher retirada na loja.");
        if (regras.isEmpty()) throw new FreteException(HttpStatus.SERVICE_UNAVAILABLE, "FRETE_INDISPONIVEL",
                "O cálculo de entrega está indisponível. Tente novamente mais tarde ou escolha retirada na loja.");
        return regras.stream().filter(r -> cep >= r.inicio() && cep <= r.fim()).findFirst()
                .map(r -> new Resultado(r.valor(), r.dias(), "TABELA_LOCAL"))
                .orElseThrow(() -> new FreteException(HttpStatus.SERVICE_UNAVAILABLE, "FRETE_INDISPONIVEL",
                        "O cálculo de frete para este CEP ainda está indisponível. Tente mais tarde ou escolha retirada."));
    }
}
