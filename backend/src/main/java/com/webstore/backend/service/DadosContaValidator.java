package com.webstore.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

final class DadosContaValidator {
    static final int NOME_MAXIMO = 120;
    static final int EMAIL_MAXIMO = 254;
    static final int TELEFONE_MAXIMO = 20;
    static final int SENHA_MINIMA = 8;
    static final int SENHA_MAXIMA_BYTES = 72;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TELEFONE_CARACTERES = Pattern.compile("^[0-9() +.-]+$");

    private DadosContaValidator() {
    }

    static String nome(String valor) {
        String normalizado = obrigatorio(valor, "Nome");
        if (normalizado.length() > NOME_MAXIMO) erro("O nome deve ter no máximo " + NOME_MAXIMO + " caracteres.");
        return normalizado;
    }

    static String email(String valor) {
        String normalizado = obrigatorio(valor, "E-mail").toLowerCase(Locale.ROOT);
        if (normalizado.length() > EMAIL_MAXIMO || !EMAIL.matcher(normalizado).matches()) {
            erro("Informe um e-mail válido.");
        }
        return normalizado;
    }

    static String telefone(String valor) {
        String normalizado = obrigatorio(valor, "Telefone");
        String numeros = normalizado.replaceAll("\\D", "");
        if (normalizado.length() > TELEFONE_MAXIMO || !TELEFONE_CARACTERES.matcher(normalizado).matches()
                || numeros.length() < 10 || numeros.length() > 11) {
            erro("Informe um telefone válido, com DDD.");
        }
        return normalizado;
    }

    static void novaSenha(String valor) {
        if (!StringUtils.hasText(valor)) erro("Senha é obrigatória.");
        boolean possuiLetra = valor.codePoints().anyMatch(Character::isLetter);
        boolean possuiNumero = valor.codePoints().anyMatch(Character::isDigit);
        if (valor.length() < SENHA_MINIMA || valor.getBytes(StandardCharsets.UTF_8).length > SENHA_MAXIMA_BYTES
                || !possuiLetra || !possuiNumero) {
            erro("A senha deve ter ao menos 8 caracteres, com uma letra e um número.");
        }
    }

    static void confirmacaoSenha(String senha, String confirmacao) {
        if (!StringUtils.hasText(confirmacao)) erro("Confirme a senha.");
        if (!senha.equals(confirmacao)) erro("As senhas precisam ser iguais.");
    }

    private static String obrigatorio(String valor, String campo) {
        if (!StringUtils.hasText(valor)) erro(campo + " é obrigatório.");
        return valor.trim();
    }

    private static void erro(String mensagem) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
