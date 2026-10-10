package com.webstore.backend.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SmtpEmailConfirmacaoMailer implements EmailConfirmacaoMailer {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remetente;

    public SmtpEmailConfirmacaoMailer(ObjectProvider<JavaMailSender> mailSender,
                                      @Value("${email.remetente:}") String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @Override
    public void enviar(String destinatario, String linkConfirmacao) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente == null || remetente.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O envio de confirmação está indisponível. Tente novamente mais tarde.");
        }
        try {
            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente);
            mensagem.setTo(destinatario);
            mensagem.setSubject("Confirme seu e-mail — Dona Adah Store");
            mensagem.setText("Olá! Confirme seu e-mail para ativar sua conta:\n\n" + linkConfirmacao
                    + "\n\nEste link expira em breve e só pode ser usado uma vez.");
            sender.send(mensagem);
        } catch (Exception erro) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível enviar a confirmação agora. Tente novamente mais tarde.");
        }
    }
}
