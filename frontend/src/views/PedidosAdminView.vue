<script setup>
import { computed, onMounted, ref } from 'vue'
import { listarPedidosAdmin, buscarPedidoAdmin, atualizarStatusEntregaAdmin } from '@/services/clienteApi'
import { formatCurrency } from '@/utils/currency'

const pedidos = ref([])
const selecionado = ref(null)
const loading = ref(true)
const saving = ref(false)
const refreshing = ref(false)
const error = ref('')
const financeiro = {
  AGUARDANDO_PAGAMENTO: 'Aguardando pagamento', PENDENTE: 'Pendente', PAGO: 'Aprovado',
  PAGO_EM_REVISAO: 'Pago em revisão', RECUSADO: 'Recusado', CANCELADO: 'Cancelado',
  EXPIRADO: 'Expirado', REEMBOLSADO: 'Reembolsado', REEMBOLSADO_PARCIAL: 'Reembolso parcial',
  CHARGEBACK: 'Chargeback', EM_MEDIACAO: 'Em mediação', ERRO: 'Erro',
}
const entrega = {
  RECEBIDO: 'Recebido', EM_SEPARACAO: 'Em separação',
  PRONTO_PARA_RETIRADA: 'Pronto para retirada', ENVIADO: 'Enviado',
  ENTREGUE: 'Entregue', CANCELADO: 'Cancelado',
}
const rastreio = ref('')

const proximosStatus = {
  RECEBIDO: ['RECEBIDO', 'EM_SEPARACAO', 'PRONTO_PARA_RETIRADA', 'ENVIADO', 'CANCELADO'],
  EM_SEPARACAO: ['EM_SEPARACAO', 'PRONTO_PARA_RETIRADA', 'ENVIADO', 'CANCELADO'],
  PRONTO_PARA_RETIRADA: ['PRONTO_PARA_RETIRADA', 'ENTREGUE', 'CANCELADO'],
  ENVIADO: ['ENVIADO', 'ENTREGUE', 'CANCELADO'],
  ENTREGUE: ['ENTREGUE'],
  CANCELADO: ['CANCELADO'],
}

const opcoes = computed(() => {
  if (!selecionado.value) return []
  const permitidos = proximosStatus[selecionado.value.statusEntrega] || [selecionado.value.statusEntrega]
  return permitidos
    .filter((status) => !(selecionado.value.modalidade === 'RETIRADA' && status === 'ENVIADO'))
    .filter((status) => !(selecionado.value.modalidade === 'ENTREGA' && status === 'PRONTO_PARA_RETIRADA'))
    .map((value) => ({ value, label: entrega[value] }))
})

const rastreioObrigatorio = computed(() => selecionado.value?.modalidade === 'ENTREGA' && selecionado.value?.statusEntrega === 'ENVIADO')

async function abrir(id) {
  error.value = ''
  try { selecionado.value = await buscarPedidoAdmin(id); rastreio.value = selecionado.value.codigoRastreio || '' } catch (e) { error.value = e.message }
}

async function atualizarDados() {
  if (refreshing.value) return
  refreshing.value = true
  error.value = ''
  try {
    pedidos.value = await listarPedidosAdmin()
    if (selecionado.value) await abrir(selecionado.value.id)
  } catch (e) {
    error.value = e.message || 'Não foi possível atualizar os pedidos.'
  } finally {
    refreshing.value = false
  }
}

async function salvar() {
  if (!selecionado.value || saving.value) return
  if (rastreioObrigatorio.value && !rastreio.value.trim()) {
    error.value = 'Informe o código de rastreio antes de marcar uma entrega como enviada.'
    return
  }
  saving.value = true; error.value = ''
  try {
    selecionado.value = await atualizarStatusEntregaAdmin(selecionado.value.id, selecionado.value.statusEntrega, rastreio.value)
    rastreio.value = selecionado.value.codigoRastreio || ''
    const index = pedidos.value.findIndex(item => item.id === selecionado.value.id)
    if (index >= 0) pedidos.value[index] = selecionado.value
  } catch (e) { error.value = e.message } finally { saving.value = false }
}
onMounted(async () => {
  try { pedidos.value = await listarPedidosAdmin() } catch (e) { error.value = e.message } finally { loading.value = false }
})
</script>

<template>
  <main class="admin-orders">
    <header><p class="eyebrow">Operação da loja</p><h1>Gerenciar pedidos</h1><p>Pagamento e entrega aparecem separados para evitar decisões ambíguas.</p><button class="refresh-button" type="button" :disabled="refreshing" @click="atualizarDados">{{ refreshing ? 'Atualizando...' : 'Atualizar' }}</button></header>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div v-if="loading" class="card">Carregando pedidos...</div>
    <div v-else class="orders-layout">
      <section class="card list" aria-label="Lista de pedidos">
        <h2>Pedidos</h2>
        <button v-for="pedido in pedidos" :key="pedido.id" type="button" class="order-row" :class="{ selected: selecionado?.id === pedido.id }" @click="abrir(pedido.id)">
          <span><strong>#{{ pedido.id }}</strong> · {{ pedido.clienteNome }}<small>{{ new Date(pedido.criadoEm).toLocaleString('pt-BR') }}</small></span>
          <span class="money">{{ formatCurrency(pedido.total) }}<small>{{ financeiro[pedido.statusPagamento] }}</small></span>
        </button>
        <p v-if="!pedidos.length">Nenhum pedido cadastrado.</p>
      </section>
      <section v-if="selecionado" class="card detail" aria-label="Detalhes do pedido">
        <div class="detail-heading"><div><p class="eyebrow">Pedido #{{ selecionado.id }}</p><h2>{{ selecionado.clienteNome }}</h2></div><strong>{{ formatCurrency(selecionado.total) }}</strong></div>
        <div class="two-columns"><div><h3>Cliente</h3><p>{{ selecionado.clienteEmail }}</p><p>{{ selecionado.clienteTelefone || 'Telefone não informado' }}</p></div><div><h3>Pagamento</h3><p>{{ financeiro[selecionado.statusPagamento] || selecionado.statusPagamento }}</p><p>Reserva: {{ selecionado.reservaStatus || 'Não aplicável' }}</p></div></div>
        <h3>Produtos</h3><ul class="products"><li v-for="item in selecionado.itens" :key="item.produtoTamanhoId"><span>{{ item.quantidade }}× {{ item.produto }} · {{ item.tamanho }}</span><strong>{{ formatCurrency(item.subtotal) }}</strong></li></ul>
        <div class="totals"><p>Mercadorias <strong>{{ formatCurrency(selecionado.subtotalMercadorias || 0) }}</strong></p><p>Frete <strong>{{ formatCurrency(selecionado.valorFrete || 0) }}</strong></p><p>Total <strong>{{ formatCurrency(selecionado.total) }}</strong></p><small>{{ selecionado.prazoFrete || 'Prazo não informado' }}</small></div>
        <div><h3>Recebimento</h3><p>{{ selecionado.modalidade === 'RETIRADA' ? 'Retirada na loja' : selecionado.modalidade === 'ENTREGA' ? 'Entrega' : 'Não informado' }}</p><address v-if="selecionado.enderecoEntrega">{{ selecionado.enderecoEntrega.destinatario }} · {{ selecionado.enderecoEntrega.rua }}, {{ selecionado.enderecoEntrega.numero }}<br>{{ selecionado.enderecoEntrega.bairro }} · {{ selecionado.enderecoEntrega.cidade }}/{{ selecionado.enderecoEntrega.uf }} · CEP {{ selecionado.enderecoEntrega.cep }}</address><p v-else>Sem endereço de entrega.</p></div>
        <div class="delivery-editor"><h3>Status da entrega</h3><select v-model="selecionado.statusEntrega"><option v-for="opcao in opcoes" :key="opcao.value" :value="opcao.value">{{ opcao.label }}</option></select><label v-if="selecionado.modalidade === 'ENTREGA' && ['ENVIADO', 'ENTREGUE'].includes(selecionado.statusEntrega)" for="codigo-rastreio">Código de rastreio</label><input v-if="selecionado.modalidade === 'ENTREGA' && ['ENVIADO', 'ENTREGUE'].includes(selecionado.statusEntrega)" id="codigo-rastreio" v-model="rastreio" maxlength="80" placeholder="Informe o código" /><p v-if="rastreioObrigatorio" class="tracking-hint">O código de rastreio é obrigatório para marcar uma entrega como enviada.</p><button type="button" :disabled="saving || (rastreioObrigatorio && !rastreio.trim())" @click="salvar">{{ saving ? 'Salvando...' : 'Salvar status' }}</button></div>
      </section>
      <section v-else class="card empty">Selecione um pedido para visualizar os detalhes.</section>
    </div>
  </main>
</template>

<style scoped>
.admin-orders { width: min(1180px, calc(100% - 2rem)); margin: 0 auto; display: grid; gap: 1rem; color: #5b1a26; }
header h1 { margin: .2rem 0; font: 600 clamp(2rem, 5vw, 3rem)/1.1 Georgia, serif; } header > p:last-of-type { color: #786b6f; }.refresh-button { margin-top: .45rem; min-height: 40px; padding: .5rem .9rem; border: 1px solid #6a1b2c; border-radius: 9px; background: #fff; color: #6a1b2c; font: inherit; font-weight: 700; cursor: pointer; }.refresh-button:disabled { opacity: .6; cursor: wait; }
.eyebrow { margin: 0; color: #9a6b47; font-size: .75rem; letter-spacing: .12em; text-transform: uppercase; }
.orders-layout { display: grid; grid-template-columns: minmax(280px,.8fr) minmax(0,1.5fr); gap: 1rem; align-items: start; }
.card { padding: 1.1rem; border: 1px solid #eadde0; border-radius: 18px; background: #fff; box-shadow: 0 12px 28px #5b1a2610; } .list { display: grid; gap: .5rem; } .list h2 { margin: 0 0 .5rem; }
.order-row { display: flex; justify-content: space-between; gap: .7rem; padding: .8rem; border: 1px solid transparent; border-radius: 12px; background: #fbf7f5; color: inherit; text-align: left; cursor: pointer; } .order-row:hover,.order-row.selected { border-color: #bd8792; background: #f5e9e7; } .order-row small,.money small { display: block; margin-top: .25rem; color: #806f73; font-size: .78rem; } .money { text-align: right; }
.detail-heading,.two-columns,.totals p { display: flex; justify-content: space-between; gap: 1rem; } .detail h2 { margin: .15rem 0 1rem; } .detail h3 { margin: 1rem 0 .4rem; font-size: .95rem; } .two-columns > div { flex: 1; } .detail p { margin: .2rem 0; color: #786b6f; } address { font-style: normal; line-height: 1.6; } .products { display: grid; gap: .4rem; margin: 0; padding: 0; list-style: none; } .products li { display: flex; justify-content: space-between; gap: 1rem; padding: .6rem 0; border-bottom: 1px solid #f0e7e8; } .totals { margin-top: 1rem; padding-top: .7rem; border-top: 1px solid #eadde0; } .totals p { color: #786b6f; } .totals p:last-of-type { color: #5b1a26; font-size: 1.1rem; } .totals small { color: #806f73; }
.delivery-editor { margin-top: 1rem; padding-top: .8rem; border-top: 1px solid #eadde0; } select,input,.delivery-editor button { min-height: 42px; padding: .6rem .8rem; border: 1px solid #cdbbc0; border-radius: 9px; font: inherit; } .delivery-editor select,.delivery-editor input { width: 100%; color: #5b1a26; background: #fff; }.delivery-editor label { display: block; margin: .8rem 0 .35rem; color: #786b6f; font-size: .85rem; font-weight: 700; } .tracking-hint { margin-top: .45rem !important; color: #755713 !important; font-size: .8rem; } .delivery-editor button { margin-top: .6rem; border: 0; background: #6a1b2c; color: #fff; font-weight: 700; cursor: pointer; } .delivery-editor button:disabled { opacity: .6; } .error { padding: .8rem; border-radius: 10px; background: #fff0f0; color: #8a1d1d; } .empty { color: #786b6f; }
@media (max-width: 800px) { .orders-layout { grid-template-columns: 1fr; } }
</style>
