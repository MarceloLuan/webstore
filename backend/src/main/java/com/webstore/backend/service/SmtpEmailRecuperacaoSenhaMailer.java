package com.webstore.backend.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SmtpEmailRecuperacaoSenhaMailer implements EmailRecuperacaoSenhaMailer {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remetente;
    public SmtpEmailRecuperacaoSenhaMailer(ObjectProvider<JavaMailSender> mailSender,
                                           @Value("${email.remetente:}") String remetente) {
        this.mailSender = mailSender; this.remetente = remetente;
    }
    @Override public void enviar(String destinatario, String linkRecuperacao) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente == null || remetente.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O envio de e-mail está indisponível.");
        try {
            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(remetente); mensagem.setTo(destinatario);
            mensagem.setSubject("Redefina sua senha — Dona Adah Store");
            mensagem.setText("Recebemos uma solicitação para redefinir sua senha:\n\n" + linkRecuperacao
                    + "\n\nO link expira em breve e só pode ser usado uma vez. Se não foi você, ignore este e-mail.");
            sender.send(mensagem);
        } catch (Exception erro) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível enviar o e-mail agora.");
        }
    }
}
