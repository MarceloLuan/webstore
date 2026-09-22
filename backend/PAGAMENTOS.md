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
| POST | `/api/pagamentos/checkout` | Mantido: inicia compra a partir do carrinho |
| POST | `/api/pagamentos/pedidos/{id}/tentativas` | Retoma o pedido original; devolve `checkoutUrl`, `preferenceId`, `pedidoId` |
| GET | `/api/pagamentos/pedidos/{id}/tentativas` | Histórico de tentativas do próprio cliente, incluindo qual concluiu e duplicidades |
| GET | `/api/pagamentos/pedidos` | Mantido: histórico de pedidos |
| GET | `/api/pagamentos/pedidos/{id}` | Mantido: status do pedido |
| POST | `/api/pagamentos/webhook` | Mantido: assinatura validada e pagamento consultado no provedor |
| GET | `/api/pagamentos/retorno/{resultado}/{id}` | Mantido: consulta ao provedor antes do redirecionamento |

Os endpoints de tentativas exigem CLIENTE e propriedade do pedido. Tentativas de
outro cliente retornam 404. O histórico não expõe segredos nem a URL de pagamento.

## Regras de retomada e confirmação

- Recusa e erro de checkout permitem outra tentativa usando o mesmo pedido,
  itens e preços originais, com nova validação de disponibilidade.
- Uma URL de checkout ainda aberta é reutilizada, inclusive em POSTs repetidos.
- Pagamento pendente bloqueia criação de outro checkout até sua resolução.
- Pedido pago, já concluído ou cancelado não aceita outro checkout.
- A sincronização bloqueia a linha do pedido; só uma tentativa pode vencer,
  mesmo com aprovações concorrentes.
- ID, referência, valor e moeda BRL são conferidos na resposta do provedor.
- A baixa usa UPDATE condicional por variação em ordem estável, evitando
  subtração quando não houver estoque. Uma falha desfaz a transação inteira.
- Eventos repetidos não repetem a baixa. Eventos de outras tentativas não
  desfazem uma compra concluída. Estado pago não regride para pendente/recusado;
  estado cancelado de uma tentativa não é reaberto por evento posterior.
- Segunda aprovação é registrada com `aprovacaoDuplicada=true`, sem nova baixa
  ou alteração da tentativa vencedora. Não há estorno automático: a cobrança
  adicional deve ser conciliada/reembolsada no provedor.
- Falhas HTTP na criação de checkout são persistidas como tentativa ERRO antes
  de responder 502. O cliente encontra o pedido em “Meus pedidos” para retomar.

## Compatibilidade e banco

As colunas antigas de preferência/pagamento/status em `pedidos` são mantidas.
Pagamentos anteriores com referência `pedido-<id>` continuam reconhecidos. Ao
sincronizar, retomar ou consultar tentativas de um pedido legado, os dados antigos
disponíveis são importados uma vez sob lock. Para pedido já pago/estoque baixado,
essa tentativa é marcada como vencedora sem descontar estoque novamente.
As datas de registros importados representam a importação; o sistema antigo não
guardava todas as tentativas e esse histórico inexistente não pode ser reconstruído.

Na configuração atual (`ddl-auto=update`), Hibernate cria a tabela e a referência
aditiva ao iniciar o backend. Não é necessário apagar pedidos ou recriar o banco.
Nenhum banco de desenvolvimento/produção foi alterado durante os testes. Para um
ambiente com migrations obrigatórias, transportar essas alterações aditivas para
uma migration revisada antes de publicar.

## Validação e limites

`PagamentoIntegrationTests` usa HTTP simulado e H2, exercitando transações reais,
recusa → nova tentativa → aprovação, mesmo checkout com IDs distintos, callback
repetido, webhook assinado, duas aprovações concorrentes, falha no checkout,
legado, propriedade dos endpoints, valor divergente e snapshot dos preços.
Execute `./mvnw.cmd test` a partir de `backend/`; não requer token real nem cobra.

Ainda é necessário homologar com Mercado Pago e PostgreSQL. Não foi implementada
reserva de estoque: se o pagamento for aprovado sem estoque disponível, a
sincronização falha e exige reconciliação operacional, como no fluxo anterior.
Também não há fila automática de reembolsos nem deduplicação de compras distintas
iniciadas por repetidos POSTs no endpoint original `/checkout`. A proteção desta
etapa é por pedido e pela retomada em `/pedidos/{id}/tentativas`.
