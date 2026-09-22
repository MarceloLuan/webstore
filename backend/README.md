# WebStore Backend

Backend em Spring Boot com PostgreSQL para o projeto WebStore.

## Configuração e execução local

As credenciais do banco e a chave JWT não possuem valores padrão no código.
Configure as variáveis abaixo no ambiente do processo ou em um arquivo local
`backend/.env`, ignorado pelo Git. O `.env.example` contém apenas nomes, sem segredos.

| Variável | Obrigatoriedade | Finalidade |
| --- | --- | --- |
| `DB_USERNAME` | Obrigatória | Usuário do PostgreSQL local |
| `DB_PASSWORD` | Obrigatória | Senha desse usuário |
| `JWT_SECRET` | Obrigatória | Chave aleatória em Base64, gerada a partir de pelo menos 32 bytes |
| `DB_URL` | Opcional | URL JDBC; padrão local: `jdbc:postgresql://localhost:5432/webstore` |
| `MERCADO_PAGO_ACCESS_TOKEN` | Para pagamentos | Credencial do Mercado Pago, usada apenas no backend |
| `MERCADO_PAGO_WEBHOOK_SECRET` | Para webhooks | Segredo de validação das notificações |
| `MERCADO_PAGO_WEBHOOK_URL` | Para receber notificações externas | URL pública HTTPS do endpoint `/api/pagamentos/webhook` |
| `FRONTEND_URL` | Opcional localmente | URL do frontend; padrão: `http://localhost:5173` |

No PowerShell, a partir da raiz do projeto:

```powershell
Set-Location backend
Copy-Item .env.example .env
```

Preencha o `.env` no seu editor antes de executar `./mvnw.cmd spring-boot:run`.
Não sobrescreva um `.env` existente. Preencha as quatro variáveis `DB_*`/`JWT_SECRET`
do exemplo; para usar o padrão de `DB_URL`, remova sua linha em vez de deixá-la vazia.
Remova também a linha de `FRONTEND_URL` se quiser usar o padrão local.
As três variáveis do Mercado Pago podem ficar vazias para trabalhar sem pagamentos.
Crie previamente o banco local e conceda acesso ao usuário escolhido.

O Spring importa explicitamente `./.env` como um arquivo de propriedades, relativo
ao diretório de execução. Execute dentro de `backend/` ou configure esse diretório
na IDE. Variáveis definidas no ambiente têm precedência sobre o arquivo.
Use `NOME=valor`, sem `export` e sem aspas envolvendo os valores; o arquivo segue
a sintaxe Java properties, portanto barras invertidas literais precisam ser escapadas.
Não reutilize segredos de produção no desenvolvimento e nunca coloque estas
credenciais em variáveis `VITE_*`, que ficam acessíveis no frontend.

Para gerar uma nova chave JWT criptograficamente aleatória no PowerShell:

```powershell
$jwtKeyBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
  $jwtRandom.GetBytes($jwtKeyBytes)
  [Convert]::ToBase64String($jwtKeyBytes)
} finally {
  $jwtRandom.Dispose()
}
```

Guarde o resultado somente em `JWT_SECRET` no ambiente ou `.env` privado. Não o
envie ao Git nem compartilhe a saída. Alterar a chave invalida tokens anteriores.

### Testes e publicação

Execute `./mvnw.cmd test` dentro de `backend/`. Os testes usam H2 em memória e uma
chave JWT aleatória gerada pelo Spring; não precisam de PostgreSQL, `.env` ou tokens
do Mercado Pago. A configuração de testes não importa o `.env` local.

Na publicação, injete os valores pelo ambiente/gerenciador de segredos, configure
`DB_URL`, `FRONTEND_URL` e a URL pública do webhook conforme o ambiente real.
Os padrões locais não substituem uma configuração de produção.

O `.gitignore` da raiz protege `.env`, variantes locais, chaves privadas e logs;
somente modelos `.env.example` sem valores devem ser versionados. O Git não deixa
de rastrear arquivos já versionados apenas porque foram adicionados ao ignore.
Se as credenciais antigas já foram versionadas ou compartilhadas, substitua a senha
do banco e a chave JWT: esta mudança não remove valores do histórico do Git.

## Módulo de Clientes

Estrutura criada seguindo MVC:

- `model` → entidade `Cliente`
- `repository` → acesso ao banco com `ClienteRepository`
- `service` → regras de negócio com `ClienteService`
- `controller` → endpoints REST com `ClienteController`

## Endpoints

### Cadastrar cliente
`POST /api/clientes`

Exemplo de corpo:
```json
{
  "nome": "Maria Silva",
  "email": "maria@email.com",
  "telefone": "<telefone>",
  "senha": "<senha escolhida>",
  "confirmacaoSenha": "<mesma senha>"
}
```

### Login de cliente
`POST /api/login`

Exemplo de corpo:
```json
{
  "email": "maria@email.com",
  "senha": "<senha escolhida>"
}
```

## Observações

- A senha é armazenada com `BCrypt`.
- O service já está preparado para expansão com métodos de listar, atualizar e deletar clientes.
- A conexão com o PostgreSQL é configurada pelas variáveis descritas acima.

