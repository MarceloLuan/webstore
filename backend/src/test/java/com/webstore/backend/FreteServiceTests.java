package com.webstore.backend;

import com.webstore.backend.service.frete.*;
import com.webstore.backend.model.*;
import com.webstore.backend.repository.CotacaoFreteRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FreteServiceTests {
    @Test void indisponibilidadeDoAdaptadorNaoGravaCotacaoNemAssumeValorZero() {
        var calculadora = mock(CalculadoraFrete.class);
        var repositorio = mock(CotacaoFreteRepository.class);
        var service = new FreteService(calculadora, repositorio, Clock.systemUTC(), 15);
        Pedido pedido = new Pedido(); pedido.setTotal(new BigDecimal("100.00"));
        pedido.definirRecebimento(ModalidadeRecebimento.ENTREGA,
                new EnderecoEntrega("Teste", "01310100", "Rua", "1", null, "Bairro", "Cidade", "SP"));
        when(calculadora.calcular(any())).thenThrow(new IllegalStateException("serviço indisponível"));
        assertThatThrownBy(() -> service.cotar(pedido, "snapshot")).isInstanceOfSatisfying(FreteException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("FRETE_INDISPONIVEL"));
        verifyNoInteractions(repositorio);
    }
}
