<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { atualizarMinhaConta, buscarMinhaConta, deletarMinhaConta } from '@/services/clienteApi'
import { clearUser, getUser, setUser } from '@/services/auth'

const router = useRouter()

const user = ref(getUser())
const loading = ref(true)
const saving = ref(false)
const deleting = ref(false)
const erro = ref('')
const sucesso = ref('')

const form = ref({
  nome: '',
  email: '',
  telefone: '',
  senha: '',
})

function isAuthError(message) {
  return Boolean(
    message &&
    (message.includes('não autenticado') || message.includes('Token JWT') || message.includes('inativo')),
  )
}

function sincronizarForm(conta) {
  form.value = {
    nome: conta.nome || '',
    email: conta.email || '',
    telefone: conta.telefone || '',
    senha: '',
  }
}

async function carregarConta() {
  loading.value = true
  erro.value = ''

  try {
    const conta = await buscarMinhaConta()
    user.value = conta
    sincronizarForm(conta)
    setUser(conta)
  } catch (error) {
    if (isAuthError(error.message)) {
      clearUser()
      await router.replace('/login')
      return
    }

    erro.value = error.message
  } finally {
    loading.value = false
  }
}

async function salvarConta() {
  erro.value = ''
  sucesso.value = ''

  if (!form.value.nome || !form.value.email || !form.value.telefone) {
    erro.value = 'Preencha nome, e-mail e telefone.'
    return
  }

  saving.value = true

  try {
    const oldEmail = user.value?.email || ''
    const changedPassword = Boolean(form.value.senha)

    const contaAtualizada = await atualizarMinhaConta({
      nome: form.value.nome,
      email: form.value.email,
      telefone: form.value.telefone,
      senha: form.value.senha,
    })

    user.value = contaAtualizada
    setUser(contaAtualizada)
    form.value.senha = ''

    if (changedPassword || oldEmail !== contaAtualizada.email) {
      clearUser()
      await router.push('/login')
      return
    }

    sucesso.value = 'Conta atualizada com sucesso.'
  } catch (error) {
    if (isAuthError(error.message)) {
      clearUser()
      await router.replace('/login')
      return
    }

    erro.value = error.message
  } finally {
    saving.value = false
  }
}

async function inativarConta() {
  erro.value = ''
  sucesso.value = ''

  const confirmar = window.confirm('Tem certeza que deseja inativar sua conta?')
  if (!confirmar) {
    return
  }

  deleting.value = true

  try {
    await deletarMinhaConta()
    clearUser()
    router.push('/login')
  } catch (error) {
    if (isAuthError(error.message)) {
      clearUser()
      await router.replace('/login')
      return
    }

    erro.value = error.message
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  if (!user.value) {
    router.replace('/login')
    return
  }

  carregarConta()
})
</script>

<template>
  <section class="account-page">
    <div class="account-hero">
      <div>
        <p class="eyebrow">Minha conta</p>
        <h1>Gerenciar dados da conta</h1>
        <p class="lead">
          Atualize suas informações pessoais, ajuste a senha e, se quiser, inative a conta.
        </p>
      </div>
    </div>

    <div v-if="loading" class="panel loading-panel">Carregando conta...</div>

    <div v-else class="account-grid">
      <section class="panel form-panel">
        <div class="panel-header">
          <div>
            <p class="panel-kicker">Dados principais</p>
            <h2>Editar conta</h2>
          </div>
          <button class="secondary-button" type="button" @click="router.push('/home')">Voltar</button>
        </div>

        <form class="account-form" @submit.prevent="salvarConta">
          <label for="nome">Nome completo</label>
          <input id="nome" v-model="form.nome" type="text" placeholder="Seu nome completo" />

          <label for="email">E-mail</label>
          <input id="email" v-model="form.email" type="email" placeholder="seuemail@exemplo.com" />

          <label for="telefone">Telefone</label>
          <input id="telefone" v-model="form.telefone" type="text" placeholder="(00) 00000-0000" />

          <label for="senha">Nova senha</label>
          <input id="senha" v-model="form.senha" type="password" placeholder="Deixe vazio para manter" />

          <div class="form-actions">
            <button class="primary-button" type="submit" :disabled="saving">
              {{ saving ? 'SALVANDO...' : 'Salvar alterações' }}
            </button>
            <button class="danger-button" type="button" :disabled="deleting" @click="inativarConta">
              {{ deleting ? 'INATIVANDO...' : 'Inativar conta' }}
            </button>
          </div>
        </form>

        <p v-if="erro" class="message error">{{ erro }}</p>
        <p v-if="sucesso" class="message success">{{ sucesso }}</p>
      </section>

      <aside class="panel info-panel">
        <p class="panel-kicker">Resumo</p>
        <h2>{{ user?.nome }}</h2>

        <dl class="info-list">
          <div>
            <dt>E-mail</dt>
            <dd>{{ user?.email }}</dd>
          </div>
          <div>
            <dt>Telefone</dt>
            <dd>{{ user?.telefone || '-' }}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>{{ user?.ativo ? 'Ativa' : 'Inativa' }}</dd>
          </div>
          <div>
            <dt>Perfil</dt>
            <dd>{{ user?.role }}</dd>
          </div>
        </dl>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.account-page {
  width: min(1100px, 100%);
  margin: 0 auto;
  display: grid;
  gap: 1rem;
  padding: 1rem 0 2rem;
}

.account-hero {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: stretch;
  padding: 1.4rem;
  border-radius: 24px;
  background: linear-gradient(135deg, #fff 0%, #fbf6f3 100%);
  border: 1px solid rgba(106, 27, 44, 0.08);
  box-shadow: 0 18px 40px rgba(106, 27, 44, 0.08);
}

.eyebrow,
.panel-kicker {
  margin: 0 0 0.35rem;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 0.74rem;
  color: #8f5a67;
}

h1,
h2,
p,
dl {
  margin: 0;
}

h1 {
  color: #5c1a2a;
  font-size: clamp(1.9rem, 3.8vw, 3rem);
  line-height: 1.08;
}

.lead {
  margin-top: 0.85rem;
  color: #62525b;
  line-height: 1.6;
  max-width: 58ch;
}

.role-badge {
  min-width: 180px;
  align-self: center;
  padding: 1rem 1.1rem;
  border-radius: 20px;
  background: #6a1b2c;
  color: #fff;
  display: grid;
  gap: 0.25rem;
}

.role-badge span {
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  opacity: 0.82;
}

.account-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.75fr);
  gap: 1rem;
}

.panel {
  border-radius: 24px;
  padding: 1.3rem;
  background: #fff;
  border: 1px solid rgba(106, 27, 44, 0.08);
  box-shadow: 0 18px 40px rgba(106, 27, 44, 0.08);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
  margin-bottom: 1rem;
}

.account-form {
  display: grid;
  gap: 0.7rem;
}

label,
dt {
  color: #5d4f54;
  font-size: 0.94rem;
}

input {
  border: 1px solid #eadfdb;
  border-radius: 12px;
  padding: 0.8rem 0.95rem;
  background: #fdfafa;
  font-size: 0.96rem;
}

input:focus {
  outline: 2px solid rgba(106, 27, 44, 0.18);
  border-color: #6a1b2c;
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 0.4rem;
}

.primary-button,
.secondary-button,
.danger-button {
  border: none;
  border-radius: 999px;
  padding: 0.82rem 1rem;
  font-weight: 700;
  cursor: pointer;
}

.primary-button {
  background: #6a1b2c;
  color: #fff;
}

.secondary-button {
  background: #f3ebe8;
  color: #5c1a2a;
}

.danger-button {
  background: #f4ded9;
  color: #7b1d24;
}

.message {
  margin-top: 0.85rem;
  font-size: 0.95rem;
}

.error {
  color: #8a1d1d;
}

.success {
  color: #176d33;
}

.info-panel {
  display: grid;
  align-content: start;
  gap: 0.9rem;
}

.info-list {
  display: grid;
  gap: 0.75rem;
}

.info-list div {
  padding: 0.85rem;
  border-radius: 16px;
  background: #faf6f4;
  border: 1px solid rgba(106, 27, 44, 0.06);
}

.info-list dd {
  margin: 0.2rem 0 0;
  color: #5c1a2a;
  font-weight: 700;
}

.notice {
  padding: 1rem;
  border-radius: 18px;
  background: #f9f3f0;
  border: 1px solid rgba(106, 27, 44, 0.08);
  color: #5c1a2a;
}

.notice p {
  margin-top: 0.35rem;
  color: #6b5b61;
  line-height: 1.5;
}

.loading-panel {
  text-align: center;
  color: #5c1a2a;
}

@media (max-width: 900px) {
  .account-grid,
  .account-hero {
    grid-template-columns: 1fr;
    display: grid;
  }
}

@media (max-width: 640px) {
  .account-page {
    padding-top: 0.5rem;
  }

  .panel,
  .account-hero {
    padding: 1rem;
    border-radius: 18px;
  }

  .panel-header {
    flex-direction: column;
  }

  .role-badge {
    min-width: 0;
    width: 100%;
  }
}
</style>
