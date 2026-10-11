<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { solicitarRecuperacaoSenha } from '@/services/clienteApi'

const email = ref('')
const loading = ref(false)
const mensagem = ref('')
const erro = ref('')

async function enviar() {
  erro.value = ''
  mensagem.value = ''
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) {
    erro.value = 'Informe um e-mail válido.'
    return
  }
  loading.value = true
  try {
    mensagem.value = (await solicitarRecuperacaoSenha(email.value.trim())).message
  } catch (e) {
    erro.value = e.status === 429 ? 'Muitas solicitações. Aguarde alguns minutos e tente novamente.' : (e.message || 'Não foi possível concluir a solicitação.')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="password-card">
    <p class="eyebrow">Dona Adah Store</p>
    <h1>Esqueci minha senha</h1>
    <p>Informe o e-mail da sua conta. Se ele estiver cadastrado, enviaremos um link temporário.</p>
    <form @submit.prevent="enviar">
      <label for="recovery-email">E-mail</label>
      <input id="recovery-email" v-model.trim="email" type="email" maxlength="254" autocomplete="email" required placeholder="seuemail@exemplo.com">
      <button type="submit" :disabled="loading">{{ loading ? 'Enviando...' : 'Enviar link' }}</button>
    </form>
    <p v-if="mensagem" class="message success" role="status">{{ mensagem }}</p>
    <p v-if="erro" class="message error" role="alert">{{ erro }}</p>
    <RouterLink to="/login">Voltar para entrar</RouterLink>
  </section>
</template>

<style scoped>
.password-card { width:min(92vw,470px); display:grid; gap:1rem; padding:2rem; border:1px solid rgba(106,27,44,.1); border-radius:20px; background:#fff; box-shadow:0 18px 48px rgba(106,27,44,.12); color:#4b3a3a; }
.eyebrow { margin:0; color:#8c6a4d; font-size:.72rem; font-weight:700; letter-spacing:.14em; text-transform:uppercase; }
h1 { margin:0; color:#5b1a26; font:600 2rem/1.1 Georgia,serif; }
.password-card > p { margin:0; line-height:1.55; }
form { display:grid; gap:.6rem; }
label { color:#5b1a26; font-weight:700; }
input { min-height:44px; padding:.7rem .8rem; border:1px solid #eadfdb; border-radius:10px; font:inherit; }
button { min-height:44px; border:0; border-radius:10px; background:#6a1b2c; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
button:disabled { opacity:.65; cursor:wait; }
.message { padding:.75rem; border-radius:10px; }.success { background:#edf8f0; color:#24613a; }.error { background:#fff0f0; color:#8a1d1d; }
a { color:#6a1b2c; font-weight:700; }
@media (max-width:520px) { .password-card { width:100%; padding:1.2rem; border-radius:16px; } h1 { font-size:clamp(1.65rem,8vw,2rem); } input { min-height:46px; font-size:16px; } button { min-height:46px; } }
</style>
