package com.webstore.backend.controller.dto;

import com.webstore.backend.model.ModalidadeRecebimento;
import com.webstore.backend.model.EnderecoEntrega;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.Set;

public record CheckoutRequest(ModalidadeRecebimento modalidade, EnderecoRequest endereco) {
    public EnderecoEntrega validarEndereco() {
        if (modalidade == null) throw erro("Selecione entrega ou retirada.");
        if (modalidade == ModalidadeRecebimento.RETIRADA) {
            if (endereco != null) throw erro("Retirada não deve incluir endereço de entrega.");
            return null;
        }
        if (endereco == null) throw erro("Informe o endereço de entrega.");
        return endereco.validar();
    }

    private static ResponseStatusException erro(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }

    public record EnderecoRequest(String destinatario, String cep, String rua, String numero,
                                  String complemento, String bairro, String cidade, String uf) {
        private static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
                "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

        private EnderecoEntrega validar() {
            String cepValido = campo(cep, "CEP", 9, true);
            if (!cepValido.matches("[0-9]{5}-?[0-9]{3}") || cepValido.replace("-", "").equals("00000000")) {
                throw erro("Informe um CEP válido com 8 dígitos.");
            }
            String ufValida = campo(uf, "UF", 2, true).toUpperCase(Locale.ROOT);
            if (!UFS.contains(ufValida)) throw erro("Selecione uma UF válida.");
            return new EnderecoEntrega(campo(destinatario, "Destinatário", 120, true), cepValido.replace("-", ""),
                    campo(rua, "Rua", 160, true), campo(numero, "Número", 20, true),
                    campo(complemento, "Complemento", 120, false), campo(bairro, "Bairro", 100, true),
                    campo(cidade, "Cidade", 100, true), ufValida);
        }

        private static String campo(String valor, String nome, int limite, boolean obrigatorio) {
            String normalizado = valor == null ? "" : valor.strip();
            if (obrigatorio && normalizado.isBlank()) throw erro(nome + " é obrigatório.");
            if (normalizado.length() > limite || normalizado.chars().anyMatch(Character::isISOControl)) {
                throw erro(nome + " inválido ou acima do limite de " + limite + " caracteres.");
            }
            return normalizado.isEmpty() ? null : normalizado;
        }
    }
}
