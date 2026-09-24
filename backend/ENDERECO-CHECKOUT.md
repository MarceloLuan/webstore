# Recebimento e endereço da compra

`POST /api/pagamentos/checkout` exige modalidade explícita. Sem corpo, modalidade
ausente ou desconhecida, responde HTTP 400 antes de reservar estoque ou chamar o
provedor. O frontend exige a seleção e mostra o formulário somente para entrega.

```json
{
  "modalidade": "ENTREGA",
  "endereco": {
    "destinatario": "Maria Silva",
    "cep": "01310-100",
    "rua": "Avenida Paulista",
    "numero": "1578",
    "complemento": "Apartamento 12",
    "bairro": "Bela Vista",
    "cidade": "São Paulo",
    "uf": "SP"
  }
}
```

Para retirada: `{"modalidade":"RETIRADA"}`. Endereço informado junto com retirada
é rejeitado, para evitar gravar um destino que não será utilizado.

O resumo anterior ao pagamento mostra a modalidade e o destino. Na retirada,
`frontend/src/config/store.js` fornece o endereço cadastrado da loja, o WhatsApp
e `pickupInstructions`. Ajuste nessa lista as orientações e horários quando
definidos; o sistema não presume horários. Essas informações também aparecem em
Meus pedidos. Na entrega, o resumo usa os campos informados pelo cliente.
O botão de pagamento fica desabilitado sem modalidade válida; a API também
rejeita a ausência ou um valor desconhecido, independentemente da interface.

## Validação

Todos os campos da entrega são obrigatórios, exceto complemento. O backend
normaliza espaços nas extremidades, CEP sem hífen e UF em maiúsculas. CEP deve
ter oito dígitos (com ou sem hífen) e não pode ser 00000000. UF deve ser uma das
27 siglas brasileiras. Número é texto e aceita S/N. Limites: destinatário 120,
rua 160, número 20, complemento 120, bairro/cidade 100. Textos contendo caracteres
de controle são rejeitados. Campos obrigatórios contendo só espaços são inválidos.

O frontend aplica campos obrigatórios, limites e valida CEP/UF; o service repete
a validação independentemente do navegador. A validação é estrutural: não consulta
CEP, não comprova existência do endereço e não calcula cobertura de entrega.

## Persistência e compatibilidade

`Pedido` armazena `modalidade` e o embutido imutável `EnderecoEntrega`, nas colunas
`entrega_destinatario`, `entrega_cep`, `entrega_rua`, `entrega_numero`,
`entrega_complemento`, `entrega_bairro`, `entrega_cidade` e `entrega_uf`.
Não existe FK para perfil nem referência a um endereço mutável. As colunas não
participam de UPDATEs do ORM e o método de definição rejeita pedidos já salvos.
Alterar dados do cliente não muda o destinatário ou endereço da compra anterior.

Hibernate (`ddl-auto=update`) adiciona as colunas ao iniciar. O atualizador de
schema instala `ck_pedido_recebimento` no PostgreSQL: entrega exige campos
essenciais preenchidos e retirada não pode conter endereço. Pedidos anteriores
permanecem com modalidade/endereço nulos, sem inventar dados. Continuam consultáveis
e seus pagamentos existentes continuam sincronizáveis; nova tentativa de checkout
nesses pedidos exige atendimento da loja, sem presumir retirada ou entrega.

O contrato de entrada mudou: clientes antigos que enviavam `{}` devem atualizar
o checkout. O frontend deste projeto já foi atualizado. Os GETs de pedidos e
detalhe retornam `modalidade` e `enderecoEntrega`; o acesso continua restrito ao
proprietário (e ao endpoint administrativo já autorizado). Meus pedidos exibe a
cópia persistida, inclusive após recusa e retomada.

## Deduplicação e pagamento

A assinatura v2 do checkout inclui modalidade e todos os campos normalizados,
além do cliente e carrinho. A mesma requisição, inclusive CEP com/sem hífen e UF
com caixa diferente, reutiliza o pedido. Outro destino representa outra compra:
não altera o pedido anterior nem cancela sua preferência/reserva. Pedidos de
assinatura v1 devem ser retomados pelo histórico quando tiverem modalidade válida;
não há reconstrução de endereços antigos.

Retomar pagamento usa exclusivamente o endereço já gravado. O frontend não
envia endereço novo para `/pedidos/{id}/tentativas`. Não houve integração com
transportadora, consulta de CEP, prazo ou cobrança de frete. O pagamento continua
usando o total dos produtos e a tela informa que o frete não está incluído.

## Testes

`PagamentoIntegrationTests` cobre obrigatoriedade por campo, CEP/UF/limites,
ausência de efeitos colaterais em requisições inválidas, entrega via HTTP,
isolamento entre clientes, retirada, normalização, deduplicação por destino,
preservação do snapshot após alteração do cliente e nova tentativa, e legado.
Execute `./mvnw.cmd test` em `backend`. Os testes não fazem cobranças reais.
