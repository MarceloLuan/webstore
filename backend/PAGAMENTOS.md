# Pedido e tentativas de pagamento

## Modelo

O pedido preserva cliente, itens e preços da compra. `tentativas_pagamento` mantém
um registro por tentativa de checkout/pagamento: pedido, preferência, `payment_id`
único quando conhecido, status normalizado e do provedor, URL de checkout, datas
e indicador de aprovação duplicada. O status de uma tentativa acompanha o estado
atual desse pagamento; esta tabela não é um log de todos os eventos de webhook.

`pedidos.tentativa_concluida_id` identifica a única tentativa aceita para concluir
a compra. `estoque_baixado` continua sendo preservado. Ambos são atualizados na
mesma transação que a baixa e a remoção dos itens pagos do carrinho.

Os novos checkouts usam `external_reference=tentativa-<id>`. Se o Mercado Pago
gerar outro `payment_id` dentro do mesmo checkout, ele ganha um registro separado,
com a mesma preferência, sem apagar a tentativa anterior.

## Endpoints

| Método | Caminho | Comportamento |
| --- | --- | --- |
| POST | `/api/pagamentos/checkout` | Inicia ou recupera a compra do mesmo snapshot do carrinho |
| POST | `/api/pagamentos/pedidos/{id}/tentativas` | Retoma o pedido original; devolve `checkoutUrl`, `preferenceId`, `pedidoId` |
| GET | `/api/pagamentos/pedidos/{id}/tentativas` | Histórico de tentativas do próprio cliente, incluindo qual concluiu e duplicidades |
| GET | `/api/pagamentos/pedidos` | Mantido: histórico de pedidos |
| GET | `/api/pagamentos/pedidos/{id}` | Mantido: status do pedido |
| POST | `/api/pagamentos/webhook` | Mantido: assinatura validada e pagamento consultado no provedor |
| GET | `/api/pagamentos/retorno/{resultado}/{id}` | Mantido: consulta ao provedor antes do redirecionamento |

Os endpoints de tentativas exigem CLIENTE e propriedade do pedido. Tentativas de
outro cliente retornam 404. O histórico não expõe segredos nem a URL de pagamento.

## Regras de retomada e confirmação

- Recusa e erro definitivo de checkout permitem outra tentativa usando o mesmo pedido,
  itens e preços originais, com nova validação de disponibilidade.
- Uma URL de checkout ainda aberta é reutilizada, inclusive em POSTs repetidos.
- Pagamento pendente bloqueia criação de outro checkout até sua resolução.
- Pedido pago, já concluído ou cancelado não aceita outro checkout.
- A sincronização bloqueia a linha do pedido; só uma tentativa pode vencer,
  mesmo com aprovações concorrentes.
- ID, referência, valor e moeda BRL são conferidos na resposta do provedor.
- A baixa consome a reserva sob bloqueios das variações em ordem estável.
  Sem reserva válida, registra o pagamento como PAGO_EM_REVISAO, sem baixa indevida.
- Eventos repetidos não repetem a baixa. Eventos de outras tentativas não
  desfazem uma compra concluída. Estado pago não regride para pendente/recusado;
  estado cancelado de uma tentativa não é reaberto por evento posterior.
- Segunda aprovação é registrada com `aprovacaoDuplicada=true`, sem nova baixa
  ou alteração da tentativa vencedora. Não há estorno automático: a cobrança
  adicional deve ser conciliada/reembolsada no provedor.
- Falhas HTTP na criação de checkout são persistidas como tentativa ERRO antes
  de responder 502. Uma falha de resultado incerto bloqueia novos envios até
  confirmação; não equivale a uma recusa de pagamento.

## Deduplicação de checkout

O backend não depende de um identificador gerado pelo navegador. Calcula SHA-256
com versão da assinatura, cliente, carrinho, IDs das linhas, variações,
quantidades e preços normalizados. Salva o resultado em
`pedidos.checkout_fingerprint`, com restrição única. Uma trava de banco por cliente
serializa a consulta/criação, inclusive entre processos/instâncias diferentes.

- Mesmo cliente e snapshot retornam o pedido e a preferência já persistidos.
  Isso cobre POST repetido, duplo clique, duas abas e resposta perdida pelo cliente.
- Se a primeira chamada ainda estiver em andamento, a repetição retorna 409
  sem chamar o Mercado Pago. Após conclusão, repetir recupera a resposta salva.
- Se a primeira chamada falhou, repetir `/checkout` não cria outra tentativa.
  Retomadas explícitas continuam usando `/pedidos/{id}/tentativas` e suas regras.
- Pagamento pendente, pedido pago ou cancelado não reabre o checkout.
- Alterar itens, quantidades ou preços muda o snapshot e representa outra compra.
  A preferência anterior não é cancelada automaticamente. Para retomar uma compra
  específica, usar a ação em “Meus pedidos”.
- Após pagamento, recolocar os produtos cria novas linhas no carrinho e permite
  uma compra legítima. A deduplicação não usa uma janela arbitrária de tempo.
- Clientes diferentes nunca compartilham a compra ou sua URL de pagamento.

### Falha de comunicação e persistência

A criação ocorre em duas transações locais, com a chamada externa entre elas:

1. Gravar pedido, assinatura e tentativa com `statusProvedor=checkout_processing`;
   confirmar a transação antes de enviar qualquer pedido ao provedor.
2. Chamar o Mercado Pago uma única vez para essa tentativa.
3. Persistir preferência/URL em nova transação, protegida pelo lock do pedido.
   Uma confirmação recebida enquanto a chamada estava em andamento não regride.

Em timeout, HTTP 5xx ou resposta inválida, fica `checkout_unknown`. Uma interrupção
abrupta pode deixar `checkout_processing`. Ambos bloqueiam a criação de outra
preferência tanto em `/checkout` quanto na retomada. As falhas HTTP
400/401/403/404/422 são tratadas como recusas definitivas da criação e permitem
retomada explícita. O status de pagamento `rejected` também continua permitindo
nova tentativa.

Não presumimos que o endpoint de preferências deduplica requisições por um header.
No resultado incerto, preferimos bloquear a repetição a emitir outra preferência.
O webhook pode resolver uma tentativa usando `tentativa-<id>`, mesmo sem URL salva.
Se nenhuma notificação chegar, é necessária conciliação com o Mercado Pago:
localizar a preferência/pagamento pela referência antes de decidir a recuperação.
Não apagar pedidos ou limpar o marcador para liberar uma repetição sem confirmar
o que ocorreu no provedor. Ainda não existe uma ferramenta automática para essa
conciliação; um marcador incerto não expira automaticamente.

No frontend, a função de checkout ignora chamadas concorrentes e o botão fica
bloqueado durante a requisição e o redirecionamento. Erros liberam nova interação;
o backend continua protegendo a compra. Voltar pelo cache do navegador restaura
o botão via `pageshow`.

## Compatibilidade e banco

As colunas antigas de preferência/pagamento/status em `pedidos` são mantidas.
Pagamentos anteriores com referência `pedido-<id>` continuam reconhecidos. Ao
sincronizar, retomar ou consultar tentativas de um pedido legado, os dados antigos
disponíveis são importados uma vez sob lock. Para pedido já pago/estoque baixado,
essa tentativa é marcada como vencedora sem descontar estoque novamente.
As datas de registros importados representam a importação; o sistema antigo não
guardava todas as tentativas e esse histórico inexistente não pode ser reconstruído.

Na configuração atual (`ddl-auto=update`), Hibernate cria a tabela e a referência
aditiva ao iniciar o backend, além da coluna única `checkout_fingerprint`.
Não é necessário apagar pedidos ou recriar o banco.
Nenhum banco de desenvolvimento/produção foi alterado durante os testes. Para um
ambiente com migrations obrigatórias, transportar essas alterações aditivas para
uma migration revisada antes de publicar.

Pedidos anteriores à deduplicação ficam com assinatura nula: não há reconstrução
automática de IDs de linhas de carrinho já removidas. Para esses pedidos, retomar
por “Meus pedidos”; a deduplicação por snapshot aplica-se aos checkouts criados
após esta alteração.

## Validação e limites

`PagamentoIntegrationTests` usa HTTP simulado e H2, exercitando transações reais,
recusa → nova tentativa → aprovação, mesmo checkout com IDs distintos, callback
repetido, webhook assinado, duas aprovações concorrentes, falha no checkout,
legado, propriedade dos endpoints, valor divergente e snapshot dos preços.
Também cobre POSTs repetidos, chamadas sobrepostas de duas abas, resposta perdida,
timeout, erro 5xx, interrupção após envio, isolamento entre clientes, mudança de
carrinho e nova compra legítima após pagamento.
Execute `./mvnw.cmd test` a partir de `backend/`; não requer token real nem cobra.

Ainda é necessário homologar com o Mercado Pago real. A reserva temporária existe
no checkout e expira por prazo próprio. Aprovação sem reserva válida resulta em
`PAGO_EM_REVISAO`, sem baixa indevida. Não há reembolso automático.

## Estados financeiros e transições

Por compatibilidade, `PAGO` continua significando pagamento aprovado na API.
`PAGO_EM_REVISAO` é uma situação operacional do pedido, não um status do provedor.
Os estados financeiros da tentativa são independentes de `reserva_status`.

| Provedor (`/v1/payments`) | Estado local |
| --- | --- |
| pending, in_process, authorized | PENDENTE |
| approved | PAGO |
| approved / partially_refunded | REEMBOLSADO_PARCIAL |
| rejected | RECUSADO |
| cancelled | CANCELADO |
| cancelled / expired | EXPIRADO |
| refunded | REEMBOLSADO |
| charged_back | CHARGEBACK |
| in_mediation | EM_MEDIACAO |

O segundo valor, quando presente, é `status_detail`. Mapeamento baseado na
[documentação Payments do Mercado Pago](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-payments/response-handling/query-results?scope=prod).
Estado desconhecido retorna 502 e gera log para conciliação; a transação não muda
o pagamento nem libera estoque. Não se presume cancelamento ou erro definitivo.

| Estado atual por payment_id | Próximos estados diferentes permitidos |
| --- | --- |
| AGUARDANDO_PAGAMENTO, ERRO, PENDENTE | Qualquer estado financeiro reconhecido |
| PAGO | REEMBOLSADO_PARCIAL, REEMBOLSADO, CHARGEBACK, EM_MEDIACAO |
| REEMBOLSADO_PARCIAL | REEMBOLSADO, CHARGEBACK, EM_MEDIACAO |
| EM_MEDIACAO | REEMBOLSADO, CHARGEBACK; PAGO/PARCIAL somente com data comprovadamente posterior |
| RECUSADO, CANCELADO, EXPIRADO, REEMBOLSADO, CHARGEBACK | Nenhum; somente repetição do próprio estado |

Persistimos `status_detail` e `date_last_updated` na tentativa. Respostas anteriores
à versão aceita são ignoradas; versões iguais com estados diferentes também.
Na ausência de datas, a matriz conservadora continua impedindo regressões.
Uma nova tentativa após recusa usa outro `payment_id`; não reabre o pagamento
recusado. A expiração local da reserva não muda o estado financeiro para EXPIRADO.

O webhook continua validando assinatura e consultando o pagamento no provedor.
Lock do pedido, tentativa vencedora e marcador `estoque_baixado` preservam a
idempotência. Só a vencedora altera o resumo financeiro após a conclusão;
reembolso de uma cobrança duplicada não cancela o pedido pago pela vencedora.
Reembolso/chargeback não repõem mercadoria: uma devolução física requer outro
processo. Quando a primeira consulta já mostra reembolso, chargeback, parcial ou
mediação, o pagamento fica registrado e bloqueia nova cobrança sem inventar uma
baixa histórica; é necessária conciliação operacional.

O atualizador de schema amplia os CHECKs de `pedidos` e `tentativas_pagamento`.
Registros CANCELADO com status bruto refunded/charged_back são reclassificados.
Sem status bruto preservado, não é possível inferir retrospectivamente o motivo.
As novas colunas são aditivas, criadas pelo Hibernate na configuração atual.
