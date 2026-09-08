package com.webstore.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.webstore.backend.controller.dto.CheckoutResponse;
import com.webstore.backend.controller.dto.PedidoStatusResponse;
import com.webstore.backend.controller.dto.PedidoResponse;
import com.webstore.backend.controller.dto.ItemPedidoResponse;
import com.webstore.backend.model.*;
import com.webstore.backend.repository.CarrinhoRepository;
import com.webstore.backend.repository.ClienteRepository;
import com.webstore.backend.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class PagamentoService {
    private final CarrinhoRepository carrinhoRepository;
    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final RestClient mercadoPagoClient;
    private final String accessToken;
    private final String webhookSecret;
    private final String webhookUrl;
    private final String frontendUrl;

    public PagamentoService(CarrinhoRepository carrinhoRepository,
                            ClienteRepository clienteRepository,
                            PedidoRepository pedidoRepository,
                            @Value("${mercadopago.access-token:}") String accessToken,
                            @Value("${mercadopago.webhook-secret:}") String webhookSecret,
                            @Value("${app.webhook-url:}") String webhookUrl,
                            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.carrinhoRepository = carrinhoRepository;
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.mercadoPagoClient = RestClient.create("https://api.mercadopago.com");
        this.accessToken = accessToken;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.webhookUrl = webhookUrl;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
    }

    @Transactional
    public CheckoutResponse criarCheckout() {
        validarAccessToken();
        Cliente cliente = buscarClienteAutenticado();
        Carrinho carrinho = carrinhoRepository.findByClienteId(cliente.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "O carrinho está vazio."));
        if (carrinho.getItens().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O carrinho está vazio.");
        }

        Pedido pedido = criarPedido(cliente, carrinho);
        pedidoRepository.saveAndFlush(pedido);
        Map<String, Object> preference = new HashMap<>();
        preference.put("items", criarItensMercadoPago(pedido));
        preference.put("payer", Map.of("name", cliente.getNome(), "email", cliente.getEmail()));
        preference.put("external_reference", "pedido-" + pedido.getId());
        boolean retornoPublico = webhookUrl != null && !webhookUrl.isBlank();
        preference.put("back_urls", Map.of(
                "success", urlRetorno(pedido, "sucesso", retornoPublico),
                "pending", urlRetorno(pedido, "pendente", retornoPublico),
                "failure", urlRetorno(pedido, "falha", retornoPublico)));
        if (retornoPublico) preference.put("auto_return", "approved");
        if (webhookUrl != null && !webhookUrl.isBlank()) preference.put("notification_url", webhookUrl);

        try {
            PreferenciaResponse response = mercadoPagoClient.post().uri("/checkout/preferences")
                    .header("Authorization", "Bearer " + accessToken).body(preference)
                    .retrieve().body(PreferenciaResponse.class);
            if (response == null || response.id() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Mercado Pago não criou o checkout.");
            }
            String checkoutUrl = response.sandboxInitPoint() != null ? response.sandboxInitPoint() : response.initPoint();
            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Mercado Pago não retornou a URL do checkout.");
            }
            pedido.setMercadoPagoPreferenceId(response.id());
            pedidoRepository.save(pedido);
            return new CheckoutResponse(checkoutUrl, response.id(), pedido.getId());
        } catch (RestClientResponseException exception) {
            pedido.setStatus(PedidoStatus.ERRO);
            pedidoRepository.save(pedido);
            String detalhe = exception.getResponseBodyAsString();
            if (detalhe == null || detalhe.isBlank()) detalhe = exception.getStatusText();
            if (detalhe.length() > 500) detalhe = detalhe.substring(0, 500);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Mercado Pago recusou o checkout (HTTP " + exception.getStatusCode().value() + "): " + detalhe,
                    exception);
        } catch (RestClientException exception) {
            pedido.setStatus(PedidoStatus.ERRO);
            pedidoRepository.save(pedido);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível criar o checkout no Mercado Pago.", exception);
        }
    }

    @Transactional(readOnly = true)
    public PedidoStatusResponse buscarPedido(Long pedidoId) {
        Cliente cliente = buscarClienteAutenticado();
        Pedido pedido = pedidoRepository.findByIdAndClienteId(pedidoId, cliente.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        return new PedidoStatusResponse(pedido.getId(), pedido.getStatus(), pedido.getTotal());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPedidos() {
        Cliente cliente = buscarClienteAutenticado();
        return pedidoRepository.findAllByClienteIdOrderByCriadoEmDesc(cliente.getId()).stream()
                .map(pedido -> new PedidoResponse(
                        pedido.getId(), pedido.getStatus(), pedido.getTotal(), pedido.getCriadoEm(),
                        pedido.getItens().stream()
                                .map(item -> new ItemPedidoResponse(item.getNomeProduto(), item.getTamanho(),
                                        item.getQuantidade(), item.getPrecoUnitario()))
                                .toList()))
                .toList();
    }

    @Transactional
    public void processarWebhook(String dataId, String xSignature, String xRequestId) {
        validarAccessToken();
        validarAssinatura(dataId, xSignature, xRequestId);
        sincronizarPagamento(dataId);
    }

    @Transactional
    public void processarRetorno(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) return;
        validarAccessToken();
        sincronizarPagamento(paymentId.trim());
    }

    private void sincronizarPagamento(String dataId) {
        PagamentoResponse pagamento;
        try {
            pagamento = mercadoPagoClient.get().uri("/v1/payments/{id}", dataId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(PagamentoResponse.class);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível consultar o pagamento.", exception);
        }
        if (pagamento == null || pagamento.externalReference() == null
                || !pagamento.externalReference().startsWith("pedido-")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pagamento sem referência de pedido válida.");
        }
        Long pedidoId;
        try {
            pedidoId = Long.valueOf(pagamento.externalReference().substring("pedido-".length()));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referência de pedido inválida.");
        }
        Pedido pedido = pedidoRepository.findWithItensForUpdateById(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (pagamento.transactionAmount() == null || pedido.getTotal().compareTo(pagamento.transactionAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O valor do pagamento diverge do pedido.");
        }
        String paymentId = String.valueOf(pagamento.id());
        if (pedido.getMercadoPagoPaymentId() != null
                && !pedido.getMercadoPagoPaymentId().equals(paymentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este pedido já está vinculado a outro pagamento.");
        }
        pedido.setMercadoPagoPaymentId(paymentId);
        pedido.setMercadoPagoStatus(pagamento.status());
        PedidoStatus novoStatus = mapearStatus(pagamento.status());
        if (pedido.getStatus() == PedidoStatus.PAGO
                && (novoStatus == PedidoStatus.PENDENTE || novoStatus == PedidoStatus.RECUSADO)) {
            return;
        }
        if (novoStatus == PedidoStatus.PAGO && !pedido.isEstoqueBaixado()) {
            baixarEstoque(pedido);
            removerItensPagosDoCarrinho(pedido);
            pedido.setEstoqueBaixado(true);
        }
        pedido.setStatus(novoStatus);
        pedidoRepository.save(pedido);
    }

    private Pedido criarPedido(Cliente cliente, Carrinho carrinho) {
        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        BigDecimal total = BigDecimal.ZERO;
        for (ItemCarrinho itemCarrinho : carrinho.getItens()) {
            ProdutoTamanho variacao = validarItem(itemCarrinho);
            Produto produto = variacao.getProduto();
            BigDecimal preco = variacao.getPreco() != null ? variacao.getPreco() : produto.getPreco();
            ItemPedido item = new ItemPedido();
            item.setPedido(pedido);
            item.setProdutoTamanho(variacao);
            item.setNomeProduto(produto.getNome());
            item.setTamanho(variacao.getTamanho().getLabel());
            item.setQuantidade(itemCarrinho.getQuantidade());
            item.setPrecoUnitario(preco);
            pedido.getItens().add(item);
            total = total.add(preco.multiply(BigDecimal.valueOf(itemCarrinho.getQuantidade())));
        }
        pedido.setTotal(total);
        return pedido;
    }

    private ProdutoTamanho validarItem(ItemCarrinho item) {
        ProdutoTamanho variacao = item.getProdutoTamanho();
        Produto produto = variacao.getProduto();
        int quantidade = item.getQuantidade() == null ? 0 : item.getQuantidade();
        BigDecimal preco = variacao.getPreco() != null ? variacao.getPreco() : produto.getPreco();
        if (Boolean.FALSE.equals(produto.getAtivo()) || Boolean.FALSE.equals(variacao.getAtivo())
                || quantidade < 1 || variacao.getQuantidade() == null || quantidade > variacao.getQuantidade()
                || preco == null || preco.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Revise a disponibilidade de " + produto.getNome() + " antes de pagar.");
        }
        return variacao;
    }

    private List<Map<String, Object>> criarItensMercadoPago(Pedido pedido) {
        List<Map<String, Object>> itens = new ArrayList<>();
        for (ItemPedido item : pedido.getItens()) {
            itens.add(Map.of("id", item.getProdutoTamanho().getId().toString(),
                    "title", item.getNomeProduto() + " - " + item.getTamanho(),
                    "quantity", item.getQuantidade(), "currency_id", "BRL",
                    "unit_price", item.getPrecoUnitario()));
        }
        return itens;
    }

    private void baixarEstoque(Pedido pedido) {
        for (ItemPedido item : pedido.getItens()) {
            ProdutoTamanho variacao = item.getProdutoTamanho();
            if (variacao.getQuantidade() == null || variacao.getQuantidade() < item.getQuantidade()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Estoque insuficiente para confirmar " + item.getNomeProduto() + ".");
            }
            variacao.setQuantidade(variacao.getQuantidade() - item.getQuantidade());
        }
    }

    private void removerItensPagosDoCarrinho(Pedido pedido) {
        carrinhoRepository.findByClienteId(pedido.getCliente().getId()).ifPresent(carrinho -> {
            Map<Long, Integer> quantidadesPagas = new HashMap<>();
            for (ItemPedido item : pedido.getItens()) {
                quantidadesPagas.merge(item.getProdutoTamanho().getId(), item.getQuantidade(), Integer::sum);
            }
            carrinho.getItens().removeIf(itemCarrinho -> {
                Integer quantidadePaga = quantidadesPagas.get(itemCarrinho.getProdutoTamanho().getId());
                if (quantidadePaga == null) return false;
                int quantidadeAtual = itemCarrinho.getQuantidade();
                if (quantidadeAtual <= quantidadePaga) return true;
                itemCarrinho.setQuantidade(quantidadeAtual - quantidadePaga);
                return false;
            });
            carrinhoRepository.save(carrinho);
        });
    }

    private void validarAssinatura(String dataId, String xSignature, String xRequestId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure MERCADO_PAGO_WEBHOOK_SECRET.");
        }
        if (dataId == null || dataId.isBlank() || xSignature == null || xRequestId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook sem assinatura válida.");
        }
        dataId = dataId.trim();
        xSignature = xSignature.trim();
        xRequestId = xRequestId.trim();
        Map<String, String> parts = new HashMap<>();
        for (String part : xSignature.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2) parts.put(pair[0].toLowerCase(), pair[1].trim());
        }
        String ts = parts.get("ts");
        String received = parts.get("v1");
        if (ts == null || received == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura do webhook inválida.");
        }
        String manifest = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    received.getBytes(StandardCharsets.US_ASCII))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura do webhook inválida.");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Falha ao validar a assinatura do webhook.", exception);
        }
    }

    private PedidoStatus mapearStatus(String status) {
        if (status == null) return PedidoStatus.ERRO;
        return switch (status) {
            case "approved" -> PedidoStatus.PAGO;
            case "pending", "in_process", "in_mediation", "authorized" -> PedidoStatus.PENDENTE;
            case "rejected" -> PedidoStatus.RECUSADO;
            case "cancelled", "refunded", "charged_back" -> PedidoStatus.CANCELADO;
            default -> PedidoStatus.ERRO;
        };
    }

    public String urlFrontendRetorno(Long pedidoId, String resultado) {
        String resultadoSeguro = switch (resultado) {
            case "sucesso", "pendente", "falha" -> resultado;
            default -> "falha";
        };
        return frontendUrl + "/carrinho?pagamento=" + resultadoSeguro + "&pedido=" + pedidoId;
    }

    private String urlRetorno(Pedido pedido, String resultado, boolean retornoPublico) {
        if (!retornoPublico) return urlFrontendRetorno(pedido.getId(), resultado);
        int apiIndex = webhookUrl.indexOf("/api/");
        String publicBaseUrl = apiIndex > 0 ? webhookUrl.substring(0, apiIndex) : webhookUrl.replaceAll("/$", "");
        return publicBaseUrl + "/api/pagamentos/retorno/" + resultado + "/" + pedido.getId();
    }

    private void validarAccessToken() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure a variável de ambiente MERCADO_PAGO_ACCESS_TOKEN.");
        }
    }

    private Cliente buscarClienteAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cliente não autenticado.");
        }
        return clienteRepository.findByEmail(authentication.getName().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas clientes podem pagar."));
    }

    private record PreferenciaResponse(String id, @JsonProperty("init_point") String initPoint,
                                       @JsonProperty("sandbox_init_point") String sandboxInitPoint) {}
    private record PagamentoResponse(Long id, String status,
                                     @JsonProperty("external_reference") String externalReference,
                                     @JsonProperty("transaction_amount") BigDecimal transactionAmount) {}
}
