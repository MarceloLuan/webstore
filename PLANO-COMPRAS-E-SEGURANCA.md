# Compras e segurança — diagnóstico de 19/09/2026

Análise do código atual, com execução de `backend/mvnw.cmd test`: 18 testes, zero falhas e zero erros, em H2 em memória. Não foram realizadas cobranças, testes externos de pagamento ou testes de concorrência em PostgreSQL. Não foram alteradas regras de negócio nesta análise.

## O que já existe

| Área | Implementado | Limite atual |
| --- | --- | --- |
| Vitrine | Catálogo, busca, filtros, detalhes, seleção de tamanho e carrossel das seis peças ativas mais recentes com estoque | Carrossel usa ID sequencial como ordem de cadastro |
| Administração de produtos | Cadastro, edição, inativação, código único, categoria, imagem e estoque por tamanho | Não há gestão operacional de pedidos |
| Carrinho | Persistido por cliente; adicionar, alterar, remover e limpar; validação de quantidade/estoque; preço calculado no servidor | Não reserva estoque |
| Checkout | Cria pedido com cópia de nome, tamanho, quantidade e preço; cria preferência no Mercado Pago; redireciona para pagamento | Cobrança inclui só mercadorias; falta endereço/frete e controle de novas tentativas |
| Confirmação | Webhook com HMAC; consulta pagamento diretamente no Mercado Pago; confere referência e valor; consulta de pedido restrita ao dono | Precisa homologação externa e tratamento completo dos eventos |
| Estoque após pagamento | Baixa ao aprovar; flag evita repetir a baixa do mesmo pedido; remove quantidades pagas do carrinho | Falta política para pagamento aprovado quando o estoque já acabou |
| Pedidos do cliente | Histórico com itens, data, total e status financeiro | Sem entrega, rastreio, retomada de pagamento ou atualização automática |
| Conta | Cadastro, login, perfil, edição, senha atual exigida para trocar senha, inativação, logout no perfil | Sem confirmação de e-mail, recuperação de senha ou Google |
| Autorização | BCrypt; JWT com expiração; permissões ADMIN/CLIENTE; rejeição de conta inativa | Revogação de sessão e proteção contra abuso incompletas |

## Antes de receber compras reais

### 1. Pagamentos e estoque — prioridade crítica

- **Definir ambiente do checkout.** `PagamentoService.java:90` prefere `sandbox_init_point` sempre que presente. Separar explicitamente a configuração de homologação e produção e validar o fluxo correspondente na documentação do Mercado Pago.
- **Permitir tentativas de pagamento corretamente.** `sincronizarPagamento` associa o primeiro ID de pagamento ao pedido, inclusive recusado, e rejeita outro ID. Uma segunda tentativa que gere outro pagamento pode não ser reconhecida. Modelar tentativas separadas do pedido, mantendo a baixa de estoque única.
- **Evitar checkouts duplicados.** Cada POST cria um pedido/preferência novos. Desabilitar o botão no navegador não cobre duas abas, repetição de requisição ou perda de resposta. Implementar deduplicação no servidor e regra explícita de retomada/expiração.
- **Resolver disponibilidade entre checkout e aprovação.** A validação inicial não reserva mercadoria. Dois clientes podem iniciar pagamentos da última peça; o segundo pode pagar e falhar na confirmação por falta de estoque. Definir reserva com prazo e liberação ou tratamento explícito de indisponibilidade/reembolso. Não deixar pagamento recebido oculto como pedido pendente por rollback.
- **Verificar concorrência no banco real.** Há lock e versão no pedido, mas não um mecanismo explícito de reserva/atualização condicional de estoque por variação. O alcance do lock com EntityGraph depende da consulta gerada; testar dois pedidos distintos concorrentes no PostgreSQL e garantir estoque consistente.
- **Persistir falhas de checkout.** O método transacional salva `ERRO` e lança `ResponseStatusException`; o rollback padrão pode desfazer tanto o pedido quanto o registro do erro. Separar o registro da tentativa e a chamada externa, prevendo timeout e preferência criada remotamente sem confirmação local.
- **Separar estados financeiros.** Reembolso, cancelamento e chargeback viram `CANCELADO`; mediação vira `PENDENTE`. Definir transições permitidas, reembolso parcial, devolução física e regras de reposição. Repor estoque exige uma decisão de negócio, não apenas receber um evento financeiro.
- **Resistir a eventos repetidos e fora de ordem.** Há proteção parcial para PAGO → PENDENTE/RECUSADO e baixa duplicada. Falta validar todos os estados terminais e reprocessamento após reembolso.
- **Conciliação e suporte.** Criar rotina para reconciliar pedidos pendentes quando webhook falhar; registrar eventos/tentativas, configurar timeouts e permitir reprocessamento controlado. Revisar assinatura, tolerância de timestamp/replay e identidade/moeda/ambiente do pagamento conforme a integração.
- **Mensagens e atualização.** O carrinho consulta o pedido uma vez ao montar; o histórico também. Adicionar atualização limitada/ação de atualizar e retomada segura. A mensagem “Avisaremos” ainda não corresponde a um envio de notificação implementado.
- **Erros amigáveis.** O corpo da resposta de erro do provedor é repassado ao cliente; manter detalhes em logs protegidos e exibir mensagem adequada com identificador de atendimento.

Evidências: `backend/src/main/java/com/webstore/backend/service/PagamentoService.java`, `repository/PedidoRepository.java`, `model/ProdutoTamanho.java`, `frontend/src/views/CarrinhoView.vue`, `MeusPedidosView.vue`.

### 2. Endereço, frete e entrega — obrigatório para envio

Hoje o carrinho exibe “Frete: Calculado depois”, mas não existe etapa posterior de cálculo implementada. O pedido não guarda endereço, modalidade, prazo ou custo de envio. O total enviado ao Mercado Pago soma apenas os produtos.

- Definir se a primeira versão terá retirada na loja, entrega local, envio por transportadora ou uma combinação.
- Para retirada: opção explícita antes de pagar, endereço/horário/instruções e status “pronto para retirada”.
- Para entrega: capturar destinatário, CEP, rua, número, complemento, bairro, cidade e UF; validar campos e permitir correção de endereço.
- Definir CEP de origem, área atendida e regras de preço. Para cotação por transportadora, cadastrar peso e dimensões de embalagem necessários.
- Cotar no backend; apresentar modalidade, valor e prazo antes do pagamento. Tratar CEP inválido, área não atendida e indisponibilidade do serviço.
- Salvar uma cópia do endereço e da cotação no pedido. Mudanças futuras no perfil não devem alterar uma compra já feita.
- Calcular o total final no backend: mercadorias + frete − descontos, caso descontos sejam implementados. Revalidar cotação ao alterar endereço/carrinho.
- Criar gestão administrativa de pedidos: recebido, em separação, enviado, entregue/retirado; registrar código de rastreio e comunicar o cliente.
- Separar status de entrega de status financeiro; um reembolso não descreve se a mercadoria já foi enviada.

Não é necessário contratar integração de transportadora para uma primeira versão exclusiva de retirada ou entrega local, mas a modalidade e o custo precisam estar definidos antes da cobrança.

### 3. Segurança de cadastro e sessão — antes da publicação

- **Segredos:** a configuração principal contém valores literais para senha do banco e chave JWT. Migrar para ambiente/gerenciador de segredos. Se esses valores foram compartilhados ou versionados, substituí-los; apenas apagar do arquivo não elimina o histórico. Valores não foram reproduzidos neste relatório.
- **Validação no backend:** cadastro e edição validam presença dos campos, mas não formato de e-mail/telefone nem uma política de comprimento de senha/campos. Implementar limites coerentes com BCrypt e banco, validação de formato e bloqueio de senhas comuns/comprometidas.
- **Verificação e recuperação:** confirmação de e-mail, reenvio com limite e recuperação de senha com token aleatório, temporário e de uso único. Respostas de recuperação devem evitar revelar se a conta existe.
- **Abuso:** limitar tentativas de login, cadastro, recuperação e reenvio; monitorar tentativas suspeitas. CAPTCHA pode complementar controles quando necessário, sem substituir limites no servidor.
- **Ações sensíveis:** a troca de e-mail atualmente não exige senha atual. Exigir reautenticação e confirmação do novo endereço; prever proteção para inativação e vinculação de identidades.
- **Revogação:** logout hoje limpa apenas o navegador. Uma cópia do JWT permanece válida até expirar; mudar a senha também não invalida tokens antigos no backend. Implementar versão de sessão/credencial ou registro revogável e testar efetivamente logout e troca/recuperação de senha.
- **Identidade estável:** o JWT usa e-mail como `subject`. Preferir ID imutável de usuário e versão de sessão; testar troca e reutilização de e-mail para que tokens anteriores nunca passem a identificar outra conta.
- **Armazenamento:** JWT está em localStorage, acessível a JavaScript. Avaliar sessão em cookie HttpOnly/Secure/SameSite com proteção CSRF correspondente; revisar CSP/XSS e não tratar a migração para cookie como uma mudança isolada.
- **Produção:** HTTPS, configuração restrita de origens quando houver domínios separados, logs sem credenciais/dados sensíveis e tratamento consistente de sessão expirada. O proxy do Vite é de desenvolvimento; configurar encaminhamento `/api` no servidor de produção.
- **Banco e operação:** migrations versionadas em lugar de depender de `ddl-auto=update`, backup e restauração testada, conta de banco com privilégios adequados, alertas de falha de pagamento.
- **Administrador:** avaliar segundo fator para acesso administrativo antes de operações reais sensíveis.

Evidências: `ClienteService.java`, `ContaService.java`, `security/JwtUtil.java`, `security/JwtAuthenticationFilter.java`, `config/SecurityConfig.java`, `frontend/src/services/auth.js`, `backend/src/main/resources/application.properties`.

### 4. Entrar com Google — melhoria posterior à base segura

Não há integração Google/OIDC no código atual. Pode reduzir o esforço de cadastro, mas não resolve autorização, sessão, frete ou pedidos.

- Configurar projeto e cliente web no Google, consentimento, origens e redirecionamentos autorizados.
- Usar Google Identity Services; enviar a credencial ao backend, que valida assinatura, emissor, audiência e expiração. Implementar proteção contra CSRF/replay conforme o fluxo escolhido.
- Identificar a identidade pelo par provedor + `sub`, não apenas pelo e-mail.
- Vincular uma conta local existente apenas mediante prova apropriada de controle; não unir contas automaticamente só porque os e-mails coincidem.
- Adaptar o modelo atual: senha é obrigatória no banco. Contas exclusivamente Google precisam de identidade externa sem senha local inventada, e a tela de perfil deve refletir isso.
- Emitir a sessão da própria loja após a validação; completar telefone/endereço apenas quando necessários e respeitar contas inativas e permissões.
- Testar primeiro acesso, acesso repetido, conta local existente, consentimento cancelado, token inválido/expirado e tentativa de vinculação indevida.

## Matriz mínima de testes

| Cenário | Resultado esperado |
| --- | --- |
| Pagamento aprovado | Pedido pago, total correto, uma baixa de estoque e remoção apenas das quantidades pagas |
| Pagamento recusado | Sem baixa; mensagem clara; tentativa posterior permitida sem cobrar em duplicidade |
| Pendente → aprovado | Pedido atualizado pelo servidor, inclusive sem retorno do navegador |
| Pendente → expirado/cancelado | Reserva liberada conforme regra; histórico coerente |
| Abandono/fechamento de aba | Pedido recuperável e eventual aprovação conciliada |
| Webhook duplicado | Sem duplicar baixa, remoção de carrinho ou notificações |
| Webhook fora de ordem | Estado final não regride incorretamente |
| Webhook falso/valor divergente | Rejeitado, registrado e sem alterar pedido/estoque |
| Webhook temporariamente indisponível | Reenvio/conciliação recupera o pagamento |
| Duplo clique, duas abas, repetição de POST | Regra de idempotência evita duplicação indevida |
| Duas compras da última peça | Estoque consistente e tratamento explícito de pagamento sem disponibilidade |
| Mudança de preço/estoque antes do checkout | Total/disponibilidade validados no servidor e mudança comunicada |
| Reembolso, parcial e chargeback | Estados distintos e tratamento operacional documentado |
| Mercado Pago sem resposta/credencial inválida | Mensagem segura, tentativa rastreável e nova tentativa controlada |
| Pedido/carrinho de outro cliente | Acesso negado sem expor dados |
| Frete e endereço alterados | Recálculo antes de pagar; pedido preserva endereço/modalidade contratados |
| Sessão expirada no retorno | Login e retomada preservam o contexto sem falso sucesso |
| Logout/troca/recuperação de senha | Tokens revogados deixam de funcionar no servidor |
| Cadastro inválido/e-mail duplicado/abuso | Validação e limites no backend; sem criação indevida |

Executar testes de serviço com provedor simulado, integração com PostgreSQL para concorrência e homologação ponta a ponta com contas/cartões de teste do Mercado Pago. Não usar cartões reais para simular recusas.

## Ordem recomendada de execução

1. Corrigir segredos e estruturar testes de pagamento; resolver tentativas, duplicação, estados e concorrência de estoque.
2. Definir retirada/entrega, implementar endereço e frete no total final.
3. Implementar operação dos pedidos, comunicação, conciliação e reembolso.
4. Completar segurança de contas, recuperação e confirmação de e-mail; validar sessão e produção.
5. Homologar a matriz completa e testar recuperação de falhas antes de habilitar vendas reais.
6. Adicionar Google sobre essa base, podendo desenvolver em paralelo somente após definir o modelo de identidade e sessão.

Para homologação externa serão necessários os acessos/configurações de teste adequados do Mercado Pago, segredo de webhook, endpoint público HTTPS e URLs de retorno. Os testes internos existentes não dependem desses tokens. Configurações da conta externa e do ambiente publicado não foram verificadas.

## Documentação consultada

- [Mercado Pago — notificações](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-orders/payment-notifications?scope=prod): confirmar a documentação específica da versão de integração usada antes de alterar endpoints ou tópicos; o código atual usa preferências e `/v1/payments`.
- [Google — validar ID token no servidor](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).
- [OWASP — autenticação](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).
- [OWASP — gerenciamento de sessão](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).
- [OWASP — recuperação de senha](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
