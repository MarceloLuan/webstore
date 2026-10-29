package com.webstore.backend;

import com.webstore.backend.model.Administrador;
import com.webstore.backend.model.Cliente;
import com.webstore.backend.repository.UsuarioRepository;
import com.webstore.backend.repository.CarrinhoRepository;
import com.webstore.backend.repository.ItemCarrinhoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class JwtIntegrationTests {

    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CarrinhoRepository carrinhoRepository;

    @Autowired
    private ItemCarrinhoRepository itemCarrinhoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.webstore.backend.repository.RecuperacaoSenhaTokenRepository recuperacoes;

    @Autowired
    private com.webstore.backend.repository.ConfirmacaoEmailTokenRepository confirmacoes;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.webstore.backend.service.EmailConfirmacaoMailer confirmacaoMailer;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @BeforeEach
    void limparBase() {
        recuperacoes.deleteAll();
        confirmacoes.deleteAll();
        itemCarrinhoRepository.deleteAll();
        carrinhoRepository.deleteAll();
        usuarioRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void loginDeveRetornarTokenEUsuario() throws Exception {
        usuarioRepository.save(new Cliente("Cliente Teste", "cliente@test.com", "11999999999", passwordEncoder.encode("123456")));

        String responseBody = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cliente@test.com\",\"senha\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(extrairValor(responseBody, "token")).isNotBlank();
        assertThat(responseBody).contains("\"email\":\"cliente@test.com\"");
        assertThat(responseBody).contains("\"role\":\"CLIENTE\"");
    }

    @Test
    void minhaContaSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(get("/api/minha-conta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void minhaContaComTokenValidoDeveRetornar200() throws Exception {
        usuarioRepository.save(new Cliente("Cliente Teste", "cliente@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("cliente@test.com", "123456");

        mockMvc.perform(get("/api/minha-conta")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioInativoDeveInvalidarTokenCom401() throws Exception {
        Cliente cliente = usuarioRepository.save(new Cliente("Cliente Teste", "cliente@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("cliente@test.com", "123456");

        cliente.setAtivo(false);
        usuarioRepository.save(cliente);

        mockMvc.perform(get("/api/minha-conta")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutNoServidorDeveImpedirReutilizacaoDoToken() throws Exception {
        usuarioRepository.save(new Cliente("Cliente Teste", "cliente@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("cliente@test.com", "123456");

        mockMvc.perform(post("/api/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/minha-conta")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clienteNaoPodeAcessarRotaAdmin() throws Exception {
        usuarioRepository.save(new Cliente("Cliente Teste", "cliente@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("cliente@test.com", "123456");

        mockMvc.perform(get("/api/admin/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPodeAcessarRotaAdmin() throws Exception {
        usuarioRepository.save(new Administrador("Admin Teste", "admin@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("admin@test.com", "123456");

        mockMvc.perform(get("/api/admin/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void sessaoExpiradaNaoAcessaCompra() throws Exception {
        var cliente = usuarioRepository.save(new Cliente("Cliente", "expirado@test.com", "11999999999", passwordEncoder.encode("Senha123")));
        var emissor = new com.webstore.backend.security.JwtUtil(context.getEnvironment().getProperty("jwt.secret"), -60000);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(emissor, "init");
        var details = org.springframework.security.core.userdetails.User.withUsername(cliente.getEmail()).password("hash").roles("CLIENTE").build();
        String token = emissor.generateToken(details, cliente.getId(), cliente.getVersaoSessao());
        for (String endpoint : java.util.List.of("/api/carrinho", "/api/pagamentos/pedidos", "/api/minha-conta")) {
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void alterarSenhaRevogaTokenEExigeNovaSenhaNoLogin() throws Exception {
        usuarioRepository.save(new Cliente("Cliente", "troca@test.com", "11999999999", passwordEncoder.encode("Senha123")));
        String token = obterToken("troca@test.com", "Senha123");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/minha-conta")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente\",\"email\":\"troca@test.com\",\"telefone\":\"11999999999\",\"senhaAtual\":\"Senha123\",\"senha\":\"NovaSenha123\",\"confirmacaoSenha\":\"NovaSenha123\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/carrinho").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"troca@test.com\",\"senha\":\"Senha123\"}")).andExpect(status().isUnauthorized());
        assertThat(obterToken("troca@test.com", "NovaSenha123")).isNotBlank();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "{}",
        "{\"nome\":\"Cliente\",\"email\":\"invalido\",\"telefone\":\"11999999999\",\"senha\":\"Senha123\",\"confirmacaoSenha\":\"Senha123\"}",
        "{\"nome\":\"Cliente\",\"email\":\"teste@test.com\",\"telefone\":\"12\",\"senha\":\"Senha123\",\"confirmacaoSenha\":\"Senha123\"}",
        "{\"nome\":\"Cliente\",\"email\":\"teste@test.com\",\"telefone\":\"11999999999\",\"senha\":\"123\",\"confirmacaoSenha\":\"123\"}",
        "{\"nome\":\"Cliente\",\"email\":\"teste@test.com\",\"telefone\":\"11999999999\",\"senha\":\"Senha123\",\"confirmacaoSenha\":\"Diferente123\"}"
    })
    void cadastroInvalidoNaoPersisteUsuario(String payload) throws Exception {
        mockMvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void recuperacaoRevogaSessaoETokenDeUsoUnico() throws Exception {
        var cliente = usuarioRepository.save(new Cliente("Cliente", "recuperar@test.com", "11999999999", passwordEncoder.encode("Senha123")));
        String sessao = obterToken(cliente.getEmail(), "Senha123");
        String token = "token-local-exclusivo-do-teste";
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        recuperacoes.save(new com.webstore.backend.model.RecuperacaoSenhaToken(cliente, hash,
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(300)));
        String payload = "{\"token\":\"" + token + "\",\"senha\":\"NovaSenha123\",\"confirmacaoSenha\":\"NovaSenha123\"}";
        mockMvc.perform(post("/api/senha/redefinir").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/carrinho").header("Authorization", "Bearer " + sessao)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/senha/redefinir").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        assertThat(obterToken(cliente.getEmail(), "NovaSenha123")).isNotBlank();
    }

    @Test
    void cadastroConfirmacaoLoginEDuplicidade() throws Exception {
        String payload = "{\"nome\":\"Cliente Novo\",\"email\":\"novo@test.com\",\"telefone\":\"11999999999\",\"senha\":\"Senha123\",\"confirmacaoSenha\":\"Senha123\"}";
        mockMvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated());
        var usuario = usuarioRepository.findByEmail("novo@test.com").orElseThrow();
        assertThat(passwordEncoder.matches("Senha123", usuario.getSenha())).isTrue();
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"novo@test.com\",\"senha\":\"Senha123\"}")).andExpect(status().isForbidden());
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(confirmacaoMailer).enviar(org.mockito.ArgumentMatchers.eq("novo@test.com"), link.capture());
        String token = java.net.URI.create(link.getValue()).getQuery().substring("token=".length());
        mockMvc.perform(post("/api/confirmacao-email/confirmar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}")).andExpect(status().isOk());
        assertThat(obterToken("novo@test.com", "Senha123")).isNotBlank();
        mockMvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict());
        assertThat(usuarioRepository.count()).isEqualTo(1);
    }

    private String obterToken(String email, String senha) throws Exception {
        String responseBody = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return extrairValor(responseBody, "token");
    }

    private String extrairValor(String json, String chave) {
        String marcador = "\"" + chave + "\":\"";
        int inicio = json.indexOf(marcador);
        if (inicio < 0) {
            return "";
        }

        inicio += marcador.length();
        int fim = json.indexOf('"', inicio);
        return fim > inicio ? json.substring(inicio, fim) : "";
    }
}

