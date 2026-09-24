package com.webstore.backend;

import com.webstore.backend.controller.dto.CheckoutResponse;
import com.webstore.backend.controller.dto.CheckoutRequest;
import com.webstore.backend.model.*;
import com.webstore.backend.repository.*;
import com.webstore.backend.service.PagamentoService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pagamento_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "mercadopago.access-token=token-apenas-para-http-simulado",
        "mercadopago.webhook-secret=segredo-apenas-para-http-simulado"
})
@Import(PagamentoIntegrationTests.HttpSimulado.class)
class PagamentoIntegrationTests {
    @org.springframework.test.context.DynamicPropertySource
    static void bancoDeConcorrencia(org.springframework.test.context.DynamicPropertyRegistry registry) {
        String url = System.getenv("RESERVA_TEST_PG_URL");
        if (url != null && !url.isBlank()) {
            if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/webstore_reservas_test")) {
                throw new IllegalArgumentException("Use exclusivamente o banco local descartável webstore_reservas_test.");
            }
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.datasource.username", () -> "reservas_test");
            registry.add("spring.datasource.password", () -> "");
        }
    }
    @TestConfiguration
    static class HttpSimulado {
        @Bean @Primary RestClient.Builder pagamentoBuilder() { return RestClient.builder(); }
        @Bean MockRestServiceServer pagamentoServer(RestClient.Builder builder) {
            return MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        }
        // O servidor deve ser ligado ao builder antes da construção do service.
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor prepararServidor() {
            return factory -> factory.getBeanDefinition("pagamentoService").setDependsOn("pagamentoServer");
        }
    }

    @Autowired PagamentoService service;
    @Autowired MockRestServiceServer server;
    @Autowired PedidoRepository pedidos;
    @Autowired TentativaPagamentoRepository tentativas;
    @Autowired ProdutoRepository produtos;
    @Autowired ProdutoTamanhoRepository tamanhos;
    @Autowired ClienteRepository clientes;
    @Autowired CarrinhoRepository carrinhos;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.webstore.backend.service.ReservaEstoqueService reservas;
    @Autowired com.webstore.backend.security.JwtUtil jwt;
    @Autowired org.springframework.web.context.WebApplicationContext webContext;
    @Autowired org.springframework.security.web.FilterChainProxy securityFilter;
    Long variacaoId;
    Cliente cliente;

    @BeforeEach void preparar() {
        server.reset();
        jdbc.update("update pedidos set tentativa_concluida_id = null");
        tentativas.deleteAll();
        pedidos.deleteAll();
        carrinhos.deleteAll();
        produtos.deleteAll();
        clientes.deleteAll();
        cliente = clientes.save(new Cliente("Cliente", "pagamento@test.com", "11999999999", "hash-teste"));
        autenticar(cliente.getEmail());
        Produto produto = new Produto();
        produto.setCodigo("PAG-001"); produto.setNome("Vestido");
        produto.setDestaque("Teste"); produto.setDescricao(""); produto.setImagem("");
        produto.setPreco(new BigDecimal("100.00")); produto.setCategoria(Categoria.VESTIDO); produto.setAtivo(true);
        ProdutoTamanho tamanho = new ProdutoTamanho();
        tamanho.setProduto(produto); tamanho.setTamanho(Tamanho.M); tamanho.setQuantidade(5);
        produto.getTamanhos().add(tamanho);
        produto = produtos.save(produto);
        variacaoId = produto.getTamanhos().get(0).getId();
        Carrinho carrinho = new Carrinho(); carrinho.setCliente(cliente);
        ItemCarrinho item = new ItemCarrinho(); item.setCarrinho(carrinho);
        item.setProdutoTamanho(produto.getTamanhos().get(0)); item.setQuantidade(1);
        carrinho.getItens().add(item); carrinhos.save(carrinho);
    }

    @AfterEach void limparContexto() { SecurityContextHolder.clearContext(); server.verify(); }

    @Test void recusaSeguidaDeNovaTentativaAprovadaPreservaPedidoHistoricoEEstoque() {
        CheckoutResponse checkout = novoCheckout();
        Long primeira = tentativaAtual(checkout.pedidoId());
        sincronizar("101", "rejected", "tentativa-" + primeira);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.RECUSADO);
        assertThat(estoque()).isEqualTo(5);

        esperarCheckout("segunda");
        CheckoutResponse segunda = service.tentarNovamente(checkout.pedidoId());
        server.verify(); server.reset();
        assertThat(segunda.pedidoId()).isEqualTo(checkout.pedidoId());
        Long segundaId = tentativaAtual(checkout.pedidoId());
        sincronizar("102", "approved", "tentativa-" + segundaId);
        assertThat(pedidos.count()).isEqualTo(1);
        assertThat(service.listarTentativas(checkout.pedidoId())).hasSize(2)
                .anySatisfy(t -> { assertThat(t.status()).isEqualTo(PedidoStatus.RECUSADO); assertThat(t.concluiuCompra()).isFalse(); })
                .anySatisfy(t -> { assertThat(t.status()).isEqualTo(PedidoStatus.PAGO); assertThat(t.concluiuCompra()).isTrue(); });
        assertThat(estoque()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from itens_carrinho", Long.class)).isZero();
        sincronizar("102", "approved", "tentativa-" + segundaId);
        sincronizar("101", "rejected", "tentativa-" + primeira);
        assertThat(estoque()).isEqualTo(4);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.PAGO);
    }

    @Test void mesmoCheckoutPodeGerarOutroPaymentIdDepoisDaRecusa() {
        CheckoutResponse checkout = novoCheckout();
        String referencia = "tentativa-" + tentativaAtual(checkout.pedidoId());
        sincronizar("201", "rejected", referencia);
        sincronizar("202", "approved", referencia);
        assertThat(service.listarTentativas(checkout.pedidoId())).hasSize(2);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.PAGO);
        assertThat(estoque()).isEqualTo(4);
    }

    @Test void segundaAprovacaoFicaRegistradaSemConcluirOuBaixarNovamente() {
        CheckoutResponse checkout = novoCheckout();
        String referencia = "tentativa-" + tentativaAtual(checkout.pedidoId());
        sincronizar("301", "approved", referencia);
        sincronizar("302", "approved", referencia);
        sincronizar("302", "refunded", referencia);
        assertThat(estoque()).isEqualTo(4);
        assertThat(service.listarTentativas(checkout.pedidoId())).filteredOn(t -> t.concluiuCompra()).hasSize(1);
        assertThat(service.listarTentativas(checkout.pedidoId())).filteredOn(t -> t.aprovacaoDuplicada()).hasSize(1);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.PAGO);
        assertThatThrownBy(() -> service.tentarNovamente(checkout.pedidoId())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void recusaAntigaNaoSobrescreveNovaTentativa() {
        CheckoutResponse checkout = novoCheckout();
        String primeira = "tentativa-" + tentativaAtual(checkout.pedidoId());
        sincronizar("401", "rejected", primeira);
        esperarCheckout("nova"); service.tentarNovamente(checkout.pedidoId()); server.verify(); server.reset();
        sincronizar("401", "rejected", primeira);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.AGUARDANDO_PAGAMENTO);
    }

    @Test void retomadaReutilizaCheckoutAbertoEPendenteBloqueiaNovaCobranca() {
        CheckoutResponse checkout = novoCheckout();
        assertThat(service.tentarNovamente(checkout.pedidoId())).isEqualTo(checkout);
        assertThat(tentativas.count()).isEqualTo(1);
        sincronizar("501", "pending", "tentativa-" + tentativaAtual(checkout.pedidoId()));
        assertThatThrownBy(() -> service.tentarNovamente(checkout.pedidoId())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void erroDeCheckoutPreservaTentativaParaNovaTentativa() {
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.criarCheckout(retirada())).isInstanceOf(ResponseStatusException.class);
        server.verify(); server.reset();
        Pedido pedido = pedidos.findAll().get(0);
        assertThat(service.listarTentativas(pedido.getId())).singleElement().satisfies(t -> assertThat(t.status()).isEqualTo(PedidoStatus.ERRO));
        esperarCheckout("recuperado"); service.tentarNovamente(pedido.getId()); server.verify(); server.reset();
        sincronizar("601", "approved", "tentativa-" + tentativaAtual(pedido.getId()));
        assertThat(pedidos.findById(pedido.getId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.PAGO);
    }

    @Test void clienteNaoPodeRetomarOuConsultarTentativasDeOutro() {
        CheckoutResponse checkout = novoCheckout();
        clientes.save(new Cliente("Outro", "outro@test.com", "11999999999", "hash"));
        autenticar("outro@test.com");
        assertThatThrownBy(() -> service.tentarNovamente(checkout.pedidoId())).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.listarTentativas(checkout.pedidoId())).isInstanceOf(ResponseStatusException.class);
        assertThat(tentativas.count()).isEqualTo(1);
    }

    @Test void endpointsMantemContratoEExigemDonoAutenticado() throws Exception {
        CheckoutResponse checkout = novoCheckout();
        String endpoint = "/api/pagamentos/pedidos/" + checkout.pedidoId() + "/tentativas";
        String token = jwt.generateToken(org.springframework.security.core.userdetails.User
                .withUsername(cliente.getEmail()).password("hash").roles("CLIENTE").build());
        clientes.save(new Cliente("Outro", "outro@test.com", "11999999999", "hash"));
        String outroToken = jwt.generateToken(org.springframework.security.core.userdetails.User
                .withUsername("outro@test.com").password("hash").roles("CLIENTE").build());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(securityFilter).build();
        SecurityContextHolder.clearContext();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(endpoint))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(endpoint)
                        .header("Authorization", "Bearer " + outroToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
                        .header("Authorization", "Bearer " + outroToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(endpoint)
                        .header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].status").value("AGUARDANDO_PAGAMENTO"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
                        .header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.pedidoId").value(checkout.pedidoId()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.checkoutUrl").value(checkout.checkoutUrl()));
        for (int i = 0; i < 2; i++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/pagamentos/checkout")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"modalidade\":\"RETIRADA\"}")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.pedidoId").value(checkout.pedidoId()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.preferenceId").value(checkout.preferenceId()));
        }
        assertThat(pedidos.count()).isEqualTo(1);
        assertThat(tentativas.count()).isEqualTo(1);
    }

    @Test void valorIncorretoNaoConcluiPedido() {
        CheckoutResponse checkout = novoCheckout();
        esperarPagamento("701", "approved", "tentativa-" + tentativaAtual(checkout.pedidoId()), "99.00");
        assertThatThrownBy(() -> service.processarRetorno("701")).isInstanceOf(ResponseStatusException.class);
        assertThat(estoque()).isEqualTo(5);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.AGUARDANDO_PAGAMENTO);
    }

    @Test void referenciaLegadaRecusadaAceitaNovoPagamento() {
        CheckoutResponse checkout = novoCheckout();
        tentativas.deleteAll();
        Pedido pedido = pedidos.findById(checkout.pedidoId()).orElseThrow();
        pedido.setMercadoPagoPaymentId("801"); pedido.setMercadoPagoStatus("rejected"); pedido.setStatus(PedidoStatus.RECUSADO);
        pedidos.save(pedido);
        sincronizar("802", "approved", "pedido-" + pedido.getId());
        assertThat(service.listarTentativas(pedido.getId())).hasSize(2);
        assertThat(estoque()).isEqualTo(4);
    }

    @Test void pedidoLegadoPagoNaoBaixaEstoqueNovamente() {
        CheckoutResponse checkout = novoCheckout();
        tentativas.deleteAll();
        Pedido pedido = pedidos.findById(checkout.pedidoId()).orElseThrow();
        pedido.setMercadoPagoPaymentId("811"); pedido.setMercadoPagoStatus("approved");
        pedido.setStatus(PedidoStatus.PAGO); pedido.setEstoqueBaixado(true); pedidos.save(pedido);
        sincronizar("811", "approved", "pedido-" + pedido.getId());
        sincronizar("812", "approved", "pedido-" + pedido.getId());
        assertThat(estoque()).isEqualTo(5);
        assertThat(service.listarTentativas(pedido.getId())).filteredOn(t -> t.concluiuCompra()).hasSize(1);
        assertThat(service.listarTentativas(pedido.getId())).filteredOn(t -> t.aprovacaoDuplicada()).hasSize(1);
    }

    @Test void estornoDaTentativaVencedoraNaoPermiteReabrirCompra() {
        CheckoutResponse checkout = novoCheckout();
        String referencia = "tentativa-" + tentativaAtual(checkout.pedidoId());
        sincronizar("821", "approved", referencia);
        sincronizar("821", "refunded", referencia);
        sincronizar("821", "approved", referencia);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.REEMBOLSADO);
        assertThatThrownBy(() -> service.tentarNovamente(checkout.pedidoId())).isInstanceOf(ResponseStatusException.class);
        assertThat(estoque()).isEqualTo(4);
    }

    @Test void webhookSemAssinaturaNaoConsultaProvedorNemAlteraPedido() {
        CheckoutResponse checkout = novoCheckout();
        assertThatThrownBy(() -> service.processarWebhook("831", null, null)).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.AGUARDANDO_PAGAMENTO);
        assertThat(estoque()).isEqualTo(5);
    }

    @Test void webhookAssinadoRepetidoBaixaUmaVez() throws Exception {
        CheckoutResponse checkout = novoCheckout();
        String referencia = "tentativa-" + tentativaAtual(checkout.pedidoId());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("segredo-apenas-para-http-simulado".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String assinatura = "ts=123,v1=" + HexFormat.of().formatHex(mac.doFinal(
                "id:835;request-id:teste;ts:123;".getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 2; i++) {
            esperarPagamento("835", "approved", referencia, "100.00");
            service.processarWebhook("835", assinatura, "teste");
            server.verify(); server.reset();
        }
        assertThat(estoque()).isEqualTo(4);
        assertThat(service.listarTentativas(checkout.pedidoId())).hasSize(1);
    }

    @Test void novaTentativaUsaSnapshotOriginalMesmoComPrecoECarrinhoAlterados() {
        CheckoutResponse checkout = novoCheckout();
        sincronizar("841", "rejected", "tentativa-" + tentativaAtual(checkout.pedidoId()));
        Produto produto = produtos.findAll().get(0);
        produto.setPreco(new BigDecimal("200.00")); produtos.save(produto);
        carrinhos.deleteAll();
        esperarCheckout("snapshot"); service.tentarNovamente(checkout.pedidoId()); server.verify(); server.reset();
        sincronizar("842", "approved", "tentativa-" + tentativaAtual(checkout.pedidoId()));
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getTotal()).isEqualByComparingTo("100.00");
    }

    @Test void aprovacoesConcorrentesConcluemUmaUnicaVez() throws Exception {
        CheckoutResponse checkout = novoCheckout();
        String referencia = "tentativa-" + tentativaAtual(checkout.pedidoId());
        esperarPagamento("901", "approved", referencia, "100.00");
        esperarPagamento("902", "approved", referencia, "100.00");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        try {
            Future<?> a = executor.submit(() -> { aguardar(iniciar); service.processarRetorno("901"); });
            Future<?> b = executor.submit(() -> { aguardar(iniciar); service.processarRetorno("902"); });
            iniciar.countDown(); a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertThat(estoque()).isEqualTo(4);
        assertThat(service.listarTentativas(checkout.pedidoId())).filteredOn(t -> t.concluiuCompra()).hasSize(1);
        assertThat(service.listarTentativas(checkout.pedidoId())).filteredOn(t -> t.aprovacaoDuplicada()).hasSize(1);
    }

    @Test void checkoutRepetidoRetornaMesmoPedidoEPreferenciaSemChamarProvedor() {
        CheckoutResponse original = novoCheckout();
        // Inclui perda da resposta ao navegador: a resposta é recuperável só pelo carrinho.
        for (int i = 0; i < 3; i++) assertThat(service.criarCheckout(retirada())).isEqualTo(original);
        assertThat(pedidos.count()).isEqualTo(1);
        assertThat(tentativas.count()).isEqualTo(1);
    }

    @Test void duasAbasConcorrentesCriamUmaUnicaPreferencia() throws Exception {
        CountDownLatch entrouNoProvedor = new CountDownLatch(1);
        CountDownLatch liberarResposta = new CountDownLatch(1);
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andRespond(request -> {
                    entrouNoProvedor.countDown(); aguardar(liberarResposta);
                    return withSuccess("{\"id\":\"unica\",\"init_point\":\"https://example.test/unica\"}", MediaType.APPLICATION_JSON).createResponse(request);
                });
        String email = cliente.getEmail();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CheckoutResponse> primeira = executor.submit(() -> {
                autenticar(email);
                try { return service.criarCheckout(retirada()); } finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(entrouNoProvedor.await(10, TimeUnit.SECONDS)).isTrue();
            // O pedido já está confirmado no banco, mas a chamada externa ainda não respondeu.
            assertThat(pedidos.count()).isEqualTo(1);
            assertThatThrownBy(() -> service.criarCheckout(retirada())).isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            liberarResposta.countDown();
            CheckoutResponse resposta = primeira.get(10, TimeUnit.SECONDS);
            assertThat(service.criarCheckout(retirada())).isEqualTo(resposta);
            assertThat(pedidos.count()).isEqualTo(1);
            assertThat(tentativas.count()).isEqualTo(1);
        } finally { liberarResposta.countDown(); executor.shutdownNow(); }
    }

    @Test void timeoutDoProvedorNaoPermiteCriarOutraPreferenciaAsCegas() {
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andRespond(request -> { throw new java.net.SocketTimeoutException("resposta perdida"); });
        assertThatThrownBy(() -> service.criarCheckout(retirada())).isInstanceOf(ResponseStatusException.class);
        server.verify(); server.reset();
        Pedido pedido = pedidos.findAll().get(0);
        assertThat(tentativas.findAll().get(0).getStatusProvedor()).isEqualTo("checkout_unknown");
        assertThatThrownBy(() -> service.criarCheckout(retirada())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.tentarNovamente(pedido.getId())).isInstanceOf(ResponseStatusException.class);
        assertThat(pedidos.count()).isEqualTo(1);
        assertThat(tentativas.count()).isEqualTo(1);
        // A confirmação posterior ainda encontra a tentativa durável, mesmo sem URL salva.
        sincronizar("1001", "approved", "tentativa-" + tentativaAtual(pedido.getId()));
        assertThat(pedidos.findById(pedido.getId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.PAGO);
        assertThat(estoque()).isEqualTo(4);
    }

    @Test void falhaInesperadaDepoisDoEnvioMantemMarcadorDuravel() {
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andRespond(request -> { throw new AssertionError("interrupcao simulada antes de salvar a resposta"); });
        assertThatThrownBy(() -> service.criarCheckout(retirada())).isInstanceOf(AssertionError.class);
        server.verify(); server.reset();
        assertThat(tentativas.findAll().get(0).getStatusProvedor()).isEqualTo("checkout_processing");
        assertThatThrownBy(() -> service.criarCheckout(retirada())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.tentarNovamente(pedidos.findAll().get(0).getId())).isInstanceOf(ResponseStatusException.class);
        assertThat(pedidos.count()).isEqualTo(1);
        assertThat(tentativas.count()).isEqualTo(1);
    }

    @Test void erro5xxPreservaCompraSemReenviarCheckout() {
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> service.criarCheckout(retirada())).isInstanceOf(ResponseStatusException.class);
        server.verify(); server.reset();
        assertThatThrownBy(() -> service.criarCheckout(retirada())).isInstanceOf(ResponseStatusException.class);
        assertThat(tentativas.findAll().get(0).getStatusProvedor()).isEqualTo("checkout_unknown");
        assertThat(pedidos.count()).isEqualTo(1);
    }

    @Test void carrinhoAlteradoRepresentaOutraCompraSemReutilizarTotalAntigo() {
        CheckoutResponse original = novoCheckout();
        jdbc.update("update itens_carrinho set quantidade = 2");
        esperarCheckout("quantidade-alterada");
        CheckoutResponse alterado = service.criarCheckout(retirada()); server.verify(); server.reset();
        assertThat(alterado.pedidoId()).isNotEqualTo(original.pedidoId());
        assertThat(pedidos.findById(alterado.pedidoId()).orElseThrow().getTotal()).isEqualByComparingTo("200.00");
        assertThat(service.criarCheckout(retirada())).isEqualTo(alterado);
        assertThat(pedidos.count()).isEqualTo(2);
    }

    @Test void clientesDiferentesNaoCompartilhamCheckout() {
        CheckoutResponse primeiro = novoCheckout();
        Cliente outro = clientes.save(new Cliente("Outro", "checkout-outro@test.com", "11999999999", "hash"));
        Carrinho carrinho = new Carrinho(); carrinho.setCliente(outro);
        ItemCarrinho item = new ItemCarrinho(); item.setCarrinho(carrinho);
        item.setProdutoTamanho(tamanhos.findById(variacaoId).orElseThrow()); item.setQuantidade(1);
        carrinho.getItens().add(item); carrinhos.save(carrinho);
        autenticar(outro.getEmail()); esperarCheckout("outro-cliente");
        CheckoutResponse segundo = service.criarCheckout(retirada()); server.verify(); server.reset();
        assertThat(segundo.pedidoId()).isNotEqualTo(primeiro.pedidoId());
        assertThat(service.criarCheckout(retirada())).isEqualTo(segundo);
        assertThat(pedidos.count()).isEqualTo(2);
    }

    @Test void compraLegitimaAposPagamentoTemNovaIdentidade() {
        CheckoutResponse primeiro = novoCheckout();
        sincronizar("1002", "approved", "tentativa-" + tentativaAtual(primeiro.pedidoId()));
        assertThatThrownBy(() -> service.criarCheckout(retirada())).isInstanceOf(ResponseStatusException.class);
        Carrinho carrinho = carrinhos.findByClienteId(cliente.getId()).orElseThrow();
        // O pagamento removeu as linhas; adicionar novamente cria outra identidade de compra.
        jdbc.update("insert into itens_carrinho (carrinho_id, produto_tamanho_id, quantidade) values (?, ?, 1)", carrinho.getId(), variacaoId);
        esperarCheckout("nova-compra"); CheckoutResponse segundo = service.criarCheckout(retirada()); server.verify(); server.reset();
        assertThat(segundo.pedidoId()).isNotEqualTo(primeiro.pedidoId());
        assertThat(pedidos.count()).isEqualTo(2);
    }

    @Test void confirmacaoDuranteRespostaDeOutraTentativaNaoRegridePedidoPago() {
        CheckoutResponse original = novoCheckout();
        String referencia = "tentativa-" + tentativaAtual(original.pedidoId());
        sincronizar("1101", "rejected", referencia);
        esperarPagamento("1102", "approved", referencia, "100.00");
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andRespond(request -> {
                    service.processarRetorno("1102");
                    return withSuccess("{\"id\":\"tardia\",\"init_point\":\"https://example.test/tardia\"}", MediaType.APPLICATION_JSON).createResponse(request);
                });
        assertThatThrownBy(() -> service.tentarNovamente(original.pedidoId())).isInstanceOf(ResponseStatusException.class);
        assertThat(pedidos.findById(original.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.PAGO);
        assertThat(estoque()).isEqualTo(4);
    }

    @Test void doisClientesConcorrentesNaoReservamAMesmaUltimaUnidade() throws Exception {
        jdbc.update("update produto_tamanhos set quantidade = 1 where id = ?", variacaoId);
        Cliente outro = outroClienteComCarrinho("ultima@test.com", List.of(variacaoId));
        esperarCheckout("ultima-unidade");
        List<Object> resultados = checkoutsConcorrentes(cliente.getEmail(), outro.getEmail());
        server.verify(); server.reset();
        assertThat(resultados.stream().filter(CheckoutResponse.class::isInstance)).hasSize(1);
        assertThat(resultados).contains(HttpStatus.CONFLICT);
        assertThat(pedidos.count()).isEqualTo(1);
        assertThat(tentativas.count()).isEqualTo(1);
        assertThat(reservado()).isEqualTo(1);
        assertThat(estoque()).isEqualTo(1);
        CheckoutResponse vencedor = (CheckoutResponse) resultados.stream().filter(CheckoutResponse.class::isInstance).findFirst().orElseThrow();
        sincronizar("2001", "approved", "tentativa-" + tentativaAtual(vencedor.pedidoId()));
        assertThat(estoque()).isZero();
        assertThat(reservado()).isZero();
    }

    @Test void expiracaoLiberaEstoqueUmaVezEOutroClientePodeComprar() {
        jdbc.update("update produto_tamanhos set quantidade = 1 where id = ?", variacaoId);
        CheckoutResponse primeiro = novoCheckout();
        vencerReserva(primeiro.pedidoId());
        reservas.expirarVencidas(); reservas.expirarVencidas();
        assertThat(reservado()).isZero(); assertThat(estoque()).isEqualTo(1);
        assertThat(pedidos.findById(primeiro.pedidoId()).orElseThrow().getReservaStatus()).isEqualTo(ReservaStatus.EXPIRADA);
        Cliente outro = outroClienteComCarrinho("apos-expirar@test.com", List.of(variacaoId));
        autenticar(outro.getEmail()); esperarCheckout("apos-expirar");
        CheckoutResponse segundo = service.criarCheckout(retirada()); server.verify(); server.reset();
        // Pagamento tardio não pode consumir a unidade reservada pelo segundo cliente.
        sincronizar("2002", "approved", "tentativa-" + tentativaAtual(primeiro.pedidoId()));
        Pedido tardio = pedidos.findById(primeiro.pedidoId()).orElseThrow();
        assertThat(tardio.getStatus()).isEqualTo(PedidoStatus.PAGO_EM_REVISAO);
        assertThat(tardio.isEstoqueBaixado()).isFalse();
        assertThat(tardio.getMercadoPagoPaymentId()).isEqualTo("2002");
        assertThat(service.listarPendenciasEstoque()).extracting(p -> p.id()).contains(primeiro.pedidoId());
        assertThat(reservado()).isEqualTo(1); assertThat(estoque()).isEqualTo(1);
        sincronizar("2003", "approved", "tentativa-" + tentativaAtual(segundo.pedidoId()));
        sincronizar("2002", "approved", "tentativa-" + tentativaAtual(primeiro.pedidoId()));
        assertThat(estoque()).isZero(); assertThat(reservado()).isZero();
    }

    @Test void recusaLiberaReservaERetomadaReservaDeNovoSemDuplicar() {
        CheckoutResponse checkout = novoCheckout();
        assertThat(reservado()).isEqualTo(1);
        var prazo = pedidos.findById(checkout.pedidoId()).orElseThrow().getReservaExpiraEm();
        assertThat(service.criarCheckout(retirada())).isEqualTo(checkout);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getReservaExpiraEm()).isEqualTo(prazo);
        assertThat(reservado()).isEqualTo(1);
        sincronizar("2004", "rejected", "tentativa-" + tentativaAtual(checkout.pedidoId()));
        assertThat(reservado()).isZero();
        esperarCheckout("nova-reserva"); service.tentarNovamente(checkout.pedidoId()); server.verify(); server.reset();
        assertThat(reservado()).isEqualTo(1);
    }

    @Test void pagamentoPendenteTambemLiberaAoVencerSemFingirCancelamentoFinanceiro() {
        CheckoutResponse checkout = novoCheckout();
        sincronizar("2005", "pending", "tentativa-" + tentativaAtual(checkout.pedidoId()));
        vencerReserva(checkout.pedidoId()); reservas.expirarVencidas();
        assertThat(reservado()).isZero();
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.PENDENTE);
        sincronizar("2005", "approved", "tentativa-" + tentativaAtual(checkout.pedidoId()));
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.PAGO_EM_REVISAO);
        assertThat(estoque()).isEqualTo(5);
    }

    @Test void aprovacaoDisputandoComExpiracaoNaoDuplicaLiberaNemBaixaEstoque() throws Exception {
        CheckoutResponse checkout = novoCheckout();
        vencerReserva(checkout.pedidoId());
        esperarPagamento("2006", "approved", "tentativa-" + tentativaAtual(checkout.pedidoId()), "100.00");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            Future<?> expirar = executor.submit(() -> { aguardar(inicio); reservas.expirarVencidas(); });
            Future<?> aprovar = executor.submit(() -> { aguardar(inicio); service.processarRetorno("2006"); });
            inicio.countDown(); expirar.get(15, TimeUnit.SECONDS); aprovar.get(15, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertThat(reservado()).isZero(); assertThat(estoque()).isEqualTo(5);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.PAGO_EM_REVISAO);
    }

    @Test void reservaConsumidaNaoEhLiberadaPeloJob() {
        CheckoutResponse checkout = novoCheckout();
        sincronizar("2007", "approved", "tentativa-" + tentativaAtual(checkout.pedidoId()));
        vencerReserva(checkout.pedidoId()); reservas.expirarVencidas();
        assertThat(estoque()).isEqualTo(4); assertThat(reservado()).isZero();
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getReservaStatus()).isEqualTo(ReservaStatus.CONSUMIDA);
    }

    @Test void estoqueInsuficienteEmUmItemNaoReservaParcialmente() {
        Long segunda = criarVariacao("RESERVA-2", 1);
        Cliente outro = outroClienteComCarrinho("multiplos@test.com", List.of(segunda));
        autenticar(outro.getEmail()); novoCheckout(); // última unidade da segunda variação reservada
        autenticar(cliente.getEmail());
        Long carrinhoId = carrinhos.findByClienteId(cliente.getId()).orElseThrow().getId();
        jdbc.update("insert into itens_carrinho (carrinho_id, produto_tamanho_id, quantidade) values (?, ?, 1)", carrinhoId, segunda);
        assertThatThrownBy(() -> service.criarCheckout(retirada())).isInstanceOf(ResponseStatusException.class);
        assertThat(reservado()).isZero();
        assertThat(pedidos.count()).isEqualTo(1);
        assertThat(tamanhos.findById(segunda).orElseThrow().getQuantidadeReservada()).isEqualTo(1);
    }

    @Test void variosProdutosEmOrdemInversaNaoCausamDeadlock() throws Exception {
        jdbc.update("update produto_tamanhos set quantidade = 1 where id = ?", variacaoId);
        Long segunda = criarVariacao("RESERVA-ORDEM", 1);
        Long carrinhoId = carrinhos.findByClienteId(cliente.getId()).orElseThrow().getId();
        jdbc.update("insert into itens_carrinho (carrinho_id, produto_tamanho_id, quantidade) values (?, ?, 1)", carrinhoId, segunda);
        Cliente outro = outroClienteComCarrinho("ordem-inversa@test.com", List.of(segunda, variacaoId));
        esperarCheckout("ordem-estavel");
        List<Object> resultados = checkoutsConcorrentes(cliente.getEmail(), outro.getEmail());
        assertThat(resultados.stream().filter(CheckoutResponse.class::isInstance)).hasSize(1);
        assertThat(resultados).contains(HttpStatus.CONFLICT);
        assertThat(reservado()).isEqualTo(1);
        assertThat(tamanhos.findById(segunda).orElseThrow().getQuantidadeReservada()).isEqualTo(1);
        assertThat(pedidos.count()).isEqualTo(1);
    }

    private int reservado() { return tamanhos.findById(variacaoId).orElseThrow().getQuantidadeReservada(); }
    private void vencerReserva(Long pedidoId) {
        jdbc.update("update pedidos set reserva_expira_em = ? where id = ?", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), pedidoId);
    }
    private Cliente outroClienteComCarrinho(String email, List<Long> variacoes) {
        Cliente outro = clientes.save(new Cliente("Outro", email, "11999999999", "hash"));
        Carrinho carrinho = new Carrinho(); carrinho.setCliente(outro);
        for (Long id : variacoes) {
            ItemCarrinho item = new ItemCarrinho(); item.setCarrinho(carrinho);
            item.setProdutoTamanho(tamanhos.findById(id).orElseThrow()); item.setQuantidade(1);
            carrinho.getItens().add(item);
        }
        carrinhos.save(carrinho); return outro;
    }
    private Long criarVariacao(String codigo, int quantidade) {
        Produto produto = new Produto(); produto.setCodigo(codigo); produto.setNome("Teste"); produto.setDestaque("Teste");
        produto.setPreco(new BigDecimal("100.00")); produto.setCategoria(Categoria.VESTIDO); produto.setAtivo(true);
        ProdutoTamanho tamanho = new ProdutoTamanho(); tamanho.setProduto(produto); tamanho.setTamanho(Tamanho.P); tamanho.setQuantidade(quantidade);
        produto.getTamanhos().add(tamanho); produtos.save(produto); return tamanho.getId();
    }
    private List<Object> checkoutsConcorrentes(String primeiro, String segundo) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> checkoutConcorrente(primeiro, inicio));
            var b = executor.submit(() -> checkoutConcorrente(segundo, inicio));
            inicio.countDown(); return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    private Object checkoutConcorrente(String email, CountDownLatch inicio) {
        autenticar(email); aguardar(inicio);
        try { return service.criarCheckout(retirada()); }
        catch (ResponseStatusException e) { return e.getStatusCode(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void aguardar(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    private void autenticar(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
    }
    private int estoque() { return tamanhos.findById(variacaoId).orElseThrow().getQuantidade(); }
    private Long tentativaAtual(Long pedidoId) { return tentativas.findAllByPedidoIdOrderByIdDesc(pedidoId).get(0).getId(); }
    private CheckoutResponse novoCheckout() {
        esperarCheckout("inicial"); CheckoutResponse result = service.criarCheckout(retirada()); server.verify(); server.reset(); return result;
    }
    private void esperarCheckout(String id) {
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.items[0].unit_price").value(100.00))
                .andExpect(jsonPath("$.external_reference").value(org.hamcrest.Matchers.startsWith("tentativa-")))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"init_point\":\"https://example.test/checkout/" + id + "\"}", MediaType.APPLICATION_JSON));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"refunded,REEMBOLSADO", "charged_back,CHARGEBACK"})
    void posPagamentoNaoRegrideNemBaixaEstoqueNovamente(String status, PedidoStatus esperado) {
        CheckoutResponse checkout = novoCheckout();
        String ref = "tentativa-" + tentativaAtual(checkout.pedidoId());
        sincronizar("3001", "pending", ref);
        sincronizar("3001", "approved", ref);
        sincronizar("3001", status, ref);
        sincronizar("3001", status, ref);
        for (String atrasado : List.of("pending", "approved", "rejected", "cancelled")) sincronizar("3001", atrasado, ref);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(esperado);
        assertThat(tentativas.findByPaymentId("3001").orElseThrow().getStatus()).isEqualTo(esperado);
        assertThat(estoque()).isEqualTo(4);
        assertThat(reservado()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"rejected,RECUSADO", "cancelled,CANCELADO", "refunded,REEMBOLSADO", "charged_back,CHARGEBACK"})
    void estadoFinalRecebidoPrimeiroNaoReabreComAprovacaoAtrasada(String status, PedidoStatus esperado) {
        CheckoutResponse checkout = novoCheckout();
        String ref = "tentativa-" + tentativaAtual(checkout.pedidoId());
        sincronizar("3002", status, ref);
        sincronizar("3002", "approved", ref);
        sincronizar("3002", "pending", ref);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(esperado);
        assertThat(estoque()).isEqualTo(5);
        assertThat(reservado()).isZero();
    }

    @Test void expiracaoFinanceiraDistingueReservaEPermiteNovaTentativa() {
        CheckoutResponse checkout = novoCheckout();
        String ref = "tentativa-" + tentativaAtual(checkout.pedidoId());
        sincronizarDetalhado("3003", "cancelled", "expired", "2026-09-22T10:00:00Z", ref);
        sincronizar("3003", "approved", ref);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.EXPIRADO);
        assertThat(reservado()).isZero();
        esperarCheckout("apos-expiracao");
        service.tentarNovamente(checkout.pedidoId()); server.verify(); server.reset();
        sincronizar("3004", "approved", "tentativa-" + tentativaAtual(checkout.pedidoId()));
        assertThat(estoque()).isEqualTo(4);
    }

    @Test void mediacaoExigeVersaoMaisRecenteParaResolverSemNovaBaixa() {
        CheckoutResponse checkout = novoCheckout();
        String ref = "tentativa-" + tentativaAtual(checkout.pedidoId());
        sincronizarDetalhado("3005", "approved", "accredited", "2026-09-22T10:00:00Z", ref);
        sincronizarDetalhado("3005", "in_mediation", "pending", "2026-09-22T11:00:00Z", ref);
        sincronizarDetalhado("3005", "approved", "accredited", "2026-09-22T10:00:00Z", ref);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.EM_MEDIACAO);
        sincronizarDetalhado("3005", "approved", "accredited", "2026-09-22T12:00:00Z", ref);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.PAGO);
        sincronizarDetalhado("3005", "approved", "partially_refunded", "2026-09-22T13:00:00Z", ref);
        sincronizar("3005", "approved", ref);
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.REEMBOLSADO_PARCIAL);
        assertThat(estoque()).isEqualTo(4);
    }

    @Test void estadoDesconhecidoNaoLiberaReservaNemViraCancelamento() {
        CheckoutResponse checkout = novoCheckout();
        String ref = "tentativa-" + tentativaAtual(checkout.pedidoId());
        esperarPagamento("3006", "future_status", ref, "100.00");
        assertThatThrownBy(() -> service.processarRetorno("3006")).isInstanceOf(ResponseStatusException.class);
        server.verify(); server.reset();
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.AGUARDANDO_PAGAMENTO);
        assertThat(reservado()).isEqualTo(1);
    }

    private void sincronizarDetalhado(String id, String status, String detalhe, String data, String ref) {
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/" + id))
                .andRespond(withSuccess("{\"id\":" + id + ",\"status\":\"" + status
                        + "\",\"status_detail\":\"" + detalhe + "\",\"date_last_updated\":\"" + data
                        + "\",\"external_reference\":\"" + ref + "\",\"currency_id\":\"BRL\",\"transaction_amount\":100}", MediaType.APPLICATION_JSON));
        service.processarRetorno(id); server.verify(); server.reset();
    }

    private static CheckoutRequest retirada() { return new CheckoutRequest(ModalidadeRecebimento.RETIRADA, null); }

    private static String[] camposEndereco() {
        return new String[]{" Maria Silva ", "01310-100", " Avenida Paulista ", "1578", "", "Bela Vista", "São Paulo", "sp"};
    }

    private static CheckoutRequest entrega(String[] campos) {
        return new CheckoutRequest(ModalidadeRecebimento.ENTREGA, new CheckoutRequest.EnderecoRequest(
                campos[0], campos[1], campos[2], campos[3], campos[4], campos[5], campos[6], campos[7]));
    }

    @Test void enderecoNormalizadoPersistidoEDeduplicadoIndependenteDoPerfil() {
        CheckoutRequest request = entrega(camposEndereco());
        esperarCheckout("endereco");
        CheckoutResponse checkout = service.criarCheckout(request); server.verify(); server.reset();
        var pedido = pedidos.findById(checkout.pedidoId()).orElseThrow();
        assertThat(pedido.getModalidade()).isEqualTo(ModalidadeRecebimento.ENTREGA);
        assertThat(pedido.getEnderecoEntrega().destinatario()).isEqualTo("Maria Silva");
        assertThat(pedido.getEnderecoEntrega().cep()).isEqualTo("01310100");
        assertThat(pedido.getEnderecoEntrega().uf()).isEqualTo("SP");
        assertThat(pedido.getEnderecoEntrega().complemento()).isNull();
        String[] normalizado = camposEndereco(); normalizado[0] = "Maria Silva"; normalizado[1] = "01310100"; normalizado[7] = "SP";
        assertThat(service.criarCheckout(entrega(normalizado))).isEqualTo(checkout);
        cliente.setNome("Outro nome no perfil"); clientes.save(cliente);
        assertThat(service.buscarPedido(checkout.pedidoId()).enderecoEntrega()).isEqualTo(pedido.getEnderecoEntrega());
        assertThat(service.listarPedidos().get(0).enderecoEntrega()).isEqualTo(pedido.getEnderecoEntrega());
        assertThatThrownBy(() -> pedido.definirRecebimento(ModalidadeRecebimento.RETIRADA, null)).isInstanceOf(IllegalStateException.class);
        sincronizar("4001", "rejected", "tentativa-" + tentativaAtual(checkout.pedidoId()));
        esperarCheckout("retomada-endereco"); service.tentarNovamente(checkout.pedidoId()); server.verify(); server.reset();
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getEnderecoEntrega()).isEqualTo(pedido.getEnderecoEntrega());
    }

    @Test void destinosDiferentesNaoReutilizamPedidoAnterior() {
        esperarCheckout("destino-a");
        var primeiro = service.criarCheckout(entrega(camposEndereco())); server.verify(); server.reset();
        String[] outro = camposEndereco(); outro[3] = "2000"; outro[4] = "Apto 12";
        esperarCheckout("destino-b");
        var segundo = service.criarCheckout(entrega(outro)); server.verify(); server.reset();
        assertThat(segundo.pedidoId()).isNotEqualTo(primeiro.pedidoId());
        assertThat(pedidos.findById(primeiro.pedidoId()).orElseThrow().getEnderecoEntrega().numero()).isEqualTo("1578");
        assertThat(pedidos.findById(segundo.pedidoId()).orElseThrow().getEnderecoEntrega().complemento()).isEqualTo("Apto 12");
        esperarCheckout("retirada"); var retirada = service.criarCheckout(retirada()); server.verify(); server.reset();
        assertThat(retirada.pedidoId()).isNotIn(primeiro.pedidoId(), segundo.pedidoId());
        assertThat(pedidos.findById(retirada.pedidoId()).orElseThrow().getEnderecoEntrega()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2, 3, 5, 6, 7})
    void camposObrigatoriosValidadosAntesDeReservarOuChamarProvedor(int campo) {
        String[] valores = camposEndereco(); valores[campo] = "   ";
        assertThatThrownBy(() -> service.criarCheckout(entrega(valores))).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException)e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(pedidos.count()).isZero(); assertThat(reservado()).isZero();
    }

    @Test void rejeitaCepUfELimitesInvalidosNoBackend() {
        for (String cep : List.of("123", "ABCDE-123", "00000000", "123456789", "01310 100")) {
            String[] campos = camposEndereco(); campos[1] = cep;
            assertThatThrownBy(() -> service.criarCheckout(entrega(campos))).isInstanceOf(ResponseStatusException.class);
        }
        String[] campos = camposEndereco(); campos[7] = "XX";
        assertThatThrownBy(() -> service.criarCheckout(entrega(campos))).isInstanceOf(ResponseStatusException.class);
        campos[7] = "SP"; campos[4] = "a".repeat(121);
        assertThatThrownBy(() -> service.criarCheckout(entrega(campos))).isInstanceOf(ResponseStatusException.class);
        assertThat(pedidos.count()).isZero(); assertThat(reservado()).isZero();
    }

    @Test void apiExigeModalidadeEEnderecoParaEntrega() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilter).build();
        String token = jwt.generateToken(org.springframework.security.core.userdetails.User
                .withUsername(cliente.getEmail()).password("hash").roles("CLIENTE").build());
        SecurityContextHolder.clearContext();
        for (String payload : List.of("{}", "{\"modalidade\":\"INVALIDA\"}", "{\"modalidade\":\"ENTREGA\"}",
                "{\"modalidade\":\"ENTREGA\",\"endereco\":{}}", "{\"modalidade\":\"RETIRADA\",\"endereco\":{}}")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/pagamentos/checkout")
                            .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        }
        assertThat(pedidos.count()).isZero(); assertThat(tentativas.count()).isZero(); assertThat(reservado()).isZero();
    }

    @Test void apiPersisteEntregaEDevolveSomenteAoDono() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilter).build();
        String token = jwt.generateToken(org.springframework.security.core.userdetails.User
                .withUsername(cliente.getEmail()).password("hash").roles("CLIENTE").build());
        SecurityContextHolder.clearContext();
        esperarCheckout("api-endereco");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/pagamentos/checkout")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("""
                        {"modalidade":"ENTREGA","endereco":{"destinatario":"Maria Silva","cep":"01310-100",
                        "rua":"Avenida Paulista","numero":"S/N","bairro":"Bela Vista","cidade":"São Paulo","uf":"sp"}}
                        """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        server.verify(); server.reset();
        Long id = pedidos.findAll().get(0).getId();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/pagamentos/pedidos/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.modalidade").value("ENTREGA"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.enderecoEntrega.cep").value("01310100"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.enderecoEntrega.numero").value("S/N"));
        clientes.save(new Cliente("Outro", "endereco-outro@test.com", "11999999999", "hash"));
        String outroToken = jwt.generateToken(org.springframework.security.core.userdetails.User
                .withUsername("endereco-outro@test.com").password("hash").roles("CLIENTE").build());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/pagamentos/pedidos/" + id)
                        .header("Authorization", "Bearer " + outroToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test void pedidoLegadoSemDestinoContinuaConsultavelMasNaoAbreNovoPagamento() {
        var checkout = novoCheckout();
        jdbc.update("update pedidos set modalidade = null where id = ?", checkout.pedidoId());
        assertThat(service.buscarPedido(checkout.pedidoId()).modalidade()).isNull();
        assertThatThrownBy(() -> service.tentarNovamente(checkout.pedidoId())).isInstanceOf(ResponseStatusException.class);
        assertThat(tentativas.count()).isEqualTo(1);
    }

    private void sincronizar(String id, String status, String referencia) {
        esperarPagamento(id, status, referencia, "100.00"); service.processarRetorno(id); server.verify(); server.reset();
    }
    private void esperarPagamento(String id, String status, String referencia, String valor) {
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/" + id))
                .andRespond(withSuccess("{\"id\":" + id + ",\"status\":\"" + status + "\",\"external_reference\":\""
                        + referencia + "\",\"currency_id\":\"BRL\",\"transaction_amount\":" + valor + "}", MediaType.APPLICATION_JSON));
    }
}
