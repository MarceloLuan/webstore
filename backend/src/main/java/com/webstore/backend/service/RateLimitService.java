package com.webstore.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    private static final Logger LOG = LoggerFactory.getLogger(RateLimitService.class);
    private static final int MAX_CHAVES = 50_000;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final byte[] logSecret;
    private final Politica loginIp;
    private final Politica loginConta;
    private final Politica cadastroIp;
    private final Politica recuperacaoIp;
    private final Politica recuperacaoConta;
    private final Politica confirmacaoIp;
    private final Politica confirmacaoConta;

    public RateLimitService(
            @Value("${security.rate-limit.enabled:true}") boolean enabled,
            @Value("${jwt.secret}") String logSecret,
            @Value("${security.rate-limit.login.ip.max:20}") int loginIpMax,
            @Value("${security.rate-limit.login.conta.max:8}") int loginContaMax,
            @Value("${security.rate-limit.login.janela-minutos:10}") long loginMinutos,
            @Value("${security.rate-limit.cadastro.ip.max:5}") int cadastroIpMax,
            @Value("${security.rate-limit.cadastro.janela-minutos:60}") long cadastroMinutos,
            @Value("${security.rate-limit.recuperacao.ip.max:5}") int recuperacaoIpMax,
            @Value("${security.rate-limit.recuperacao.conta.max:3}") int recuperacaoContaMax,
            @Value("${security.rate-limit.recuperacao.janela-minutos:15}") long recuperacaoMinutos,
            @Value("${security.rate-limit.confirmacao.ip.max:5}") int confirmacaoIpMax,
            @Value("${security.rate-limit.confirmacao.conta.max:3}") int confirmacaoContaMax,
            @Value("${security.rate-limit.confirmacao.janela-minutos:15}") long confirmacaoMinutos) {
        this.enabled = enabled;
        this.logSecret = logSecret.getBytes(StandardCharsets.UTF_8);
        this.loginIp = politica(loginIpMax, loginMinutos);
        this.loginConta = politica(loginContaMax, loginMinutos);
        this.cadastroIp = politica(cadastroIpMax, cadastroMinutos);
        this.recuperacaoIp = politica(recuperacaoIpMax, recuperacaoMinutos);
        this.recuperacaoConta = politica(recuperacaoContaMax, recuperacaoMinutos);
        this.confirmacaoIp = politica(confirmacaoIpMax, confirmacaoMinutos);
        this.confirmacaoConta = politica(confirmacaoContaMax, confirmacaoMinutos);
    }

    public void verificarLogin(String ip, String email) {
        verificar("login", "ip", ip, loginIp);
        verificar("login", "conta", normalizarEmail(email), loginConta);
    }

    public void verificarCadastro(String ip) {
        verificar("cadastro", "ip", ip, cadastroIp);
    }

    public void verificarGoogleIp(String ip) {
        verificar("login", "ip", ip, loginIp);
    }

    public void verificarGoogleIdentidade(String subject) {
        verificar("login_google", "identidade", subject, loginConta);
    }

    public void verificarRecuperacao(String ip, String email) {
        verificar("recuperacao_senha", "ip", ip, recuperacaoIp);
        verificar("recuperacao_senha", "conta", normalizarEmail(email), recuperacaoConta);
    }

    public void verificarReenvioConfirmacao(String ip, String email) {
        verificar("reenvio_confirmacao", "ip", ip, confirmacaoIp);
        verificar("reenvio_confirmacao", "conta", normalizarEmail(email), confirmacaoConta);
    }

    private void verificar(String acao, String dimensao, String valor, Politica politica) {
        if (!enabled) return;
        Instant agora = Instant.now();
        String identificador = fingerprint(dimensao, valor);
        String chave = acao + ':' + dimensao + ':' + identificador;
        limparSeNecessario(agora);
        if (janelas.size() >= MAX_CHAVES && !janelas.containsKey(chave)) chave = acao + ':' + dimensao + ":overflow";
        Janela janela = janelas.computeIfAbsent(chave, ignorada -> new Janela());
        synchronized (janela) {
            Instant inicio = agora.minus(politica.janela());
            while (!janela.tentativas.isEmpty() && !janela.tentativas.peekFirst().isAfter(inicio)) {
                janela.tentativas.removeFirst();
            }
            if (janela.tentativas.size() >= politica.maximo()) {
                long espera = Math.max(1, Duration.between(agora,
                        janela.tentativas.peekFirst().plus(politica.janela())).toSeconds());
                if (janela.ultimoLog == null || janela.ultimoLog.isBefore(agora.minusSeconds(60))) {
                    LOG.warn("security_event=rate_limit_exceeded action={} dimension={} identifier={}",
                            acao, dimensao, identificador);
                    janela.ultimoLog = agora;
                }
                throw new RateLimitException(espera);
            }
            janela.tentativas.addLast(agora);
        }
    }

    private void limparSeNecessario(Instant agora) {
        if (janelas.size() < 10_000) return;
        Instant limite = agora.minus(Duration.ofHours(2));
        janelas.entrySet().removeIf(entry -> {
            Janela janela = entry.getValue();
            synchronized (janela) {
                return janela.tentativas.isEmpty() || janela.tentativas.peekLast().isBefore(limite);
            }
        });
    }

    private String fingerprint(String dimensao, String valor) {
        String seguro = valor == null || valor.isBlank() ? "desconhecido" : valor;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(logSecret, "HmacSHA256"));
            byte[] hash = mac.doFinal((dimensao + ':' + seguro).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (Exception erro) {
            throw new IllegalStateException("Não foi possível proteger os identificadores do limitador.", erro);
        }
    }

    private String normalizarEmail(String email) {
        return email == null ? "desconhecido" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static Politica politica(int maximo, long minutos) {
        return new Politica(Math.max(1, maximo), Duration.ofMinutes(Math.max(1, minutos)));
    }

    private record Politica(int maximo, Duration janela) { }
    private static final class Janela {
        private final ArrayDeque<Instant> tentativas = new ArrayDeque<>();
        private Instant ultimoLog;
    }
}
