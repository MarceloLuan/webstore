package com.webstore.backend.service;

import com.webstore.backend.model.Cliente;
import com.webstore.backend.model.ConfirmacaoEmailToken;
import com.webstore.backend.repository.ConfirmacaoEmailTokenRepository;
import com.webstore.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConfirmacaoEmailServiceTests {
    @Test
    void armazenaSomenteHashEConfirmaTokenUmaUnicaVez() {
        ConfirmacaoEmailTokenRepository tokens = mock(ConfirmacaoEmailTokenRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        EmailConfirmacaoMailer mailer = mock(EmailConfirmacaoMailer.class);
        ConfirmacaoEmailService service = new ConfirmacaoEmailService(tokens, usuarios, mailer, 60, 60, "http://localhost:5173");
        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setEmail("cliente@exemplo.com");
        cliente.aguardarConfirmacaoEmail();
        AtomicReference<ConfirmacaoEmailToken> salvo = new AtomicReference<>();
        when(tokens.save(any())).thenAnswer(invocation -> {
            salvo.set(invocation.getArgument(0));
            return salvo.get();
        });

        service.enviarParaNovoCadastro(cliente);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailer).enviar(eq("cliente@exemplo.com"), captor.capture());
        String token = URI.create(captor.getValue()).getQuery().substring("token=".length());
        assertNotEquals(token, salvo.get().getTokenHash());
        when(tokens.findForUpdateByTokenHash(any())).thenAnswer(invocation ->
                Optional.of(salvo.get()).filter(t -> t.getTokenHash().equals(invocation.getArgument(0))));

        service.confirmar(token);
        assertTrue(cliente.getEmailConfirmado());
        assertThrows(ResponseStatusException.class, () -> service.confirmar(token));
    }

    @Test
    void limitaReenvioEmSequencia() {
        ConfirmacaoEmailTokenRepository tokens = mock(ConfirmacaoEmailTokenRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        EmailConfirmacaoMailer mailer = mock(EmailConfirmacaoMailer.class);
        ConfirmacaoEmailService service = new ConfirmacaoEmailService(tokens, usuarios, mailer, 60, 60, "http://localhost:5173");
        Cliente cliente = new Cliente();
        cliente.setId(2L);
        cliente.setEmail("cliente@exemplo.com");
        cliente.aguardarConfirmacaoEmail();
        ConfirmacaoEmailToken anterior = new ConfirmacaoEmailToken(cliente, "a".repeat(64), java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600));
        when(usuarios.findByEmail("cliente@exemplo.com")).thenReturn(Optional.of(cliente));
        when(tokens.findFirstByUsuarioIdOrderByCriadoEmDesc(2L)).thenReturn(Optional.of(anterior));

        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.reenviar("cliente@exemplo.com"));
        assertEquals(429, erro.getStatusCode().value());
        verifyNoInteractions(mailer);
    }
}
