<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { confirmarEmail, reenviarConfirmacaoEmail } from '@/services/clienteApi'

const route = useRoute()
const email = ref(typeof route.query.email === 'string' ? route.query.email : '')
const loading = ref(false)
const reenviando = ref(false)
const mensagem = ref('')
const erro = ref('')

async function confirmar() {
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) return
  loading.value = true
  erro.value = ''
  try {
    mensagem.value = (await confirmarEmail(token)).message
  } catch (e) {
    erro.value = e.message || 'Não foi possível confirmar este e-mail.'
  } finally {
    loading.value = false
  }
}

async function reenviar() {
  erro.value = ''
  mensagem.value = ''
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) {
    erro.value = 'Informe um e-mail válido.'
    return
  }
  reenviando.value = true
  try {
    mensagem.value = (await reenviarConfirmacaoEmail(email.value.trim())).message
  } catch (e) {
    erro.value = e.message || 'Não foi possível reenviar agora. Tente novamente mais tarde.'
  } finally {
    reenviando.value = false
  }
}

onMounted(confirmar)
</script>

<template>
  <section class="confirmation-card">
    <p class="eyebrow">Dona Adah Store</p>
    <h1>Confirme seu e-mail</h1>
    <p>Enviamos um link de confirmação. Abra-o neste navegador para ativar sua conta.</p>
    <p v-if="loading" class="message">Confirmando seu e-mail...</p>
    <p v-if="mensagem" class="message success" role="status">{{ mensagem }}</p>
    <p v-if="erro" class="message error" role="alert">{{ erro }}</p>

    <form class="resend-form" @submit.prevent="reenviar">
      <label for="confirmation-email">Não recebeu o e-mail?</label>
      <input id="confirmation-email" v-model.trim="email" type="email" maxlength="254" autocomplete="email" placeholder="seuemail@exemplo.com" required>
      <button type="submit" :disabled="reenviando">{{ reenviando ? 'Enviando...' : 'Reenviar confirmação' }}</button>
    </form>
    <RouterLink to="/login">Voltar para entrar</RouterLink>
  </section>
</template>

<style scoped>
.confirmation-card { width: min(92vw, 470px); display: grid; gap: 1rem; padding: 2rem; border: 1px solid rgba(106,27,44,.1); border-radius: 20px; background: #fff; box-shadow: 0 18px 48px rgba(106,27,44,.12); color: #4b3a3a; }.eyebrow { margin: 0; color: #8c6a4d; font-size: .72rem; font-weight: 700; letter-spacing: .14em; text-transform: uppercase; } h1 { margin: 0; color: #5b1a26; font: 600 2rem/1.1 Georgia, serif; }.confirmation-card > p { margin: 0; line-height: 1.55; }.resend-form { display: grid; gap: .55rem; margin-top: .5rem; }.resend-form label { color: #5b1a26; font-weight: 700; }.resend-form input { min-height: 44px; padding: .7rem .8rem; border: 1px solid #eadfdb; border-radius: 10px; font: inherit; }.resend-form button { min-height: 44px; border: 0; border-radius: 10px; background: #6a1b2c; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }.resend-form button:disabled { opacity: .65; cursor: wait; }.message { padding: .75rem; border-radius: 10px; }.success { background: #edf8f0; color: #24613a; }.error { background: #fff0f0; color: #8a1d1d; } a { color: #6a1b2c; font-weight: 700; }
@media (max-width:520px) { .confirmation-card { width:100%; padding:1.2rem; border-radius:16px; } h1 { font-size:clamp(1.65rem,8vw,2rem); } .resend-form input { min-height:46px; font-size:16px; } .resend-form button { min-height:46px; } }
</style>
