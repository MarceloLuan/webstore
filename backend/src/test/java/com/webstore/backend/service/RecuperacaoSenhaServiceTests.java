package com.webstore.backend.service;

import com.webstore.backend.model.Cliente;
import com.webstore.backend.model.RecuperacaoSenhaToken;
import com.webstore.backend.repository.RecuperacaoSenhaTokenRepository;
import com.webstore.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecuperacaoSenhaServiceTests {
    @Test
    void tokenFicaComoHashSoPodeSerUsadoUmaVezEInvalidaSessoes() {
        RecuperacaoSenhaTokenRepository tokens = mock(RecuperacaoSenhaTokenRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        EmailRecuperacaoSenhaMailer mailer = mock(EmailRecuperacaoSenhaMailer.class);
        var encoder = new BCryptPasswordEncoder();
        var service = new RecuperacaoSenhaService(tokens, usuarios, encoder, mailer, 30, 60, "http://localhost:5173");
        Cliente cliente = new Cliente();
        cliente.setId(1L); cliente.setEmail("cliente@exemplo.com"); cliente.setAtivo(true); cliente.setSenha(encoder.encode("Senha123"));
        when(usuarios.findByEmailForUpdate("cliente@exemplo.com")).thenReturn(Optional.of(cliente));
        AtomicReference<RecuperacaoSenhaToken> salvo = new AtomicReference<>();
        when(tokens.save(any())).thenAnswer(inv -> { salvo.set(inv.getArgument(0)); return salvo.get(); });

        service.solicitar("cliente@exemplo.com");
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailer).enviar(eq("cliente@exemplo.com"), link.capture());
        String token = URI.create(link.getValue()).getQuery().substring("token=".length());
        assertNotEquals(token, salvo.get().getTokenHash());
        when(tokens.findForUpdateByTokenHash(any())).thenAnswer(inv ->
                Optional.of(salvo.get()).filter(t -> t.getTokenHash().equals(inv.getArgument(0))));
        when(tokens.findAllByUsuarioIdAndUtilizadoEmIsNullAndRevogadoEmIsNull(1L)).thenReturn(List.of(salvo.get()));

        service.redefinir(token, "NovaSenha123", "NovaSenha123");
        assertTrue(encoder.matches("NovaSenha123", cliente.getSenha()));
        assertEquals(1L, cliente.getVersaoSessao());
        assertNotNull(cliente.getSessoesInvalidasAntesDe());
        assertThrows(ResponseStatusException.class, () -> service.redefinir(token, "OutraSenha123", "OutraSenha123"));
    }

    @Test
    void emailDesconhecidoEIntervaloDeReenvioNaoDisparamEmailNemErro() {
        RecuperacaoSenhaTokenRepository tokens = mock(RecuperacaoSenhaTokenRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        EmailRecuperacaoSenhaMailer mailer = mock(EmailRecuperacaoSenhaMailer.class);
        var service = new RecuperacaoSenhaService(tokens, usuarios, new BCryptPasswordEncoder(), mailer, 30, 60, "http://localhost:5173");
        service.solicitar("desconhecido@exemplo.com");

        Cliente cliente = new Cliente(); cliente.setId(2L); cliente.setEmail("cliente@exemplo.com"); cliente.setAtivo(true);
        when(usuarios.findByEmailForUpdate("cliente@exemplo.com")).thenReturn(Optional.of(cliente));
        when(tokens.findFirstByUsuarioIdOrderByCriadoEmDesc(2L)).thenReturn(Optional.of(
                new RecuperacaoSenhaToken(cliente, "a".repeat(64), Instant.now(), Instant.now().plusSeconds(1800))));
        service.solicitar("cliente@exemplo.com");
        verifyNoInteractions(mailer);
    }
}
