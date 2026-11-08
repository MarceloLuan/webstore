package com.webstore.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

final class EntradaTextoValidator {
    private static final Pattern NOME = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$");
    private static final Pattern CODIGO = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    private EntradaTextoValidator() {
    }

    static String nome(String valor, String campo, int limite) {
        String normalizado = texto(valor, campo, limite, true);
        if (!NOME.matcher(normalizado).matches()) {
            erro(campo + " contém caracteres inválidos.");
        }
        return normalizado;
    }

    static String codigo(String valor, String campo, int limite) {
        String normalizado = texto(valor, campo, limite, true).toUpperCase();
        if (!CODIGO.matcher(normalizado).matches()) {
            erro(campo + " deve conter apenas letras, números, ponto, hífen ou sublinhado.");
        }
        return normalizado;
    }

    static String texto(String valor, String campo, int limite, boolean obrigatorio) {
        String normalizado = valor == null ? "" : valor.trim();
        if (obrigatorio && normalizado.isBlank()) {
            erro(campo + " é obrigatório.");
        }
        if (normalizado.length() > limite || normalizado.codePoints().anyMatch(EntradaTextoValidator::caractereInvalido)) {
            erro(campo + " contém caracteres inválidos ou excede o limite de " + limite + " caracteres.");
        }
        return normalizado.isEmpty() ? null : normalizado;
    }

    static void caracteresPermitidos(String valor, String mensagem) {
        if (valor != null && valor.codePoints().anyMatch(EntradaTextoValidator::caractereInvalido)) {
            erro(mensagem);
        }
    }

    private static boolean caractereInvalido(int codePoint) {
        int tipo = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || tipo == Character.FORMAT
                || tipo == Character.PRIVATE_USE
                || tipo == Character.SURROGATE
                || tipo == Character.OTHER_SYMBOL;
    }

    private static void erro(String mensagem) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
