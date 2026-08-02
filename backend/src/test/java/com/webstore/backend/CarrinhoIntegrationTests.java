package com.webstore.backend;

import com.webstore.backend.model.Administrador;
import com.webstore.backend.model.Categoria;
import com.webstore.backend.model.Cliente;
import com.webstore.backend.model.Produto;
import com.webstore.backend.model.ProdutoTamanho;
import com.webstore.backend.model.Tamanho;
import com.webstore.backend.repository.CarrinhoRepository;
import com.webstore.backend.repository.ItemCarrinhoRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CarrinhoIntegrationTests {

    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CarrinhoRepository carrinhoRepository;

    @Autowired
    private ItemCarrinhoRepository itemCarrinhoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @BeforeEach
    void preparar() {
        itemCarrinhoRepository.deleteAll();
        carrinhoRepository.deleteAll();
        produtoRepository.deleteAll();
        usuarioRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void clienteDeveAdicionarSomarAtualizarERemoverItem() throws Exception {
        Produto produto = produtoRepository.save(novoProduto(5));
        Long variacaoId = produto.getTamanhos().get(0).getId();
        usuarioRepository.save(new Cliente(
                "Cliente Carrinho",
                "carrinho@test.com",
                "11999999999",
                passwordEncoder.encode("123456")
        ));
        String token = obterToken("carrinho@test.com", "123456");

        adicionar(token, variacaoId, 2);
        Long itemId = itemCarrinhoRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/carrinho/itens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoTamanhoId\":" + variacaoId + ",\"quantidade\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantidadeTotal").value(3))
                .andExpect(jsonPath("$.subtotal").value(299.70));

        mockMvc.perform(put("/api/carrinho/itens/" + itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeTotal").value(4))
                .andExpect(jsonPath("$.itens[0].tamanho").value("M"));

        mockMvc.perform(delete("/api/carrinho/itens/" + itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeTotal").value(0))
                .andExpect(jsonPath("$.itens").isEmpty());
    }

    @Test
    void deveImpedirQuantidadeSuperiorAoEstoque() throws Exception {
        Produto produto = produtoRepository.save(novoProduto(2));
        Long variacaoId = produto.getTamanhos().get(0).getId();
        usuarioRepository.save(new Cliente(
                "Cliente Estoque",
                "estoque@test.com",
                "11999999999",
                passwordEncoder.encode("123456")
        ));
        String token = obterToken("estoque@test.com", "123456");

        mockMvc.perform(post("/api/carrinho/itens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoTamanhoId\":" + variacaoId + ",\"quantidade\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Quantidade indisponível. Há 2 unidade(s) em estoque."));

        assertThat(carrinhoRepository.findAll())
                .allSatisfy(carrinho -> assertThat(carrinho.getItens()).isEmpty());
    }

    @Test
    void carrinhoDeveSerPrivadoParaClienteAutenticado() throws Exception {
        mockMvc.perform(get("/api/carrinho"))
                .andExpect(status().isUnauthorized());

        usuarioRepository.save(new Administrador(
                "Admin",
                "admin-carrinho@test.com",
                "11999999999",
                passwordEncoder.encode("123456")
        ));
        String tokenAdmin = obterToken("admin-carrinho@test.com", "123456");

        mockMvc.perform(get("/api/carrinho")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());
    }

    private String adicionar(String token, Long variacaoId, int quantidade) throws Exception {
        return mockMvc.perform(post("/api/carrinho/itens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoTamanhoId\":" + variacaoId + ",\"quantidade\":" + quantidade + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantidadeTotal").value(quantidade))
                .andExpect(jsonPath("$.itens[0].produtoTamanhoId").value(variacaoId))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Produto novoProduto(int estoque) {
        Produto produto = new Produto();
        produto.setCodigo("CARRINHO-001");
        produto.setNome("Produto do Carrinho");
        produto.setPreco(new BigDecimal("99.90"));
        produto.setDestaque("Destaque");
        produto.setImagem("");
        produto.setDescricao("");
        produto.setAtivo(true);
        produto.setCategoria(Categoria.CAMISETA);

        ProdutoTamanho tamanho = new ProdutoTamanho();
        tamanho.setProduto(produto);
        tamanho.setTamanho(Tamanho.M);
        tamanho.setQuantidade(estoque);
        tamanho.setAtivo(true);
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
        if (inicio < 0) return "";
        inicio += marcador.length();
        int fim = json.indexOf('"', inicio);
        return fim > inicio ? json.substring(inicio, fim) : "";
    }

}
