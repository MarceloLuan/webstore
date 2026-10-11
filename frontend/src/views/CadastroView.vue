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

function senhaValida(valor) {
  return valor.length >= 8 && /[A-Za-zÀ-ÿ]/.test(valor) && /\d/.test(valor)
}

async function enviarCadastro() {
  erro.value = ''
  sucesso.value = ''

  if (!nome.value || !email.value || !telefone.value || !senha.value || !confirmarSenha.value) {
    erro.value = 'Preencha todos os campos.'
    return
  }

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) {
    erro.value = 'Informe um e-mail válido.'
    return
  }

  if (telefone.value.replace(/\D/g, '').length < 10) {
    erro.value = 'Informe um telefone válido, com DDD.'
    return
  }

  if (!senhaValida(senha.value)) {
    erro.value = 'A senha deve ter ao menos 8 caracteres, com uma letra e um número.'
    return
  }

  if (senha.value !== confirmarSenha.value) {
    erro.value = 'As senhas precisam ser iguais.'
    return
  }

  loading.value = true

  try {
    await cadastrarCliente({
      nome: nome.value,
      email: email.value,
      telefone: telefone.value,
      senha: senha.value,
      confirmacaoSenha: confirmarSenha.value,
    })

    const emailCadastrado = email.value
    sucesso.value = 'Cadastro realizado. Confira seu e-mail para confirmar a conta.'
    nome.value = ''
    email.value = ''
    telefone.value = ''
    senha.value = ''
    confirmarSenha.value = ''
    await router.push({ name: 'confirmar-email', query: { email: emailCadastrado } })
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
      <p class="required-note"><span aria-hidden="true">*</span> Campos obrigatórios</p>

      <label for="nome">Nome completo <span class="required-mark" aria-hidden="true">*</span></label>
      <input id="nome" v-model.trim="nome" type="text" placeholder="Seu nome completo" maxlength="120" required />

      <label for="email">E-mail <span class="required-mark" aria-hidden="true">*</span></label>
      <input id="email" v-model.trim="email" type="email" placeholder="seuemail@exemplo.com" maxlength="254" required />

      <label for="telefone">Telefone <span class="required-mark" aria-hidden="true">*</span></label>
      <input
        id="telefone"
        v-model="telefone"
        type="tel"
        placeholder="(00) 00000-0000"
        maxlength="20"
        inputmode="tel"
        @input="aoDigitarTelefone"
        required
      />

      <label for="senha">Senha <span class="required-mark" aria-hidden="true">*</span></label>
      <input id="senha" v-model="senha" type="password" placeholder="Crie uma senha" minlength="8" maxlength="72" autocomplete="new-password" required />
      <small class="password-hint">Use ao menos 8 caracteres, incluindo uma letra e um número.</small>

      <label for="confirmarSenha">Confirmar senha <span class="required-mark" aria-hidden="true">*</span></label>
      <input
        id="confirmarSenha"
        v-model="confirmarSenha"
        type="password"
        placeholder="Repita sua senha"
        minlength="8"
        maxlength="72"
        autocomplete="new-password"
        required
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

.required-note {
  margin: 0 0 0.2rem;
  color: #6e6166;
  font-size: 0.82rem;
}

.required-note span,
.required-mark {
  color: #a4232f;
  font-weight: 700;
}
.password-hint { margin-top: -.35rem; color: #6e6166; font-size: .78rem; }

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

@media (max-width: 520px) {
  .auth-card { width: 100%; padding: 1.2rem; border-radius: 16px; }
  .auth-card input { min-height: 46px; font-size: 16px; }
  .auth-card button { min-height: 46px; }
}
</style>
