package com.webstore.backend;

import com.webstore.backend.model.Administrador;
import com.webstore.backend.model.Cliente;
import com.webstore.backend.repository.UsuarioRepository;
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @BeforeEach
    void limparBase() {
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
    void usuarioInativoDeveRetornar403() throws Exception {
        Cliente cliente = usuarioRepository.save(new Cliente("Cliente Teste", "cliente@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("cliente@test.com", "123456");

        cliente.setAtivo(false);
        usuarioRepository.save(cliente);

        mockMvc.perform(get("/api/minha-conta")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
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

