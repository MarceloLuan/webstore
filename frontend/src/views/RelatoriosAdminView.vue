<script setup>
import { computed, onMounted, ref } from 'vue'
import { gerarRelatorioAdmin } from '@/services/clienteApi'
import { formatCurrency } from '@/utils/currency'

function dataLocal(date) {
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

const hoje = new Date()
const inicioPadrao = new Date(hoje)
inicioPadrao.setDate(inicioPadrao.getDate() - 29)

const filtros = ref({
  inicio: dataLocal(inicioPadrao),
  fim: dataLocal(hoje),
  status: '',
  categoria: '',
})
const relatorio = ref(null)
const loading = ref(true)
const erro = ref('')

const statusOpcoes = [
  ['AGUARDANDO_PAGAMENTO', 'Aguardando pagamento'], ['PENDENTE', 'Pendente'],
  ['PAGO', 'Pago'], ['PAGO_EM_REVISAO', 'Pago em revisão'], ['RECUSADO', 'Recusado'],
  ['CANCELADO', 'Cancelado'], ['EXPIRADO', 'Expirado'], ['REEMBOLSADO', 'Reembolsado'],
  ['REEMBOLSADO_PARCIAL', 'Reembolso parcial'], ['CHARGEBACK', 'Chargeback'],
  ['EM_MEDIACAO', 'Em mediação'], ['ERRO', 'Erro'],
]
const statusLabels = Object.fromEntries(statusOpcoes)
const categorias = [
  ['VESTIDO', 'Vestido'], ['BLUSA', 'Blusa'], ['CAMISA', 'Camisa'], ['CAMISETA', 'Camiseta'],
  ['CROPPED', 'Cropped'], ['CALCAS', 'Calças'], ['SHORTS', 'Shorts'], ['SAIA', 'Saia'],
  ['JAQUETA', 'Jaqueta'], ['CASACO', 'Casaco'], ['CONJUNTO', 'Conjunto'],
  ['MODA_INTIMA', 'Moda íntima'], ['MACACAO', 'Macacão'], ['ACESSORIO', 'Acessório'],
]

const periodoDescricao = computed(() => {
  if (!relatorio.value) return ''
  const { inicio, fim, status } = relatorio.value.filtros
  const base = `${formatarData(inicio)} a ${formatarData(fim)}`
  return status ? `${base} · ${statusLabels[status] || status}` : `${base} · todos os status`
})

function formatarData(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`))
}

async function carregar() {
  loading.value = true
  erro.value = ''
  try {
    relatorio.value = await gerarRelatorioAdmin(filtros.value)
  } catch (error) {
    erro.value = error.message || 'Não foi possível gerar o relatório.'
  } finally {
    loading.value = false
  }
}

function limparFiltros() {
  filtros.value = { inicio: dataLocal(inicioPadrao), fim: dataLocal(hoje), status: '', categoria: '' }
  carregar()
}

onMounted(carregar)
</script>

<template>
  <main class="reports-page">
    <header class="reports-hero">
      <div>
        <p class="eyebrow">Administração</p>
        <h1>Relatórios da loja</h1>
        <p>Acompanhe pedidos, produtos vendidos e a posição atual do estoque com dados registrados no sistema.</p>
      </div>
    </header>

    <form class="filter-card" @submit.prevent="carregar">
      <label>Data inicial<input v-model="filtros.inicio" type="date" required></label>
      <label>Data final<input v-model="filtros.fim" type="date" required></label>
      <label>Status dos pedidos<select v-model="filtros.status"><option value="">Todos</option><option v-for="item in statusOpcoes" :key="item[0]" :value="item[0]">{{ item[1] }}</option></select></label>
      <label>Categoria no estoque<select v-model="filtros.categoria"><option value="">Todas</option><option v-for="item in categorias" :key="item[0]" :value="item[0]">{{ item[1] }}</option></select></label>
      <div class="filter-actions"><button class="primary" type="submit" :disabled="loading">{{ loading ? 'Gerando...' : 'Aplicar filtros' }}</button><button type="button" @click="limparFiltros">Limpar</button></div>
      <small>O filtro de categoria é aplicado somente à posição de estoque.</small>
    </form>

    <p v-if="erro" class="error" role="alert">{{ erro }}</p>
    <section v-if="loading && !relatorio" class="panel state">Carregando relatório...</section>

    <template v-if="relatorio">
      <div class="section-heading"><div><p class="eyebrow">Pedidos</p><h2>Resumo do período</h2></div><span>{{ periodoDescricao }}</span></div>
      <section class="metric-grid" aria-label="Resumo dos pedidos">
        <article><small>Pedidos</small><strong>{{ relatorio.resumoPedidos.quantidadePedidos }}</strong></article>
        <article><small>Itens nos pedidos</small><strong>{{ relatorio.resumoPedidos.quantidadeItens }}</strong></article>
        <article><small>Valor dos pedidos</small><strong>{{ formatCurrency(relatorio.resumoPedidos.valorPedidos) }}</strong></article>
        <article><small>Ticket médio</small><strong>{{ formatCurrency(relatorio.resumoPedidos.ticketMedio) }}</strong></article>
        <article><small>Frete incluído</small><strong>{{ formatCurrency(relatorio.resumoPedidos.valorFrete) }}</strong></article>
      </section>
      <p v-if="!filtros.status" class="context-note">O valor considera pedidos de todos os status. Selecione “Pago” para visualizar apenas vendas aprovadas.</p>

      <div class="report-grid">
        <section class="panel">
          <h2>Pedidos por status</h2>
          <div v-if="relatorio.pedidosPorStatus.length" class="table-shell"><table><thead><tr><th>Status</th><th>Pedidos</th><th>Valor</th></tr></thead><tbody><tr v-for="item in relatorio.pedidosPorStatus" :key="item.status"><td>{{ statusLabels[item.status] || item.status }}</td><td>{{ item.quantidade }}</td><td>{{ formatCurrency(item.valor) }}</td></tr></tbody></table></div>
          <p v-else class="empty">Nenhum pedido encontrado no período.</p>
        </section>

        <section class="panel">
          <h2>Produtos nos pedidos</h2>
          <div v-if="relatorio.produtosVendidos.length" class="table-shell"><table><thead><tr><th>Produto</th><th>Unidades</th><th>Valor</th></tr></thead><tbody><tr v-for="item in relatorio.produtosVendidos" :key="item.produtoId"><td><strong>{{ item.nome }}</strong><small>{{ item.codigo }}</small></td><td>{{ item.quantidade }}</td><td>{{ formatCurrency(item.valor) }}</td></tr></tbody></table></div>
          <p v-else class="empty">Nenhum item encontrado nos pedidos filtrados.</p>
        </section>
      </div>

      <div class="section-heading stock-heading"><div><p class="eyebrow">Produtos</p><h2>Posição atual do estoque</h2></div><span>Atual, independente do período dos pedidos</span></div>
      <section class="metric-grid stock-metrics" aria-label="Resumo do estoque">
        <article><small>Produtos ativos</small><strong>{{ relatorio.resumoEstoque.produtosAtivos }}</strong></article>
        <article><small>Produtos inativos</small><strong>{{ relatorio.resumoEstoque.produtosInativos }}</strong></article>
        <article><small>Unidades disponíveis</small><strong>{{ relatorio.resumoEstoque.unidadesDisponiveis }}</strong></article>
        <article><small>Unidades reservadas</small><strong>{{ relatorio.resumoEstoque.unidadesReservadas }}</strong></article>
      </section>
      <section class="panel">
        <div v-if="relatorio.estoqueProdutos.length" class="table-shell"><table><thead><tr><th>Produto</th><th>Categoria</th><th>Situação</th><th>Total</th><th>Reservado</th><th>Disponível</th></tr></thead><tbody><tr v-for="item in relatorio.estoqueProdutos" :key="item.produtoId" :class="{ low: item.ativo && item.quantidadeDisponivel <= 3 }"><td><strong>{{ item.nome }}</strong><small>{{ item.codigo }}</small></td><td>{{ item.categoria || '-' }}</td><td>{{ item.ativo ? 'Ativo' : 'Inativo' }}</td><td>{{ item.quantidadeTotal }}</td><td>{{ item.quantidadeReservada }}</td><td><strong>{{ item.quantidadeDisponivel }}</strong></td></tr></tbody></table></div>
        <p v-else class="empty">Nenhum produto encontrado para a categoria selecionada.</p>
      </section>
    </template>
  </main>
</template>

<style scoped>
.reports-page { width:min(1180px,100%); display:grid; gap:1rem; color:#5b1a26; }
.reports-hero,.filter-card,.panel,.metric-grid article { border:1px solid rgba(106,27,44,.09); background:#fff; box-shadow:0 14px 32px rgba(106,27,44,.06); }
.reports-hero { padding:1.4rem; border-radius:24px; background:linear-gradient(135deg,#fff,#fbf6f3); }.reports-hero h1 { margin:.2rem 0; font:600 clamp(2rem,5vw,3rem)/1.05 Georgia,serif; }.reports-hero p:last-child { margin:.6rem 0 0; max-width:70ch; color:#6d5c61; line-height:1.5; }
.eyebrow { margin:0; color:#8c6a4d; font-size:.72rem; font-weight:700; letter-spacing:.14em; text-transform:uppercase; }
.filter-card { padding:1rem; border-radius:18px; display:grid; grid-template-columns:repeat(4,minmax(0,1fr)) auto; align-items:end; gap:.8rem; }.filter-card label { display:grid; gap:.35rem; color:#65565a; font-size:.82rem; font-weight:700; }.filter-card input,.filter-card select { width:100%; min-height:44px; padding:.6rem .7rem; border:1px solid #cfbfc3; border-radius:10px; background:#fff; color:#5b1a26; font:inherit; }.filter-actions { display:flex; gap:.5rem; }.filter-actions button { min-height:44px; padding:.65rem .9rem; border:1px solid #6a1b2c; border-radius:10px; background:#fff; color:#6a1b2c; font:inherit; font-weight:700; cursor:pointer; }.filter-actions .primary { background:#6a1b2c; color:#fff; }.filter-actions button:disabled { opacity:.65; cursor:wait; }.filter-card > small { grid-column:1/-1; color:#786b6f; }
.section-heading { display:flex; justify-content:space-between; align-items:end; gap:1rem; margin-top:.35rem; }.section-heading h2,.panel h2 { margin:.2rem 0 0; }.section-heading span { color:#786b6f; font-size:.85rem; text-align:right; }
.metric-grid { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:.75rem; }.metric-grid article { min-width:0; padding:1rem; border-radius:16px; display:grid; gap:.45rem; }.metric-grid small { color:#786b6f; }.metric-grid strong { font-size:clamp(1.15rem,2.4vw,1.55rem); overflow-wrap:anywhere; }.context-note { margin:0; padding:.8rem 1rem; border-radius:12px; background:#fff8e6; color:#755713; font-size:.85rem; }
.report-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:1rem; }.panel { min-width:0; padding:1.1rem; border-radius:20px; }.table-shell { width:100%; margin-top:.8rem; overflow-x:auto; }table { width:100%; border-collapse:collapse; min-width:480px; }th,td { padding:.75rem .65rem; border-bottom:1px solid #eee3e5; text-align:left; }th { color:#786b6f; font-size:.72rem; text-transform:uppercase; letter-spacing:.06em; }td { color:#5d4f54; font-size:.9rem; }td small { display:block; margin-top:.2rem; color:#8a7b7f; }.empty,.state { color:#786b6f; }.error { margin:0; padding:.8rem; border-radius:10px; background:#fff0f0; color:#8a1d1d; }.stock-heading { margin-top:.7rem; }.stock-metrics { grid-template-columns:repeat(4,minmax(0,1fr)); }.low { background:#fff9eb; }
@media(max-width:960px){.filter-card{grid-template-columns:repeat(2,minmax(0,1fr))}.metric-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.report-grid{grid-template-columns:1fr}}
@media(max-width:600px){.reports-page{width:100%}.reports-hero,.panel{padding:1rem;border-radius:16px}.filter-card{grid-template-columns:1fr}.filter-actions{display:grid;grid-template-columns:1fr 1fr}.filter-card input,.filter-card select{font-size:16px}.section-heading{align-items:flex-start;flex-direction:column}.section-heading span{text-align:left}.metric-grid,.stock-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.metric-grid article{padding:.85rem}.table-shell{margin-right:-.25rem}}
@media(max-width:380px){.metric-grid,.stock-metrics{grid-template-columns:1fr}}
</style>
