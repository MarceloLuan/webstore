package com.webstore.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.webstore.backend.controller.dto.CheckoutResponse;
import com.webstore.backend.controller.dto.CheckoutRequest;
import com.webstore.backend.controller.dto.PedidoStatusResponse;
import com.webstore.backend.controller.dto.PedidoResponse;
import com.webstore.backend.controller.dto.ItemPedidoResponse;
import com.webstore.backend.controller.dto.TentativaPagamentoResponse;
import com.webstore.backend.model.*;
import com.webstore.backend.repository.CarrinhoRepository;
import com.webstore.backend.repository.ClienteRepository;
import com.webstore.backend.repository.PedidoRepository;
import com.webstore.backend.repository.TentativaPagamentoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class PagamentoService {
    private final CarrinhoRepository carrinhoRepository;
    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final TentativaPagamentoRepository tentativaRepository;
    private final ReservaEstoqueService reservas;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PagamentoService.class);
    private final RestClient mercadoPagoClient;
    private final TransactionTemplate checkoutTransaction;
    private final EntityManager entityManager;
    private final String accessToken;
    private final String webhookSecret;
    private final String webhookUrl;
    private final String frontendUrl;

    public PagamentoService(CarrinhoRepository carrinhoRepository,
                            ClienteRepository clienteRepository,
                            PedidoRepository pedidoRepository,
                            TentativaPagamentoRepository tentativaRepository,
                            ReservaEstoqueService reservas,
                            RestClient.Builder restClientBuilder,
                            PlatformTransactionManager transactionManager,
                            EntityManager entityManager,
                            @Value("${mercadopago.access-token:}") String accessToken,
                            @Value("${mercadopago.webhook-secret:}") String webhookSecret,
                            @Value("${app.webhook-url:}") String webhookUrl,
                            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.carrinhoRepository = carrinhoRepository;
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.tentativaRepository = tentativaRepository;
        this.reservas = reservas;
        this.mercadoPagoClient = restClientBuilder.baseUrl("https://api.mercadopago.com").build();
        this.checkoutTransaction = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
        this.accessToken = accessToken;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.webhookUrl = webhookUrl;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
    }

    public CheckoutResponse criarCheckout(CheckoutRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a modalidade de recebimento.");
        EnderecoEntrega endereco = request.validarEndereco();
        validarAccessToken();
        reservas.expirarVencidas();
        return executarCheckout(checkoutTransaction.execute(status -> prepararCompra(request.modalidade(), endereco)));
    }

    private CheckoutPreparado prepararCompra(ModalidadeRecebimento modalidade, EnderecoEntrega endereco) {
        Cliente cliente = buscarClienteAutenticado();
        // Serializa a decisão de criar/reutilizar entre abas e instâncias da aplicação.
        clienteRepository.findForCheckoutById(cliente.getId()).orElseThrow();
        Carrinho carrinho = carrinhoRepository.findByClienteId(cliente.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "O carrinho está vazio."));
        if (carrinho.getItens().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O carrinho está vazio.");
        }

        Pedido pedido = criarPedido(cliente, carrinho);
        pedido.definirRecebimento(modalidade, endereco);
        String fingerprint = fingerprint(cliente, carrinho, pedido);
        Optional<Long> existente = pedidoRepository.findIdByCheckoutFingerprint(fingerprint);
        if (existente.isPresent()) {
            Pedido anterior = pedidoRepository.findWithItensForUpdateById(existente.get()).orElseThrow();
            if (anterior.isEstoqueBaixado() || anterior.getTentativaConcluida() != null
                    || anterior.getStatus() == PedidoStatus.PAGO || anterior.getStatus() == PedidoStatus.CANCELADO) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta compra já foi encerrada. Consulte seus pedidos.");
            }
            if (anterior.getStatus() == PedidoStatus.PENDENTE) throw checkoutEmConfirmacao();
            List<TentativaPagamento> tentativas = tentativaRepository.findAllByPedidoIdOrderByIdDesc(anterior.getId());
            if (tentativas.isEmpty()) throw checkoutEmConfirmacao();
            TentativaPagamento tentativa = tentativas.get(0);
            if (tentativa.getCheckoutUrl() == null) throw checkoutEmConfirmacao();
            if (!reservas.ativa(anterior)) throw reservaVencida();
            return new CheckoutPreparado(anterior.getId(), tentativa.getId(), null,
                    new CheckoutResponse(tentativa.getCheckoutUrl(), tentativa.getPreferenceId(), anterior.getId()));
        }
        pedido.setCheckoutFingerprint(fingerprint);
        // Bloquear variações antes dos INSERTs de itens evita upgrades de FK locks no PostgreSQL.
        if (!reservas.reservar(pedido)) throw estoqueIndisponivel();
        pedidoRepository.saveAndFlush(pedido);
        return prepararTentativa(pedido);
    }

    public CheckoutResponse tentarNovamente(Long pedidoId) {
        validarAccessToken();
        reservas.expirarVencidas();
        return executarCheckout(checkoutTransaction.execute(status -> prepararRetomada(pedidoId)));
    }

    private CheckoutPreparado prepararRetomada(Long pedidoId) {
        Pedido pedido = buscarPedidoDoClienteComLock(pedidoId);
        importarTentativaLegada(pedido);
        if (pedido.getModalidade() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Pedido antigo sem modalidade de recebimento. Consulte a loja antes de tentar um novo pagamento.");
        }
        if (pedido.isEstoqueBaixado() || pedido.getTentativaConcluida() != null
                || pedido.getStatus() == PedidoStatus.PAGO || pedido.getStatus() == PedidoStatus.CANCELADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido não permite novo pagamento.");
        }
        List<TentativaPagamento> anteriores = tentativaRepository.findAllByPedidoIdOrderByIdDesc(pedidoId);
        if (anteriores.stream().anyMatch(t -> "checkout_processing".equals(t.getStatusProvedor())
                || "checkout_unknown".equals(t.getStatusProvedor()))) {
            throw checkoutEmConfirmacao();
        }
        if (anteriores.stream().anyMatch(t -> t.getStatus() == PedidoStatus.PENDENTE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aguarde a confirmação do pagamento pendente.");
        }
        for (TentativaPagamento tentativa : anteriores) {
            if (tentativa.getStatus() == PedidoStatus.AGUARDANDO_PAGAMENTO && tentativa.getCheckoutUrl() != null
                    && reservas.ativa(pedido)) {
                return new CheckoutPreparado(pedidoId, tentativa.getId(), null,
                        new CheckoutResponse(tentativa.getCheckoutUrl(), tentativa.getPreferenceId(), pedidoId));
            }
        }
        for (ItemPedido item : pedido.getItens()) {
            ProdutoTamanho variacao = item.getProdutoTamanho();
            if (Boolean.FALSE.equals(variacao.getAtivo()) || Boolean.FALSE.equals(variacao.getProduto().getAtivo())
                    || variacao.getQuantidade() == null || variacao.getQuantidade() < item.getQuantidade()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto indisponível para nova tentativa.");
            }
        }
        return prepararTentativa(pedido);
    }

    @Transactional
    public List<TentativaPagamentoResponse> listarTentativas(Long pedidoId) {
        Pedido pedido = buscarPedidoDoClienteComLock(pedidoId);
        importarTentativaLegada(pedido);
        Long concluidaId = pedido.getTentativaConcluida() == null ? null : pedido.getTentativaConcluida().getId();
        return tentativaRepository.findAllByPedidoIdOrderByIdDesc(pedidoId).stream()
                .map(t -> new TentativaPagamentoResponse(t.getId(), t.getStatus(), t.getStatusProvedor(),
                        t.getId().equals(concluidaId), t.isAprovacaoDuplicada(), t.getCriadoEm(), t.getAtualizadoEm()))
                .toList();
    }

    private Pedido buscarPedidoDoClienteComLock(Long pedidoId) {
        Cliente cliente = buscarClienteAutenticado();
        Pedido pedido = pedidoRepository.findWithItensForUpdateById(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (!pedido.getCliente().getId().equals(cliente.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        }
        return pedido;
    }

    private CheckoutPreparado prepararTentativa(Pedido pedido) {
        if (!reservas.reservar(pedido)) throw estoqueIndisponivel();
        TentativaPagamento tentativa = new TentativaPagamento();
        tentativa.setPedido(pedido);
        tentativa.setStatusProvedor("checkout_processing");
        tentativaRepository.saveAndFlush(tentativa);
        Cliente cliente = pedido.getCliente();
        Map<String, Object> preference = new HashMap<>();
        preference.put("items", criarItensMercadoPago(pedido));
        preference.put("payer", Map.of("name", cliente.getNome(), "email", cliente.getEmail()));
        preference.put("external_reference", "tentativa-" + tentativa.getId());
        boolean retornoPublico = webhookUrl != null && !webhookUrl.isBlank();
        preference.put("back_urls", Map.of(
                "success", urlRetorno(pedido, "sucesso", retornoPublico),
                "pending", urlRetorno(pedido, "pendente", retornoPublico),
                "failure", urlRetorno(pedido, "falha", retornoPublico)));
        if (retornoPublico) preference.put("auto_return", "approved");
        if (webhookUrl != null && !webhookUrl.isBlank()) preference.put("notification_url", webhookUrl);

        // A transação confirma pedido e tentativa ANTES de qualquer chamada externa.
        return new CheckoutPreparado(pedido.getId(), tentativa.getId(), preference, null);
    }

    private CheckoutResponse executarCheckout(CheckoutPreparado preparado) {
        if (preparado.resposta() != null) return preparado.resposta();
        PreferenciaResponse response;
        String checkoutUrl;

        try {
            response = mercadoPagoClient.post().uri("/checkout/preferences")
                    .header("Authorization", "Bearer " + accessToken).body(preparado.preference())
                    .retrieve().body(PreferenciaResponse.class);
            if (response == null || response.id() == null) {
                throw new RestClientException("Resposta de checkout inválida.");
            }
            checkoutUrl = response.sandboxInitPoint() != null ? response.sandboxInitPoint() : response.initPoint();
            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                throw new RestClientException("URL de checkout ausente.");
            }
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            registrarFalhaCheckout(preparado, Set.of(400, 401, 403, 404, 422).contains(status));
            throw new FalhaCheckoutException(exception);
        } catch (RestClientException exception) {
            registrarFalhaCheckout(preparado, false);
            throw new FalhaCheckoutException(exception);
        }
        CheckoutResponse finalizada = checkoutTransaction.execute(status -> {
            // OSIV pode manter o snapshot da primeira transação nesta requisição.
            entityManager.clear();
            Pedido pedido = pedidoRepository.findWithItensForUpdateById(preparado.pedidoId()).orElseThrow();
            TentativaPagamento tentativa = tentativaRepository.findById(preparado.tentativaId()).orElseThrow();
            pedido.setMercadoPagoPreferenceId(response.id());
            tentativa.setPreferenceId(response.id());
            tentativa.setCheckoutUrl(checkoutUrl);
            // Um webhook pode ter confirmado o pagamento antes desta resposta HTTP.
            if ("checkout_processing".equals(tentativa.getStatusProvedor())) {
                tentativa.setStatusProvedor(null);
                if (pedido.getTentativaConcluida() == null && !pedido.isEstoqueBaixado()) {
                    pedido.setStatus(PedidoStatus.AGUARDANDO_PAGAMENTO);
                }
            }
            if (pedido.getTentativaConcluida() != null || pedido.isEstoqueBaixado()) return null;
            if (!reservas.ativa(pedido)) return null;
            return new CheckoutResponse(checkoutUrl, response.id(), pedido.getId());
        });
        if (finalizada == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Consulte seus pedidos: a compra foi concluída ou o prazo da reserva terminou.");
        }
        return finalizada;
    }

    private void registrarFalhaCheckout(CheckoutPreparado preparado, boolean falhaDefinitiva) {
        checkoutTransaction.executeWithoutResult(status -> {
            entityManager.clear();
            Pedido pedido = pedidoRepository.findWithItensForUpdateById(preparado.pedidoId()).orElseThrow();
            TentativaPagamento tentativa = tentativaRepository.findById(preparado.tentativaId()).orElseThrow();
            if (!"checkout_processing".equals(tentativa.getStatusProvedor())) return;
            tentativa.setStatus(PedidoStatus.ERRO);
            tentativa.setStatusProvedor(falhaDefinitiva ? "checkout_error" : "checkout_unknown");
            if (pedido.getTentativaConcluida() == null && !pedido.isEstoqueBaixado()) {
                pedido.setStatus(PedidoStatus.ERRO);
                if (falhaDefinitiva) liberarSeSemTentativaAberta(pedido);
            }
        });
    }

    private ResponseStatusException checkoutEmConfirmacao() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe uma solicitação para esta compra. Consulte Meus pedidos e aguarde a confirmação antes de tentar novamente.");
    }

    private ResponseStatusException estoqueIndisponivel() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Estoque indisponível: outra compra pode ter reservado estas peças. Revise o carrinho.");
    }

    private ResponseStatusException reservaVencida() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A reserva terminou. Acesse Meus pedidos para verificar a disponibilidade e tentar novamente.");
    }

    private String fingerprint(Cliente cliente, Carrinho carrinho, Pedido snapshot) {
        Map<Long, BigDecimal> precos = new HashMap<>();
        snapshot.getItens().forEach(item -> precos.put(item.getProdutoTamanho().getId(), item.getPrecoUnitario()));
        StringBuilder base = new StringBuilder("v2:").append(cliente.getId()).append(':').append(carrinho.getId());
        base.append(':').append(snapshot.getModalidade());
        EnderecoEntrega endereco = snapshot.getEnderecoEntrega();
        if (endereco != null) {
            for (String campo : new String[]{endereco.destinatario(), endereco.cep(), endereco.rua(), endereco.numero(),
                    endereco.complemento(), endereco.bairro(), endereco.cidade(), endereco.uf()}) {
                String valor = campo == null ? "" : campo;
                base.append('|').append(valor.length()).append(':').append(valor);
            }
        }
        carrinho.getItens().stream().sorted(Comparator.comparing(ItemCarrinho::getId)).forEach(item -> base
                .append('|').append(item.getId()).append(':').append(item.getProdutoTamanho().getId())
                .append(':').append(item.getQuantidade()).append(':')
                .append(precos.get(item.getProdutoTamanho().getId()).stripTrailingZeros().toPlainString()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(base.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record CheckoutPreparado(Long pedidoId, Long tentativaId, Map<String, Object> preference,
                                    CheckoutResponse resposta) {}

    private static class FalhaCheckoutException extends ResponseStatusException {
        FalhaCheckoutException(Exception cause) {
            super(HttpStatus.BAD_GATEWAY, "Não foi possível confirmar a criação do checkout. Consulte Meus pedidos antes de repetir o pagamento.", cause);
        }
    }

    @Transactional(readOnly = true)
    public PedidoStatusResponse buscarPedido(Long pedidoId) {
        Cliente cliente = buscarClienteAutenticado();
        Pedido pedido = pedidoRepository.findByIdAndClienteId(pedidoId, cliente.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        return new PedidoStatusResponse(pedido.getId(), pedido.getStatus(), pedido.getTotal(), pedido.getReservaStatus(), pedido.getReservaExpiraEm(), pedido.getModalidade(), pedido.getEnderecoEntrega());
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
                                .toList(), pedido.getReservaStatus(), pedido.getReservaExpiraEm(), pedido.getModalidade(), pedido.getEnderecoEntrega()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PedidoStatusResponse> listarPendenciasEstoque() {
        return pedidoRepository.findAllByStatusOrderByCriadoEmDesc(PedidoStatus.PAGO_EM_REVISAO).stream()
                .map(p -> new PedidoStatusResponse(p.getId(), p.getStatus(), p.getTotal(), p.getReservaStatus(), p.getReservaExpiraEm(), p.getModalidade(), p.getEnderecoEntrega()))
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
        if (pagamento == null || pagamento.id() == null || !String.valueOf(pagamento.id()).equals(dataId)
                || pagamento.externalReference() == null
                || !(pagamento.externalReference().startsWith("pedido-")
                || pagamento.externalReference().startsWith("tentativa-"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pagamento sem referência de pedido válida.");
        }
        Long pedidoId;
        Long origemId = null;
        try {
            if (pagamento.externalReference().startsWith("tentativa-")) {
                origemId = Long.valueOf(pagamento.externalReference().substring("tentativa-".length()));
                pedidoId = tentativaRepository.buscarPedidoId(origemId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tentativa não encontrada."));
            } else {
                pedidoId = Long.valueOf(pagamento.externalReference().substring("pedido-".length()));
            }
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referência de pedido inválida.");
        }
        Pedido pedido = pedidoRepository.findWithItensForUpdateById(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if (pagamento.transactionAmount() == null || pedido.getTotal().compareTo(pagamento.transactionAmount()) != 0
                || !"BRL".equals(pagamento.currencyId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O valor do pagamento diverge do pedido.");
        }
        String paymentId = String.valueOf(pagamento.id());
        importarTentativaLegada(pedido);
        TentativaPagamento tentativa = tentativaRepository.findByPaymentId(paymentId).orElse(null);
        if (tentativa != null && !tentativa.getPedido().getId().equals(pedidoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pagamento vinculado a outro pedido.");
        }
        if (tentativa == null) {
            TentativaPagamento origem = origemId == null ? null : tentativaRepository.findById(origemId).orElseThrow();
            if (origem != null && origem.getPaymentId() == null) {
                tentativa = origem;
            } else {
                // Um mesmo checkout do provedor pode gerar mais de um payment_id.
                tentativa = new TentativaPagamento();
                tentativa.setPedido(pedido);
                tentativa.setPreferenceId(origem == null ? pedido.getMercadoPagoPreferenceId() : origem.getPreferenceId());
            }
            tentativa.setPaymentId(paymentId);
        }
        PedidoStatus novoStatus = TransicoesPagamento.mapear(pagamento.status(), pagamento.statusDetail());
        if (novoStatus == null) {
            log.error("Estado desconhecido do provedor: pedido={}, pagamento={}, status={}", pedidoId, paymentId, pagamento.status());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Estado de pagamento desconhecido; requer conciliação.");
        }
        if (!TransicoesPagamento.permite(tentativa.getStatus(), novoStatus,
                tentativa.getAtualizadoProvedorEm(), pagamento.dateLastUpdated())) return;
        tentativa.setStatus(novoStatus);
        tentativa.setStatusProvedor(pagamento.status());
        tentativa.setDetalheStatusProvedor(pagamento.statusDetail());
        if (pagamento.dateLastUpdated() != null) tentativa.setAtualizadoProvedorEm(pagamento.dateLastUpdated());
        tentativaRepository.saveAndFlush(tentativa);
        TentativaPagamento concluida = pedido.getTentativaConcluida();
        if (concluida != null) {
            if (!concluida.getId().equals(tentativa.getId())) {
                if (novoStatus == PedidoStatus.PAGO) tentativa.setAprovacaoDuplicada(true);
                return;
            }
            // Apenas a tentativa vencedora pode atualizar o estado financeiro após a conclusão.
            if (novoStatus != PedidoStatus.PAGO) pedido.setStatus(novoStatus);
            else pedido.setStatus(pedido.isEstoqueBaixado() ? PedidoStatus.PAGO : PedidoStatus.PAGO_EM_REVISAO);
            pedido.setMercadoPagoStatus(pagamento.status());
            return;
        }
        if (novoStatus == PedidoStatus.PAGO && !pedido.isEstoqueBaixado()) {
            boolean estoqueConfirmado = reservas.consumir(pedido);
            if (estoqueConfirmado) {
                removerItensPagosDoCarrinho(pedido);
                pedido.setEstoqueBaixado(true);
            } else {
                log.error("Pagamento aprovado exige conciliação de estoque: pedido={}, pagamento={}", pedidoId, paymentId);
            }
            pedido.setTentativaConcluida(tentativa);
            pedido.setMercadoPagoPaymentId(paymentId);
            pedido.setMercadoPagoStatus(pagamento.status());
            pedido.setStatus(estoqueConfirmado ? PedidoStatus.PAGO : PedidoStatus.PAGO_EM_REVISAO);
        } else if (Set.of(PedidoStatus.REEMBOLSADO, PedidoStatus.REEMBOLSADO_PARCIAL,
                PedidoStatus.CHARGEBACK, PedidoStatus.EM_MEDIACAO).contains(novoStatus)) {
            // A consulta pode já mostrar um evento pós-aprovação. Não presumir entrega.
            pedido.setTentativaConcluida(tentativa);
            pedido.setMercadoPagoPaymentId(paymentId);
            pedido.setMercadoPagoStatus(pagamento.status());
            pedido.setStatus(novoStatus);
            reservas.liberar(pedido);
        } else {
            List<TentativaPagamento> todas = tentativaRepository.findAllByPedidoIdOrderByIdDesc(pedidoId);
            // Uma recusa antiga não substitui uma tentativa nova ou pendente.
            PedidoStatus resumo = todas.stream().anyMatch(t -> t.getStatus() == PedidoStatus.PENDENTE)
                    ? PedidoStatus.PENDENTE : todas.get(0).getStatus();
            pedido.setStatus(resumo);
            liberarSeSemTentativaAberta(pedido);
        }
        pedidoRepository.save(pedido);
    }

    private void importarTentativaLegada(Pedido pedido) {
        if (!tentativaRepository.findAllByPedidoIdOrderByIdDesc(pedido.getId()).isEmpty()) return;
        if (pedido.getMercadoPagoPaymentId() == null && pedido.getMercadoPagoPreferenceId() == null) return;
        TentativaPagamento legada = new TentativaPagamento();
        legada.setPedido(pedido);
        legada.setPaymentId(pedido.getMercadoPagoPaymentId());
        legada.setPreferenceId(pedido.getMercadoPagoPreferenceId());
        legada.setStatusProvedor(pedido.getMercadoPagoStatus());
        PedidoStatus financeiro = TransicoesPagamento.mapear(pedido.getMercadoPagoStatus(), null);
        legada.setStatus(financeiro == null ? pedido.getStatus() : financeiro);
        tentativaRepository.saveAndFlush(legada);
        if (pedido.isEstoqueBaixado() || pedido.getStatus() == PedidoStatus.PAGO) {
            pedido.setTentativaConcluida(legada);
        }
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

    private void liberarSeSemTentativaAberta(Pedido pedido) {
        boolean aberta = tentativaRepository.findAllByPedidoIdOrderByIdDesc(pedido.getId()).stream().anyMatch(t ->
                t.getStatus() == PedidoStatus.PENDENTE || t.getStatus() == PedidoStatus.AGUARDANDO_PAGAMENTO
                || "checkout_unknown".equals(t.getStatusProvedor()) || "checkout_processing".equals(t.getStatusProvedor()));
        if (!aberta && pedido.getTentativaConcluida() == null) reservas.liberar(pedido);
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
                                     @JsonProperty("status_detail") String statusDetail,
                                     @JsonProperty("date_last_updated") java.time.Instant dateLastUpdated,
                                     @JsonProperty("external_reference") String externalReference,
                                     @JsonProperty("currency_id") String currencyId,
                                     @JsonProperty("transaction_amount") BigDecimal transactionAmount) {}
}
