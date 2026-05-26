<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { RouterLink } from 'vue-router'
import { loginCliente } from '@/services/clienteApi'
import { setUser } from '@/services/auth'

const router = useRouter()
const email = ref('')
const senha = ref('')
const loading = ref(false)
const erro = ref('')
const sucesso = ref('')

async function enviarLogin() {
  erro.value = ''
  sucesso.value = ''

  if (!email.value || !senha.value) {
    erro.value = 'Preencha e-mail e senha.'
    return
  }

  loading.value = true

  try {
    const cliente = await loginCliente({
      email: email.value,
      senha: senha.value,
    })

    // salvar sessão simples no localStorage e redirecionar para /home
    setUser(cliente)
    sucesso.value = `Login realizado com sucesso para ${cliente?.nome || email.value}.`
    router.push('/home')
  } catch (e) {
    erro.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="auth-card">
    <h1>ACESSO A CONTA</h1>

    <form @submit.prevent="enviarLogin" class="auth-form">
      <label for="email">E-mail</label>
      <input id="email" v-model="email" type="email" placeholder="seuemail@exemplo.com" />

      <label for="senha">Senha</label>
      <input id="senha" v-model="senha" type="password" placeholder="Digite sua senha" />

      <button type="submit" :disabled="loading">
        {{ loading ? 'ENTRANDO...' : 'ENTRAR' }}
      </button>
    </form>

    <p v-if="erro" class="feedback erro">{{ erro }}</p>
    <p v-if="sucesso" class="feedback sucesso">{{ sucesso }}</p>

    <p class="switch-text">
      Nao possui conta?
      <RouterLink to="/cadastro">Cadastre-se</RouterLink>
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
