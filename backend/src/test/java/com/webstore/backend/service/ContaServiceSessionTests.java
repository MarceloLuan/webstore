package com.webstore.backend.service;

import com.webstore.backend.controller.dto.AtualizarContaRequest;
import com.webstore.backend.model.Cliente;
import com.webstore.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaServiceSessionTests {
    private static final String EMAIL = "cliente@exemplo.com";

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logoutIncrementaVersaoDaSessao() {
        var contexto = contexto();
        contexto.service.encerrarSessoes();
        assertEquals(1L, contexto.usuario.getVersaoSessao());
        verify(contexto.usuarios).save(contexto.usuario);
    }

    @Test
    void alteracaoDeSenhaIncrementaVersaoDaSessao() {
        var contexto = contexto();
        when(contexto.encoder.matches("SenhaAtual123", contexto.usuario.getSenha())).thenReturn(true);
        when(contexto.encoder.encode("NovaSenha123")).thenReturn("novo-hash");
        AtualizarContaRequest request = new AtualizarContaRequest();
        request.setNome("Cliente Teste"); request.setEmail(EMAIL); request.setTelefone("11999999999");
        request.setSenhaAtual("SenhaAtual123"); request.setSenha("NovaSenha123"); request.setConfirmacaoSenha("NovaSenha123");

        contexto.service.atualizarConta(request);

        assertEquals("novo-hash", contexto.usuario.getSenha());
        assertEquals(1L, contexto.usuario.getVersaoSessao());
    }

    @Test
    void inativacaoIncrementaVersaoDaSessao() {
        var contexto = contexto();
        contexto.service.deletarConta();
        assertFalse(contexto.usuario.getAtivo());
        assertEquals(1L, contexto.usuario.getVersaoSessao());
    }

    private Contexto contexto() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Cliente usuario = new Cliente("Cliente Teste", EMAIL, "11999999999", "hash-atual");
        usuario.setId(10L);
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(usuarios.save(usuario)).thenReturn(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null, java.util.List.of()));
        return new Contexto(new ContaService(usuarios, encoder), usuarios, encoder, usuario);
    }

    private record Contexto(ContaService service, UsuarioRepository usuarios,
                            PasswordEncoder encoder, Cliente usuario) { }
}
