package com.webstore.backend;

import com.webstore.backend.service.frete.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class FretePorFaixaCepTests {
    private static CalculadoraFrete.Solicitacao destino(String cep) {
        return new CalculadoraFrete.Solicitacao(cep, new BigDecimal("100.00"), List.of());
    }
    @Test void limitesDaCoberturaEValoresSaoDeterminadosPeloServidor() {
        var calc = new FretePorFaixaCep("80000001-86124999:10.00:2;01000000-19999999:30.00:5", "80000001-86124999;01000000-19999999");
        for (String cep : List.of("80000001", "86124999")) {
            assertThat(calc.calcular(destino(cep)).valor()).isEqualByComparingTo("10.00");
            assertThat(calc.calcular(destino(cep)).diasUteis()).isEqualTo(2);
        }
        for (String cep : List.of("01000000", "19999999")) assertThat(calc.calcular(destino(cep)).valor()).isEqualByComparingTo("30.00");
        for (String cep : List.of("80000000", "86125000", "00999999", "20000000")) {
            assertThatThrownBy(() -> calc.calcular(destino(cep))).isInstanceOfSatisfying(FreteException.class,
                    e -> assertThat(e.getCodigo()).isEqualTo("REGIAO_NAO_ATENDIDA"));
        }
    }
    @Test void faltaDeTarifaNaoViraFreteGratis() {
        var calc = new FretePorFaixaCep("", "01000000-19999999");
        assertThatThrownBy(() -> calc.calcular(destino("01310100"))).isInstanceOfSatisfying(FreteException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("FRETE_INDISPONIVEL"));
        assertThatThrownBy(() -> calc.calcular(destino("80000001"))).isInstanceOfSatisfying(FreteException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("REGIAO_NAO_ATENDIDA"));
    }
    @Test void cepInvalidoENaoNumericoSaoRejeitados() {
        var calc = new FretePorFaixaCep("", "01000000-19999999");
        for (String cep : List.of("00000000", "abc", "123", "123456789")) {
            assertThatThrownBy(() -> calc.calcular(destino(cep))).isInstanceOfSatisfying(FreteException.class,
                    e -> assertThat(e.getCodigo()).isEqualTo("CEP_INVALIDO"));
        }
    }
    @Test void configuracaoAmbiguaOuNegativaNaoInicializa() {
        for (String regras : List.of("01000000-19999999:-1:3", "01000000-19999999:10.999:3",
                "19999999-01000000:10:3", "01000000-19999999:10:3;10000000-15000000:20:5")) {
            assertThatThrownBy(() -> new FretePorFaixaCep(regras, "01000000-19999999")).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
