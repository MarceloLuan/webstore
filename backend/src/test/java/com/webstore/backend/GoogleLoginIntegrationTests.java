package com.webstore.backend;

import com.webstore.backend.model.*;
import com.webstore.backend.repository.*;
import com.webstore.backend.security.*;
import com.webstore.backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:google_login_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional
class GoogleLoginIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy filter;
    @Autowired UsuarioRepository usuarios;
    @Autowired IdentidadeExternaRepository identidades;
    @Autowired RecuperacaoSenhaTokenRepository recuperacoes;
    @Autowired RecuperacaoSenhaService recuperacao;
    @Autowired JwtUtil jwtUtil;
    @Autowired PasswordEncoder encoder;
    @MockitoBean GoogleCredentialVerifier verifier;
    @MockitoBean EmailConfirmacaoMailer mailer;
    @MockitoSpyBean RateLimitService limits;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
    }
    private Jwt claims(String sub, String email) {
        return Jwt.withTokenValue("verified").header("alg", "RS256").subject(sub)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("email", email).claim("email_verified", true).claim("name", "Cliente Google").build();
    }
    private String login() throws Exception {
        String body = mvc.perform(post("/api/login/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"test-credential\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body.split("\"token\":\"")[1].split("\"")[0];
    }
    @Test void createsPasswordlessClientAndRevokesWebstoreSessionOnLogout() throws Exception {
        when(verifier.verify("test-credential")).thenReturn(claims("sub-1", "new@gmail.com"));
        String token = login();
        var user = usuarios.findByEmail("new@gmail.com").orElseThrow();
        assertInstanceOf(Cliente.class, user);
        assertNull(user.getSenha());
        assertNull(user.getTelefone());
        assertEquals(user.getId().longValue(), jwtUtil.extractUserId(token));
        assertEquals(1, identidades.count());
        login();
        assertEquals(1, identidades.count());
        verify(limits).verificarCadastro("127.0.0.1");
        mvc.perform(get("/api/minha-conta").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.possuiSenhaLocal").value(false));
        mvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/minha-conta").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
    @Test void doesNotLinkMatchingEmailAndLocalPasswordStillWorks() throws Exception {
        var local = usuarios.save(new Cliente("Local", "local@gmail.com", "11999999999", encoder.encode("Senha123")));
        when(verifier.verify("credential")).thenReturn(claims("new-sub", local.getEmail()));
        mvc.perform(post("/api/login/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"credential\"}"))
                .andExpect(status().isConflict());
        assertEquals(0, identidades.count());
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"local@gmail.com\",\"senha\":\"Senha123\"}" )).andExpect(status().isOk());
    }
    @Test void existingSubjectPreservesAdminAndLocalEmailEvenWhenGoogleEmailChanges() throws Exception {
        var admin = usuarios.save(new Administrador("Admin", "admin@example.com", "11999999999", encoder.encode("Senha123")));
        identidades.save(new IdentidadeExterna(admin, IdentidadeExterna.Provedor.GOOGLE, "admin-sub"));
        when(verifier.verify("test-credential")).thenReturn(claims("admin-sub", "changed@gmail.com"));
        String token = login();
        assertEquals(admin.getId().longValue(), jwtUtil.extractUserId(token));
        assertEquals("admin@example.com", usuarios.findById(admin.getId()).orElseThrow().getEmail());
        mvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        admin.setAtivo(false);
        usuarios.saveAndFlush(admin);
        mvc.perform(post("/api/login/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"test-credential\"}" )).andExpect(status().isForbidden());
        mvc.perform(get("/api/minha-conta").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
    @Test void invalidCredentialCannotCreateAccount() throws Exception {
        when(verifier.verify("invalid")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(post("/api/login/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"invalid\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(0, identidades.count());
    }
    @Test void externalOnlyAccountCannotResetOrLoginWithPassword() throws Exception {
        when(verifier.verify("test-credential")).thenReturn(claims("sub-reset", "external@gmail.com"));
        login();
        recuperacao.solicitar("external@gmail.com");
        assertEquals(0, recuperacoes.count());
        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"external@gmail.com\",\"senha\":\"any-password\"}" )).andExpect(status().isUnauthorized());
    }
    @Test void thirdPartyEmailRequiresWebstoreConfirmationBeforeSession() throws Exception {
        when(verifier.verify("third-party")).thenReturn(claims("sub-third", "person@example.com"));
        mvc.perform(post("/api/login/google").contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"third-party\"}"))
                .andExpect(status().isForbidden());
        var user = usuarios.findByEmail("person@example.com").orElseThrow();
        assertFalse(user.getEmailConfirmado());
        verify(mailer).enviar(eq("person@example.com"), anyString());
    }
}
