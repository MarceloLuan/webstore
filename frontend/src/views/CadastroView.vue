<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { RouterLink } from 'vue-router'
import { cadastrarCliente } from '@/services/clienteApi'

const router = useRouter()
const nome = ref('')
const email = ref('')
const telefone = ref('')
const senha = ref('')
const confirmarSenha = ref('')
const loading = ref(false)
const erro = ref('')
const sucesso = ref('')

function formatarTelefone(valor) {
  const numeros = valor.replace(/\D/g, '').slice(0, 11)

  if (numeros.length <= 2) {
    return numeros
  }

  if (numeros.length <= 6) {
    return `(${numeros.slice(0, 2)}) ${numeros.slice(2)}`
  }

  if (numeros.length <= 10) {
    return `(${numeros.slice(0, 2)}) ${numeros.slice(2, 6)}-${numeros.slice(6)}`
  }

  return `(${numeros.slice(0, 2)}) ${numeros.slice(2, 7)}-${numeros.slice(7)}`
}

function aoDigitarTelefone(evento) {
  telefone.value = formatarTelefone(evento.target.value)
}

async function enviarCadastro() {
  erro.value = ''
  sucesso.value = ''

  if (!nome.value || !email.value || !telefone.value || !senha.value || !confirmarSenha.value) {
    erro.value = 'Preencha todos os campos.'
    return
  }

  if (senha.value !== confirmarSenha.value) {
    erro.value = 'As senhas nao conferem.'
    return
  }

  loading.value = true

  try {
    await cadastrarCliente({
      nome: nome.value,
      email: email.value,
      telefone: telefone.value,
      senha: senha.value,
    })

    sucesso.value = 'Cadastro realizado com sucesso. Redirecionando para login...'
    nome.value = ''
    email.value = ''
    telefone.value = ''
    senha.value = ''
    confirmarSenha.value = ''
    await router.push('/login')
  } catch (e) {
    erro.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-card">
    <h1>CRIAR CONTA</h1>

    <form @submit.prevent="enviarCadastro" class="auth-form">
      <label for="nome">Nome completo</label>
      <input id="nome" v-model="nome" type="text" placeholder="Seu nome completo" />

      <label for="email">E-mail</label>
      <input id="email" v-model="email" type="email" placeholder="seuemail@exemplo.com" />

      <label for="telefone">Telefone</label>
      <input
        id="telefone"
        v-model="telefone"
        type="tel"
        placeholder="(00) 00000-0000"
        @input="aoDigitarTelefone"
      />

      <label for="senha">Senha</label>
      <input id="senha" v-model="senha" type="password" placeholder="Crie uma senha" />

      <label for="confirmarSenha">Confirmar senha</label>
      <input
        id="confirmarSenha"
        v-model="confirmarSenha"
        type="password"
        placeholder="Repita sua senha"
      />

      <button type="submit" :disabled="loading">
        {{ loading ? 'CADASTRANDO...' : 'Cadastrar' }}
      </button>
    </form>

    <p v-if="erro" class="feedback erro">{{ erro }}</p>
    <p v-if="sucesso" class="feedback sucesso">{{ sucesso }}</p>

    <p class="switch-text">
      Ja tem conta?
      <RouterLink to="/login">Faça login</RouterLink>
    </p>
  </section>
</template>

<style scoped>
.auth-card {
  width: min(92vw, 430px);
  background: #ffffff;
  border-radius: 18px;
  padding: 2rem;
  box-shadow: 0 18px 48px rgba(106, 27, 44, 0.12);
}

h1 {
  margin: 0 0 1.4rem;
  text-align: center;
  color: #6a1b2c;
  letter-spacing: 0.04em;
  font-size: 1.35rem;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

label {
  font-size: 0.95rem;
  color: #4b3a3a;
}

input {
  border: 1px solid #eadfdb;
  border-radius: 12px;
  padding: 0.75rem 0.9rem;
  background: #f2efed;
  font-size: 0.96rem;
}

input:focus {
  outline: 2px solid #6a1b2c55;
  border-color: #6a1b2c;
}

button {
  margin-top: 0.7rem;
  border: none;
  border-radius: 12px;
  padding: 0.88rem 1rem;
  font-size: 0.97rem;
  font-weight: 700;
  color: #fff;
  background: #6a1b2c;
  cursor: pointer;
}

button:hover {
  background: #581624;
}

button:disabled {
  opacity: 0.8;
  cursor: wait;
}

.switch-text {
  margin: 1.1rem 0 0;
  text-align: center;
  color: #4b3a3a;
}

.switch-text a {
  color: #6a1b2c;
  font-weight: 700;
  text-decoration: none;
}

.switch-text a:hover {
  text-decoration: underline;
}

.feedback {
  margin: 0.9rem 0 0;
  text-align: center;
  font-size: 0.9rem;
}

.erro {
  color: #8a1d1d;
}

.sucesso {
  color: #176d33;
}
</style>
