package com.webstore.backend;

import com.webstore.backend.model.Administrador;
import com.webstore.backend.model.Cliente;
import com.webstore.backend.model.Categoria;
import com.webstore.backend.model.Produto;
import com.webstore.backend.model.ProdutoTamanho;
import com.webstore.backend.model.Tamanho;
import com.webstore.backend.repository.ProdutoRepository;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ProdutoIntegrationTests {

    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @BeforeEach
    void limparBase() {
        produtoRepository.deleteAll();
        usuarioRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void clienteAutenticadoDeveVerSomenteProdutosAtivos() throws Exception {
        produtoRepository.save(novoProduto("Vestido Floral", "129.90", true));
        produtoRepository.save(novoProduto("Produto Inativo", "59.90", false));
        usuarioRepository.save(new Cliente("Cliente Teste", "cliente@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("cliente@test.com", "123456");

        String responseBody = mockMvc.perform(get("/api/produtos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody)
            .contains("Vestido Floral")
            .doesNotContain("Produto Inativo");
    }

    @Test
    void adminDeveCriarListarEExcluirProduto() throws Exception {
        usuarioRepository.save(new Administrador("Admin Teste", "admin@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("admin@test.com", "123456");

        mockMvc.perform(post("/api/admin/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"nome\":\"Saia Midi\"," +
                                "\"preco\":\"109,90\"," +
                                "\"destaque\":\"Favorito\"," +
                                "\"imagem\":\"https://img.example/saia.jpg\"," +
                    "\"descricao\":\"Produto de teste\"," +
                    "\"categoria\":\"Saia\"," +
                    "\"tamanhos\":[{" +
                    "\"tamanho\":\"P\"," +
                    "\"quantidade\":10},{" +
                    "\"tamanho\":\"M\"," +
                    "\"quantidade\":8}]}"))
                .andExpect(status().isCreated());

        String listBody = mockMvc.perform(get("/api/admin/produtos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(listBody)
            .contains("Saia Midi")
            .contains("Saia")
            .contains("P")
            .contains("M");

        Long produtoId = produtoRepository.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(delete("/api/admin/produtos/" + produtoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(produtoRepository.findById(produtoId)).isEmpty();
    }

    @Test
    void adminDeveAtualizarCategoriaDoProduto() throws Exception {
        usuarioRepository.save(new Administrador("Admin Teste", "admin@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("admin@test.com", "123456");

        Produto produto = produtoRepository.save(novoProduto("Camisa de Linho", "189.90", true));

        String responseBody = mockMvc.perform(put("/api/admin/produtos/" + produto.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"nome\":\"Camisa de Linho\"," +
                                "\"preco\":\"189,90\"," +
                                "\"destaque\":\"Destaque\"," +
                                "\"imagem\":\"https://img.example/camisa.jpg\"," +
                                "\"descricao\":\"Produto atualizado\"," +
                                "\"categoria\":\"Camisa\"," +
                                "\"tamanhos\":[{" +
                                "\"tamanho\":\"P\"," +
                                "\"quantidade\":4}]}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Produto atualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertThat(atualizado.getCategoria()).isEqualTo(Categoria.CAMISA);
        assertThat(responseBody)
            .contains("Camisa")
            .contains("Produto atualizado");
    }

    @Test
    void deveExporOpcoesDeCategoriaETamanho() throws Exception {
        String responseBody = mockMvc.perform(get("/api/produtos/opcoes"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody)
            .contains("categorias")
            .contains("tamanhos")
            .contains("Vestido")
            .contains("Tamanho Único");
    }

    @Test
    void cadastroComCategoriaInvalidaDeveFalhar() throws Exception {
        usuarioRepository.save(new Administrador("Admin Teste", "admin@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("admin@test.com", "123456");

        mockMvc.perform(post("/api/admin/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"nome\":\"Produto X\"," +
                                "\"preco\":\"109,90\"," +
                                "\"destaque\":\"Favorito\"," +
                                "\"categoria\":\"NaoExiste\"," +
                                "\"tamanhos\":[{" +
                                "\"tamanho\":\"P\"," +
                                "\"quantidade\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastroComPrecoNegativoDeveFalhar() throws Exception {
        usuarioRepository.save(new Administrador("Admin Teste", "admin@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("admin@test.com", "123456");

        mockMvc.perform(post("/api/admin/produtos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"nome\":\"Produto com preço inválido\"," +
                                "\"preco\":\"-10,00\"," +
                                "\"categoria\":\"Vestido\"," +
                                "\"tamanhos\":[{" +
                                "\"tamanho\":\"P\"," +
                                "\"quantidade\":1}]}"))
                .andExpect(status().isBadRequest());

        assertThat(produtoRepository.count()).isZero();
    }

    @Test
    void atualizacaoComPrecoNegativoDeveFalharEPreservarPrecoAnterior() throws Exception {
        usuarioRepository.save(new Administrador("Admin Teste", "admin@test.com", "11999999999", passwordEncoder.encode("123456")));
        String token = obterToken("admin@test.com", "123456");
        Produto produto = produtoRepository.save(novoProduto("Vestido", "99.90", true));

        mockMvc.perform(put("/api/admin/produtos/" + produto.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"nome\":\"Vestido\"," +
                                "\"preco\":\"-1,00\"," +
                                "\"categoria\":\"Vestido\"," +
                                "\"tamanhos\":[{" +
                                "\"tamanho\":\"P\"," +
                                "\"quantidade\":1}]}"))
                .andExpect(status().isBadRequest());

        Produto naoAtualizado = produtoRepository.findById(produto.getId()).orElseThrow();
        assertThat(naoAtualizado.getPreco()).isEqualByComparingTo("99.90");
    }

    private Produto novoProduto(String nome, String preco, boolean ativo) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setPreco(new BigDecimal(preco));
        produto.setDestaque("Destaque");
        produto.setImagem("");
        produto.setDescricao("");
        produto.setAtivo(ativo);
        produto.setCategoria(Categoria.VESTIDO);

        ProdutoTamanho tamanho = new ProdutoTamanho();
        tamanho.setProduto(produto);
        tamanho.setTamanho(Tamanho.P);
        tamanho.setQuantidade(1);
        produto.getTamanhos().add(tamanho);

        return produto;
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

