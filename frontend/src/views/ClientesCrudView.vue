<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { atualizarCliente, deletarCliente, listarClientes } from '@/services/clienteApi'

const clientes = ref([])
const loading = ref(false)
const salvando = ref(false)
const erro = ref('')
const sucesso = ref('')

const clienteEmEdicao = ref(null)
const form = ref({
  id: null,
  nome: '',
  email: '',
  telefone: '',
  senha: '',
})

async function carregarClientes() {
  loading.value = true
  erro.value = ''

  try {
    clientes.value = await listarClientes()
  } catch (e) {
    erro.value = e.message
  } finally {
    loading.value = false
  }
}

function iniciarEdicao(cliente) {
  clienteEmEdicao.value = cliente
  form.value = {
    id: cliente.id,
    nome: cliente.nome,
    email: cliente.email,
    telefone: cliente.telefone || '',
    senha: '',
  }
  sucesso.value = ''
  erro.value = ''
}

function cancelarEdicao() {
  clienteEmEdicao.value = null
  form.value = {
    id: null,
    nome: '',
    email: '',
    telefone: '',
    senha: '',
  }
}

async function salvarEdicao() {
  erro.value = ''
  sucesso.value = ''

  if (!form.value.nome || !form.value.email || !form.value.telefone) {
    erro.value = 'Preencha nome, e-mail e telefone.'
    return
  }

  salvando.value = true

  try {
    await atualizarCliente(form.value.id, {
      nome: form.value.nome,
      email: form.value.email,
      telefone: form.value.telefone,
      senha: form.value.senha,
    })

    sucesso.value = 'Cliente atualizado com sucesso.'
    cancelarEdicao()
    await carregarClientes()
  } catch (e) {
    erro.value = e.message
  } finally {
    salvando.value = false
  }
}

async function excluirCliente(id) {
  erro.value = ''
  sucesso.value = ''

  const confirmar = window.confirm('Deseja realmente excluir este cliente?')
  if (!confirmar) {
    return
  }

  try {
    await deletarCliente(id)
    sucesso.value = 'Cliente excluido com sucesso.'

    if (clienteEmEdicao.value?.id === id) {
      cancelarEdicao()
    }

    await carregarClientes()
  } catch (e) {
    erro.value = e.message
  }
}

onMounted(() => {
  carregarClientes()
})
</script>

<template>
  <section class="crud-card">
    <div class="crud-header">
      <h1>PAINEL DE CLIENTES</h1>
      <RouterLink class="novo-link" to="/cadastro">Novo cadastro</RouterLink>
    </div>

    <p class="hint">CRUD completo: listar, editar e excluir clientes cadastrados.</p>

    <p v-if="erro" class="feedback erro">{{ erro }}</p>
    <p v-if="sucesso" class="feedback sucesso">{{ sucesso }}</p>

    <div class="table-wrapper">
      <p v-if="loading" class="hint">Carregando clientes...</p>

      <table v-else-if="clientes.length" class="clientes-table">
        <thead>
          <tr>
            <th>Nome</th>
            <th>E-mail</th>
            <th>Telefone</th>
            <th>Acoes</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="cliente in clientes" :key="cliente.id">
            <td>{{ cliente.nome }}</td>
            <td>{{ cliente.email }}</td>
            <td>{{ cliente.telefone }}</td>
            <td class="acoes-col">
              <button class="btn secondary" @click="iniciarEdicao(cliente)">Editar</button>
              <button class="btn danger" @click="excluirCliente(cliente.id)">Excluir</button>
            </td>
          </tr>
        </tbody>
      </table>

      <p v-else class="hint">Ainda nao existem clientes cadastrados.</p>
    </div>

    <form v-if="clienteEmEdicao" @submit.prevent="salvarEdicao" class="edit-form">
      <h2>Editar cliente</h2>

      <label for="editNome">Nome completo</label>
      <input id="editNome" v-model="form.nome" type="text" placeholder="Nome completo" />

      <label for="editEmail">E-mail</label>
      <input id="editEmail" v-model="form.email" type="email" placeholder="seuemail@exemplo.com" />

      <label for="editTelefone">Telefone</label>
      <input id="editTelefone" v-model="form.telefone" type="tel" placeholder="(00) 00000-0000" />

      <label for="editSenha">Nova senha (opcional)</label>
      <input id="editSenha" v-model="form.senha" type="password" placeholder="Somente se quiser trocar" />

      <div class="actions-row">
        <button class="btn primary" type="submit" :disabled="salvando">
          {{ salvando ? 'SALVANDO...' : 'Salvar alteracoes' }}
        </button>
        <button class="btn secondary" type="button" @click="cancelarEdicao">Cancelar</button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.crud-card {
  width: min(95vw, 950px);
  background: #ffffff;
  border-radius: 20px;
  padding: 2rem;
  box-shadow: 0 18px 48px rgba(106, 27, 44, 0.12);
}

.crud-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

h1 {
  margin: 0;
  color: #6a1b2c;
  letter-spacing: 0.04em;
  font-size: 1.35rem;
}

h2 {
  margin: 0;
  color: #6a1b2c;
  font-size: 1.12rem;
}

.novo-link {
  color: #6a1b2c;
  font-weight: 700;
  text-decoration: none;
}

.novo-link:hover {
  text-decoration: underline;
}

.hint {
  margin: 0.9rem 0;
  color: #5d4c4c;
}

.table-wrapper {
  margin-top: 0.8rem;
  overflow-x: auto;
}

.clientes-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 680px;
}

.clientes-table th,
.clientes-table td {
  text-align: left;
  padding: 0.85rem;
  border-bottom: 1px solid #ebded8;
}

.clientes-table th {
  color: #6a1b2c;
  font-size: 0.95rem;
}

.acoes-col {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.edit-form {
  margin-top: 1.5rem;
  padding: 1.2rem;
  border: 1px solid #ebded8;
  border-radius: 16px;
  background: #faf7f5;
  display: grid;
  gap: 0.6rem;
}

label {
  font-size: 0.93rem;
  color: #4b3a3a;
}

input {
  border: 1px solid #eadfdb;
  border-radius: 12px;
  padding: 0.72rem 0.9rem;
  background: #f2efed;
  font-size: 0.95rem;
}

input:focus {
  outline: 2px solid #6a1b2c55;
  border-color: #6a1b2c;
}

.btn {
  border: none;
  border-radius: 12px;
  padding: 0.7rem 0.95rem;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;
}

.primary {
  background: #6a1b2c;
  color: #fff;
}

.primary:hover {
  background: #581624;
}

.secondary {
  background: #ece4df;
  color: #4b3a3a;
}

.secondary:hover {
  background: #e2d8d2;
}

.danger {
  background: #9f233c;
  color: #fff;
}

.danger:hover {
  background: #821d31;
}

.actions-row {
  margin-top: 0.5rem;
  display: flex;
  gap: 0.7rem;
  flex-wrap: wrap;
}

.feedback {
  margin: 0.7rem 0 0;
  font-size: 0.9rem;
}

.erro {
  color: #8a1d1d;
}

.sucesso {
  color: #176d33;
}

@media (max-width: 760px) {
  .crud-card {
    padding: 1.1rem;
  }

  .crud-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
