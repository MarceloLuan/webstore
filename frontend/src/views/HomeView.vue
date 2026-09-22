<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import ProdutoImagem from '@/components/produtos/ProdutoImagem.vue'
import { useProductStore } from '@/services/produtoStore'
import NovidadesCarrossel from '@/components/produtos/NovidadesCarrossel.vue'
import { formatCurrency } from '@/utils/currency'

const route = useRoute()
const loading = ref(true)
const { activeProducts, loadProducts } = useProductStore()

const searchTerm = computed(() => (typeof route.query.busca === 'string' ? route.query.busca.trim() : ''))
const selectedCategory = computed(() => (
  typeof route.query.categoria === 'string' ? route.query.categoria.trim() : ''
))
const selectedSize = computed(() => (
  typeof route.query.tamanho === 'string' ? route.query.tamanho.trim() : ''
))
const selectedOrder = computed(() => (
  typeof route.query.ordenar === 'string' ? route.query.ordenar : 'relevancia'
))

function normalizeSearchText(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('pt-BR')
    .trim()
}

function hasStock(product) {
  return product.tamanhos?.some((item) => Number(item.quantidade) > 0) ?? false
}

const filteredProducts = computed(() => {
  const search = normalizeSearchText(searchTerm.value)
  const category = normalizeSearchText(selectedCategory.value)

  const matches = activeProducts.value.filter((product) => {
    const matchesSearch = !search || normalizeSearchText(product.nome).includes(search)
    const matchesCategory = !category || normalizeSearchText(product.categoria) === category
    const matchesSize = !selectedSize.value || product.tamanhos?.some(
      (item) => item.tamanho === selectedSize.value && Number(item.quantidade) > 0,
    )
    return matchesSearch && matchesCategory && matchesSize
  })

  if (selectedOrder.value === 'menor-preco') {
    return [...matches].sort((a, b) => Number(a.preco) - Number(b.preco)).sort((a, b) => Number(hasStock(b)) - Number(hasStock(a)))
  }

  if (selectedOrder.value === 'maior-preco') {
    return [...matches].sort((a, b) => Number(b.preco) - Number(a.preco)).sort((a, b) => Number(hasStock(b)) - Number(hasStock(a)))
  }

  if (selectedOrder.value === 'nome') {
    return [...matches].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).sort((a, b) => Number(hasStock(b)) - Number(hasStock(a)))
  }

  return matches.sort((a, b) => Number(hasStock(b)) - Number(hasStock(a)))
})

const hasActiveFilter = computed(() => Boolean(
  searchTerm.value || selectedCategory.value || selectedSize.value || selectedOrder.value !== 'relevancia',
))
const resultTitle = computed(() => {
  if (searchTerm.value && selectedCategory.value) {
    return `${selectedCategory.value}: resultados para “${searchTerm.value}”`
  }

  if (searchTerm.value) {
    return `Produtos encontrados para “${searchTerm.value}”`
  }

  if (selectedCategory.value) {
    return selectedCategory.value
  }

  return 'Produtos em destaque'
})

onMounted(async () => {
  try {
    await loadProducts()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="home-page">
    <NovidadesCarrossel v-if="!hasActiveFilter && !loading" :products="activeProducts" />

    <section class="featured-section">
      <div class="section-heading">
        <div>
          <p class="panel-kicker">{{ hasActiveFilter ? 'Produtos filtrados' : 'Lançamentos' }}</p>
          <h2>{{ resultTitle }}</h2>
        </div>
        <span v-if="hasActiveFilter" class="result-count">
          {{ filteredProducts.length }} {{ filteredProducts.length === 1 ? 'produto' : 'produtos' }}
        </span>
      </div>

      <div v-if="loading" class="product-grid loading-grid" aria-live="polite" aria-busy="true">
        <span class="sr-only">Carregando produtos...</span>
        <article v-for="index in 4" :key="index" class="product-card skeleton-card" aria-hidden="true">
          <span class="skeleton skeleton-image"></span>
          <span class="skeleton skeleton-label"></span>
          <span class="skeleton skeleton-title"></span>
          <span class="skeleton skeleton-price"></span>
        </article>
      </div>

      <div v-else-if="filteredProducts.length" class="product-grid">
        <RouterLink
          v-for="product in filteredProducts"
          :key="product.id"
          class="product-card"
          :class="{ 'product-card--sold-out': !hasStock(product) }"
          :to="{ name: 'produto-detalhes', params: { id: product.id } }"
          :aria-label="`Ver detalhes de ${product.nome}`"
        >
          <div class="product-image-shell">
            <ProdutoImagem :src="product.imagem" :alt="product.nome" ratio="3 / 4" />
            <span v-if="!hasStock(product)" class="sold-out-badge">Sem estoque</span>
          </div>
          <small>{{ product.destaque }}</small>
          <strong>{{ product.nome }}</strong>
          <span>{{ formatCurrency(product.preco) }}</span>
          <span class="view-product">Ver produto</span>
        </RouterLink>
      </div>

      <div v-else class="empty-search" role="status">
        <strong>Nenhum produto encontrado.</strong>
        <p>Tente outra pesquisa ou remova algum dos filtros aplicados.</p>
      </div>
    </section>

  </section>
</template>

<style scoped>
.home-page {
  width: min(1280px, calc(100% - 0.5rem));
  margin: 0 auto;
  padding: 0 0 1.2rem;
  display: grid;
  gap: 1rem;
}

.panel-kicker {
  margin: 0 0 0.35rem;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 0.74rem;
  color: #8c6a4d;
}

h2,
p {
  margin: 0;
}

.featured-section {
  display: grid;
  gap: 0.9rem;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 1rem;
}

.section-heading h2 {
  color: #5b1a26;
  font-size: clamp(1.5rem, 2vw, 2rem);
  font-family: 'Iowan Old Style', 'Palatino Linotype', Georgia, serif;
}

.section-note {
  color: #6f5f63;
  max-width: 42ch;
  line-height: 1.55;
  text-align: right;
}

.result-count {
  color: #6f5f63;
  font-size: 0.9rem;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.9rem;
}

.empty-search {
  padding: clamp(1.5rem, 4vw, 3rem);
  border: 1px dashed rgba(106, 27, 44, 0.24);
  border-radius: 18px;
  background: #fffaf7;
  text-align: center;
}

.skeleton-card {
  pointer-events: none;
  overflow: hidden;
}

.skeleton {
  display: block;
  border-radius: 8px;
  background: linear-gradient(90deg, #eee4df 25%, #f9f3ef 50%, #eee4df 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.35s ease-in-out infinite;
}

.skeleton-image {
  width: 100%;
  aspect-ratio: 3 / 4;
  border-radius: 12px;
}

.skeleton-label {
  width: 38%;
  height: 0.65rem;
  margin-top: 0.45rem;
}

.skeleton-title {
  width: 72%;
  height: 0.95rem;
}

.skeleton-price {
  width: 46%;
  height: 1.1rem;
  margin-top: 0.2rem;
}

@keyframes skeleton-loading {
  to {
    background-position-x: -200%;
  }
}

.empty-search strong {
  color: #5b1a26;
  font-size: 1.1rem;
}

.empty-search p {
  margin-top: 0.35rem;
  color: #6f5f63;
}

.product-card {
  border-radius: 18px;
  padding: 1rem;
  background: #fffaf7;
  border: 1px solid rgba(106, 27, 44, 0.08);
  display: grid;
  gap: 0.28rem;
  min-height: 132px;
  color: inherit;
  text-decoration: none;
  transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease;
}

.product-card:hover,
.product-card:focus-visible {
  transform: translateY(-3px);
  border-color: rgba(106, 27, 44, 0.2);
  box-shadow: 0 14px 28px rgba(106, 27, 44, 0.1);
}

.product-card:focus-visible {
  outline: 3px solid rgba(106, 27, 44, 0.18);
  outline-offset: 2px;
}

.product-card :deep(.product-media) {
  margin-bottom: 0.45rem;
}

.product-image-shell { position: relative; }

.product-card--sold-out { background: #f4efed; }

.product-card--sold-out :deep(.product-media) {
  opacity: 0.52;
  filter: grayscale(0.45);
}

.sold-out-badge {
  position: absolute;
  top: 0.7rem;
  right: 0.7rem;
  border-radius: 999px;
  padding: 0.42rem 0.7rem;
  background: rgba(79, 26, 37, 0.92);
  color: #fff !important;
  font-size: 0.7rem !important;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.product-card small {
  color: #8f6a75;
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.14em;
}

.product-card strong {
  color: #4f1a25;
  font-size: 0.98rem;
  font-weight: 600;
}

.product-card span {
  color: #5b1a26;
  font-size: 1.18rem;
  font-weight: 700;
}

.product-card .view-product {
  margin-top: 0.35rem;
  color: #8c6a4d;
  font-size: 0.82rem;
  font-weight: 600;
}

@media (max-width: 960px) {
  .product-grid {
    grid-template-columns: 1fr;
  }

  .section-heading {
    align-items: start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .home-page {
    width: calc(100% - 0.25rem);
  }

  .product-grid {
    gap: 0.75rem;
  }
}
</style>
