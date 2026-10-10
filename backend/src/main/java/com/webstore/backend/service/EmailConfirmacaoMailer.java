package com.webstore.backend.service;

public interface EmailConfirmacaoMailer {
    void enviar(String destinatario, String linkConfirmacao);
}
