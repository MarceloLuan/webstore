<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { storeInfo } from '@/config/store'
import { useAuthStore } from '@/stores/authStore'

const { user, isAdmin } = useAuthStore()
const year = new Date().getFullYear()
const socialLinks = computed(() => [
  { name: 'Instagram', url: storeInfo.instagramUrl, description: 'Inspiração para o seu estilo', icon: 'instagram' },
  { name: 'WhatsApp', url: storeInfo.whatsappUrl, description: 'Converse com a loja', icon: 'whatsapp' },
].map((social) => {
  // Sem endereço cadastrado, o canal é apresentado sem criar um link falso.
  let href = ''
  try {
    const url = new URL(social.url)
    if (url.protocol === 'https:') href = url.href
  } catch { /* Aguarda o preenchimento em config/store.js. */ }
  return { ...social, href }
}))
</script>

<template>
  <footer class="store-footer" aria-label="Rodapé da Dona Adah Store">
    <div class="footer-content">
      <div class="footer-brand">
        <p class="footer-kicker">Seu estilo, do seu jeito</p>
        <RouterLink class="footer-name" to="/home">{{ storeInfo.name }}</RouterLink>
        <p class="brand-description">Peças para fazer parte dos seus dias.<br />Encontre seu próximo look por aqui.</p>
        <div class="store-address">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M20 10c0 6-8 11-8 11S4 16 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></svg>
          <div><span>Visite nossa loja</span><address>{{ storeInfo.address }}</address></div>
        </div>
      </div>

      <nav class="footer-navigation" aria-label="Links úteis">
        <h2>Explore a loja</h2>
        <RouterLink to="/home">Ver coleção</RouterLink>
        <template v-if="user">
          <RouterLink to="/minha-conta">Meu perfil</RouterLink>
          <RouterLink v-if="user.role === 'CLIENTE'" to="/meus-pedidos">Meus pedidos</RouterLink>
          <RouterLink v-if="user.role === 'CLIENTE'" to="/carrinho">Meu carrinho</RouterLink>
          <RouterLink v-if="isAdmin" to="/produtos">Gerenciar produtos</RouterLink>
        </template>
        <template v-else>
          <RouterLink to="/login">Entrar na minha conta</RouterLink>
          <RouterLink to="/cadastro">Criar conta</RouterLink>
        </template>
      </nav>

      <div class="footer-social">
        <h2>Vamos nos conectar</h2>
        <component :is="social.href ? 'a' : 'div'" v-for="social in socialLinks" :key="social.name"
          class="social-card" :class="{ 'social-card--pending': !social.href }"
          :href="social.href || undefined" :target="social.href ? '_blank' : undefined"
          :rel="social.href ? 'noopener noreferrer' : undefined"
          :aria-label="social.href ? `${social.name} da loja (abre em nova aba)` : undefined">
          <svg v-if="social.icon === 'instagram'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
            <rect x="3" y="3" width="18" height="18" rx="5" /><circle cx="12" cy="12" r="4" /><circle cx="17.5" cy="6.5" r=".8" fill="currentColor" stroke="none" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M21 11.5a9 9 0 0 1-13.4 7.9L3 21l1.6-4.6A9 9 0 1 1 21 11.5Z" /><path d="m8 7 2 3-1 1c1 2 2 3 4 4l1-1 3 2c-1 2-3 2-5 1-3-1-5-4-6-6 0-2 0-3 2-4Z" />
          </svg>
          <span><strong>{{ social.name }}</strong><small>{{ social.href ? social.description : 'Link em breve' }}</small></span>
          <span v-if="social.href" class="social-arrow" aria-hidden="true">↗</span>
        </component>
      </div>
    </div>
    <div class="footer-bottom"><span>© {{ year }} {{ storeInfo.name }}</span><span>Versatilidade e liberdade para vestir.</span></div>
  </footer>
</template>

<style scoped>
.store-footer {
  margin-top: auto;
  padding: clamp(1.5rem, 4vw, 3rem) max(1.25rem, calc((100% - 1200px) / 2)) 1.2rem;
  color: #f8eee5;
  background: radial-gradient(ellipse at top left, #7a2c3c 0%, transparent 60%), var(--primary-wine-deep);
  border-top: 3px solid var(--gold-soft);
}
.footer-content { display: grid; grid-template-columns: 1.4fr .85fr 1fr; gap: clamp(1.5rem, 4vw, 4rem); }
.footer-kicker { margin: 0 0 .65rem; color: #e0c69b; font-size: .65rem; text-transform: uppercase; letter-spacing: .2em; }
.footer-name { display: inline-block; font: clamp(1.8rem, 3vw, 2.5rem)/1.15 'Palatino Linotype', Georgia, serif; text-decoration: none; }
.brand-description { margin: .85rem 0 1.4rem; color: #e0c9c9; font-size: .85rem; line-height: 1.8; }
.store-address { display: flex; align-items: flex-start; gap: .65rem; }
.store-address svg { width: 21px; height: 21px; flex-shrink: 0; color: #e0c69b; }
.store-address span { display: block; margin-bottom: .35rem; color: #e0c69b; font-size: .72rem; }
address { font-style: normal; font-size: .85rem; line-height: 1.6; }
h2 { margin: .25rem 0 1.1rem; color: #e0c69b; font-size: .75rem; font-weight: 600; letter-spacing: .08em; text-transform: uppercase; }
a { color: inherit; }
.footer-navigation { display: flex; flex-direction: column; align-items: flex-start; gap: .8rem; }
.footer-navigation h2 { margin-bottom: .3rem; }
.footer-navigation a { font-size: .85rem; text-decoration: none; padding: .2rem 0; }
.footer-navigation a:hover { color: #e0c69b; text-decoration: underline; text-underline-offset: 4px; }
.social-card { display: flex; align-items: center; gap: .8rem; padding: .85rem; margin-top: .65rem; border: 1px solid #a96d77; border-radius: 12px; text-decoration: none; }
a.social-card:hover { background: #ffffff0d; border-color: #e0c69b; }
.social-card svg { width: 24px; height: 24px; color: #e0c69b; flex-shrink: 0; }
.social-card strong, .social-card small { display: block; }
.social-card strong { font-size: .85rem; font-weight: 600; }
.social-card small { margin-top: .25rem; font-size: .72rem; color: #e0c9c9; }
.social-card--pending { border-style: dashed; }
.social-arrow { margin-left: auto; color: #e0c69b; }
.footer-bottom { display: flex; justify-content: space-between; flex-wrap: wrap; gap: .6rem 1rem; margin-top: 2rem; padding-top: 1.1rem; border-top: 1px solid #a96d77; font-size: .7rem; color: #e0c9c9; }
a:focus-visible { outline: 2px solid #e0c69b; outline-offset: 5px; border-radius: 4px; }
@media (max-width: 800px) {
  .footer-content { grid-template-columns: 1fr 1fr; }
  .footer-brand { grid-column: 1 / -1; }
}
@media (max-width: 460px) {
  .footer-content { grid-template-columns: 1fr; gap: 1.75rem; }
  .footer-bottom { flex-direction: column; line-height: 1.6; }
}
</style>
