package com.webstore.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DadosContaValidatorTests {
    @Test
    void normalizaEmailEValidaDadosPessoais() {
        assertEquals("cliente@exemplo.com", DadosContaValidator.email(" Cliente@Exemplo.com "));
        assertEquals("(43) 99999-9999", DadosContaValidator.telefone("(43) 99999-9999"));
    }

    @Test
    void rejeitaEmailETelefoneInvalidos() {
        assertThrows(ResponseStatusException.class, () -> DadosContaValidator.email("cliente-sem-dominio"));
        assertThrows(ResponseStatusException.class, () -> DadosContaValidator.telefone("123456789"));
    }

    @Test
    void rejeitaCamposAcimaDoLimite() {
        assertThrows(ResponseStatusException.class, () -> DadosContaValidator.nome("a".repeat(121)));
        assertThrows(ResponseStatusException.class, () -> DadosContaValidator.telefone("1".repeat(21)));
    }

    @Test
    void exigeSenhaComLetraNumeroEComprimentoMinimo() {
        assertThrows(ResponseStatusException.class, () -> DadosContaValidator.novaSenha("abcdefg"));
        assertThrows(ResponseStatusException.class, () -> DadosContaValidator.novaSenha("12345678"));
        DadosContaValidator.novaSenha("Senha123");
    }

    @Test
    void exigeConfirmacaoIgualASenha() {
        assertThrows(ResponseStatusException.class, () -> DadosContaValidator.confirmacaoSenha("Senha123", "Senha456"));
    }
}
