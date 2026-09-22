package com.webstore.backend;

import com.webstore.backend.controller.dto.CheckoutResponse;
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
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(service::criarCheckout).isInstanceOf(ResponseStatusException.class);
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
        assertThat(pedidos.findById(checkout.pedidoId()).orElseThrow().getStatus()).isEqualTo(PedidoStatus.CANCELADO);
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
        esperarCheckout("inicial"); CheckoutResponse result = service.criarCheckout(); server.verify(); server.reset(); return result;
    }
    private void esperarCheckout(String id) {
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.items[0].unit_price").value(100.00))
                .andExpect(jsonPath("$.external_reference").value(org.hamcrest.Matchers.startsWith("tentativa-")))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"init_point\":\"https://example.test/checkout/" + id + "\"}", MediaType.APPLICATION_JSON));
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
