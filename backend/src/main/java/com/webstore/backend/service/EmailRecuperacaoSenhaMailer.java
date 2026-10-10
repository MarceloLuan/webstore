package com.webstore.backend.service;

public interface EmailRecuperacaoSenhaMailer {
    void enviar(String destinatario, String linkRecuperacao);
}
