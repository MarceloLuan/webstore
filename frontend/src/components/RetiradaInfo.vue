<script setup>
import { storeInfo } from '@/config/store'
</script>

<template>
  <section class="pickup-info" aria-label="Informações para retirada na loja">
    <strong>Retirada na loja</strong>
    <p>{{ storeInfo.name }}</p>
    <address>{{ storeInfo.address || 'Confirme o endereço de retirada com a loja.' }}</address>
    <ul>
      <li v-for="instrucao in storeInfo.pickupInstructions" :key="instrucao">{{ instrucao }}</li>
    </ul>
    <a v-if="storeInfo.whatsappUrl" :href="storeInfo.whatsappUrl" target="_blank" rel="noopener noreferrer">
      Combinar retirada pelo WhatsApp
    </a>
  </section>
</template>

<style scoped>
.pickup-info { padding: 1rem; border: 1px solid #e3d5d8; border-radius: 12px; background: #faf5f3; color: #5b1a26; line-height: 1.5; overflow-wrap: anywhere; }
.pickup-info p { margin: 0.4rem 0 0; }
.pickup-info address { font-style: normal; }
.pickup-info ul { margin: 0.7rem 0; padding-left: 1.2rem; font-size: 0.88rem; }
.pickup-info a { color: #7d2032; font-weight: 600; }
.pickup-info a:focus-visible { outline: 2px solid #7d2032; outline-offset: 3px; }
</style>
