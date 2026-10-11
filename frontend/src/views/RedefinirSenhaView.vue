<script setup>
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { redefinirSenha } from '@/services/clienteApi'

const route = useRoute()
const token = computed(() => typeof route.query.token === 'string' ? route.query.token : '')
const senha = ref('')
const confirmacaoSenha = ref('')
const loading = ref(false)
const mensagem = ref('')
const erro = ref('')

async function enviar() {
  erro.value = ''
  mensagem.value = ''
  if (!token.value) { erro.value = 'Este link de recuperação é inválido.'; return }
  if (senha.value.length < 8 || !/[A-Za-zÀ-ÿ]/.test(senha.value) || !/\d/.test(senha.value)) {
    erro.value = 'A senha deve ter ao menos 8 caracteres, com uma letra e um número.'; return
  }
  if (senha.value !== confirmacaoSenha.value) { erro.value = 'As senhas precisam ser iguais.'; return }
  loading.value = true
  try {
    mensagem.value = (await redefinirSenha({ token: token.value, senha: senha.value, confirmacaoSenha: confirmacaoSenha.value })).message
    senha.value = ''; confirmacaoSenha.value = ''
  } catch (e) {
    erro.value = e.message || 'Não foi possível redefinir a senha.'
  } finally { loading.value = false }
}
</script>

<template>
  <section class="password-card">
    <p class="eyebrow">Dona Adah Store</p>
    <h1>Crie uma nova senha</h1>
    <p>Use pelo menos 8 caracteres, incluindo uma letra e um número.</p>
    <form v-if="token && !mensagem" @submit.prevent="enviar">
      <label for="new-password">Nova senha</label>
      <input id="new-password" v-model="senha" type="password" minlength="8" maxlength="72" autocomplete="new-password" required>
      <label for="confirm-password">Confirme a nova senha</label>
      <input id="confirm-password" v-model="confirmacaoSenha" type="password" minlength="8" maxlength="72" autocomplete="new-password" required>
      <button type="submit" :disabled="loading">{{ loading ? 'Salvando...' : 'Redefinir senha' }}</button>
    </form>
    <p v-if="!token" class="message error" role="alert">Este link de recuperação é inválido.</p>
    <p v-if="mensagem" class="message success" role="status">{{ mensagem }}</p>
    <p v-if="erro" class="message error" role="alert">{{ erro }}</p>
    <RouterLink to="/login">Ir para o login</RouterLink>
  </section>
</template>

<style scoped>
.password-card { width:min(92vw,470px); display:grid; gap:1rem; padding:2rem; border:1px solid rgba(106,27,44,.1); border-radius:20px; background:#fff; box-shadow:0 18px 48px rgba(106,27,44,.12); color:#4b3a3a; }
.eyebrow { margin:0; color:#8c6a4d; font-size:.72rem; font-weight:700; letter-spacing:.14em; text-transform:uppercase; }
h1 { margin:0; color:#5b1a26; font:600 2rem/1.1 Georgia,serif; }.password-card > p { margin:0; line-height:1.55; }
form { display:grid; gap:.6rem; } label { color:#5b1a26; font-weight:700; }
input { min-height:44px; padding:.7rem .8rem; border:1px solid #eadfdb; border-radius:10px; font:inherit; }
button { min-height:44px; margin-top:.35rem; border:0; border-radius:10px; background:#6a1b2c; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
button:disabled { opacity:.65; cursor:wait; }.message { padding:.75rem; border-radius:10px; }.success { background:#edf8f0; color:#24613a; }.error { background:#fff0f0; color:#8a1d1d; } a { color:#6a1b2c; font-weight:700; }
@media (max-width:520px) { .password-card { width:100%; padding:1.2rem; border-radius:16px; } h1 { font-size:clamp(1.65rem,8vw,2rem); } input { min-height:46px; font-size:16px; } button { min-height:46px; } }
</style>
