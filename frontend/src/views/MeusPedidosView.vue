<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { listarMeusPedidos } from '@/services/clienteApi'
import { formatCurrency } from '@/utils/currency'

const pedidos = ref([])
const loading = ref(true)
const error = ref('')

const statusInfo = {
  AGUARDANDO_PAGAMENTO: ['Aguardando pagamento', 'waiting'],
  PENDENTE: ['Pagamento pendente', 'pending'],
  PAGO: ['Pago', 'paid'],
  RECUSADO: ['Recusado', 'failed'],
  CANCELADO: ['Cancelado', 'failed'],
  ERRO: ['Erro no pagamento', 'failed'],
}

function formatDate(value) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

onMounted(async () => {
  try {
    pedidos.value = await listarMeusPedidos()
  } catch (requestError) {
    error.value = requestError.message || 'Não foi possível carregar seus pedidos.'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="orders-page">
    <header class="orders-header">
      <div><p>Histórico de compras</p><h1>Meus pedidos</h1></div>
      <RouterLink to="/home">Continuar comprando</RouterLink>
    </header>

    <div v-if="loading" class="state-card">Carregando seus pedidos...</div>
    <div v-else-if="error" class="state-card error">{{ error }}</div>
    <div v-else-if="!pedidos.length" class="state-card">
      <h2>Você ainda não fez nenhum pedido</h2>
      <RouterLink to="/home">Explorar produtos</RouterLink>
    </div>

    <div v-else class="orders-list">
      <article v-for="pedido in pedidos" :key="pedido.id" class="order-card">
        <header>
          <div><small>Pedido</small><strong>#{{ pedido.id }}</strong></div>
          <div><small>Realizado em</small><span>{{ formatDate(pedido.criadoEm) }}</span></div>
          <span class="status" :class="`status--${(statusInfo[pedido.status] || statusInfo.ERRO)[1]}`">
            {{ (statusInfo[pedido.status] || statusInfo.ERRO)[0] }}
          </span>
        </header>
        <ul>
          <li v-for="(item, index) in pedido.itens" :key="index">
            <span><strong>{{ item.nome }}</strong><small>Tamanho {{ item.tamanho }} · {{ item.quantidade }} un.</small></span>
            <span>{{ formatCurrency(Number(item.precoUnitario) * item.quantidade) }}</span>
          </li>
        </ul>
        <footer><span>Total</span><strong>{{ formatCurrency(pedido.total) }}</strong></footer>
      </article>
    </div>
  </section>
</template>

<style scoped>
.orders-page { width: min(1000px, calc(100% - .5rem)); display: grid; gap: 1rem; }
.orders-header, .order-card, .state-card { background: #fff; border: 1px solid rgba(106,27,44,.1); border-radius: 22px; box-shadow: 0 14px 32px rgba(106,27,44,.07); }
.orders-header { padding: 1.4rem; display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
.orders-header p, .orders-header h1 { margin: 0; }
.orders-header p { color: #8c6a4d; text-transform: uppercase; letter-spacing: .14em; font-size: .72rem; }
.orders-header h1 { color: #5b1a26; font-family: Georgia, serif; }
a { color: #6a1b2c; font-weight: 700; }
.orders-list { display: grid; gap: .9rem; }
.order-card { overflow: hidden; }
.order-card > header { padding: 1rem 1.2rem; background: #fffaf7; display: grid; grid-template-columns: 1fr 1.5fr auto; align-items: center; gap: 1rem; }
.order-card header div { display: grid; gap: .2rem; }
small { color: #786b6f; }
.status { border-radius: 999px; padding: .45rem .7rem; font-size: .78rem; font-weight: 800; }
.status--paid { background: #e6f5ea; color: #24613a; }
.status--pending, .status--waiting { background: #fff4d8; color: #755713; }
.status--failed { background: #fde7e7; color: #8a1d1d; }
ul { list-style: none; margin: 0; padding: .5rem 1.2rem; }
li { padding: .75rem 0; display: flex; justify-content: space-between; gap: 1rem; border-bottom: 1px solid rgba(106,27,44,.08); }
li:last-child { border-bottom: 0; }
li > span:first-child { display: grid; gap: .2rem; }
.order-card footer { padding: 1rem 1.2rem; display: flex; justify-content: flex-end; align-items: center; gap: 1rem; background: #faf5f2; }
.order-card footer strong { color: #5b1a26; font-size: 1.15rem; }
.state-card { padding: 2rem; text-align: center; color: #65565a; }
.error { color: #8a1d1d; }
@media (max-width: 650px) { .orders-header { align-items: flex-start; flex-direction: column; } .order-card > header { grid-template-columns: 1fr; } }
</style>
