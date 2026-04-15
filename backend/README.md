# WebStore Backend

Backend em Spring Boot com PostgreSQL para o projeto WebStore.

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
  "senha": "123456"
}
```

### Login de cliente
`POST /api/login`

Exemplo de corpo:
```json
{
  "email": "maria@email.com",
  "senha": "123456"
}
```

## Observações

- A senha é armazenada com `BCrypt`.
- O service já está preparado para expansão com métodos de listar, atualizar e deletar clientes.
- A conexão com o PostgreSQL já deve estar configurada em `src/main/resources/application.properties`.

