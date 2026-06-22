<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import ProdutoImagem from '@/components/produtos/ProdutoImagem.vue'
import { getUser } from '@/services/auth'
import { useProductStore } from '@/services/produtoStore'

const route = useRoute()
const user = ref(getUser())
const loading = ref(true)
const selectedSize = ref('')
const quantity = ref(1)
const actionMessage = ref('')

const { findProductById, loadProducts } = useProductStore()

const productId = computed(() => Number(route.params.id))
const product = computed(() => findProductById(productId.value))
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const isClient = computed(() => user.value?.role === 'CLIENTE')
const availableSizes = computed(() => (
  Array.isArray(product.value?.tamanhos)
    ? product.value.tamanhos.filter((item) => Number(item.quantidade) > 0)
    : []
))
const selectedStock = computed(() => (
  availableSizes.value.find((item) => item.tamanho === selectedSize.value)?.quantidade || 0
))

function formatPrice(value) {
  return Number(value || 0).toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  })
}

function showFutureAction(action) {
  if (!selectedSize.value) {
    actionMessage.value = 'Selecione um tamanho antes de continuar.'
    return
  }

  actionMessage.value = `${action} estará disponível na próxima etapa do sistema.`
}

watch(selectedSize, () => {
  quantity.value = 1
  actionMessage.value = ''
})

onMounted(async () => {
  await loadProducts()
  loading.value = false
})
</script>

<template>
  <section class="details-page">
    <RouterLink class="back-link" to="/home">← Voltar aos produtos</RouterLink>

    <div v-if="loading" class="state-card" role="status">Carregando produto...</div>

    <div v-else-if="!product" class="state-card">
      <h1>Produto não encontrado</h1>
      <p>Este produto pode ter sido removido ou não está mais disponível.</p>
      <RouterLink class="primary-link" to="/home">Explorar outros produtos</RouterLink>
    </div>

    <article v-else class="product-details">
      <div class="image-panel">
        <ProdutoImagem :src="product.imagem" :alt="product.nome" ratio="4 / 5" />
      </div>

      <div class="details-content">
        <div>
          <p class="category-label">{{ product.categoria || 'Produto' }}</p>
          <h1>{{ product.nome }}</h1>
          <p class="highlight">{{ product.destaque }}</p>
        </div>

        <strong class="price">{{ formatPrice(product.preco) }}</strong>

        <div class="description">
          <h2>Sobre o produto</h2>
          <p>{{ product.descricao || 'Produto selecionado especialmente para a nossa vitrine.' }}</p>
        </div>

        <div class="availability">
          <h2>Tamanhos disponíveis</h2>
          <div v-if="availableSizes.length" class="size-summary">
            <span v-for="item in availableSizes" :key="item.tamanho">
              {{ item.tamanho }}
              <small>{{ item.quantidade }} em estoque</small>
            </span>
          </div>
          <p v-else class="unavailable">Produto temporariamente sem estoque.</p>
        </div>

        <section v-if="isClient && availableSizes.length" class="purchase-preview" aria-labelledby="purchase-title">
          <div>
            <p class="preview-label">Próxima etapa</p>
            <h2 id="purchase-title">Escolha suas opções</h2>
          </div>

          <div class="option-grid">
            <label>
              Tamanho
              <select v-model="selectedSize">
                <option value="">Selecione</option>
                <option v-for="item in availableSizes" :key="item.tamanho" :value="item.tamanho">
                  {{ item.tamanho }}
                </option>
              </select>
            </label>

            <label>
              Quantidade
              <input
                v-model.number="quantity"
                type="number"
                min="1"
                :max="selectedStock || 1"
                :disabled="!selectedSize"
              />
            </label>
          </div>

          <div class="purchase-actions">
            <button type="button" class="secondary-action" @click="showFutureAction('Adicionar ao carrinho')">
              Adicionar ao carrinho
            </button>
            <button type="button" class="primary-action" @click="showFutureAction('Comprar agora')">
              Comprar agora
            </button>
          </div>

          <p v-if="actionMessage" class="action-message" role="status">{{ actionMessage }}</p>
        </section>

        <section v-else-if="isAdmin" class="admin-note">
          <div>
            <p class="preview-label">Visualização administrativa</p>
            <strong>Confira como o produto aparece para os usuários.</strong>
          </div>
          <RouterLink class="primary-link" to="/produtos">Gerenciar produtos</RouterLink>
        </section>
      </div>
    </article>
  </section>
</template>

<style scoped>
.details-page {
  width: min(1180px, calc(100% - 0.5rem));
  display: grid;
  gap: 1rem;
}

.back-link {
  width: fit-content;
  color: #6a1b2c;
  font-weight: 700;
  text-decoration: none;
}

.back-link:hover {
  text-decoration: underline;
}

.product-details {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(0, 1.1fr);
  gap: clamp(1.2rem, 3vw, 2.5rem);
  padding: clamp(1rem, 2.5vw, 2rem);
  border: 1px solid rgba(106, 27, 44, 0.1);
  border-radius: 26px;
  background: #fff;
  box-shadow: 0 18px 44px rgba(106, 27, 44, 0.09);
}

.image-panel {
  min-width: 0;
}

.image-panel :deep(.product-media) {
  max-height: 680px;
}

.details-content {
  min-width: 0;
  display: grid;
  align-content: start;
  gap: 1.2rem;
}

.category-label,
.preview-label {
  margin: 0 0 0.35rem;
  color: #8c6a4d;
  font-size: 0.75rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  color: #5b1a26;
  font-family: 'Iowan Old Style', 'Palatino Linotype', Georgia, serif;
  font-size: clamp(2rem, 4vw, 3.6rem);
  line-height: 1.05;
}

h2 {
  color: #5b1a26;
  font-size: 1rem;
}

.highlight {
  margin-top: 0.45rem;
  color: #806d72;
}

.price {
  color: #5b1a26;
  font-size: clamp(1.5rem, 3vw, 2.1rem);
}

.description,
.availability {
  display: grid;
  gap: 0.55rem;
}

.description p {
  color: #65565a;
  line-height: 1.7;
}

.size-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
}

.size-summary span {
  min-width: 88px;
  display: grid;
  gap: 0.15rem;
  padding: 0.65rem 0.8rem;
  border: 1px solid rgba(106, 27, 44, 0.12);
  border-radius: 12px;
  background: #fbf7f5;
  color: #5b1a26;
  font-weight: 700;
}

.size-summary small {
  color: #786b6f;
  font-size: 0.7rem;
  font-weight: 500;
}

.unavailable {
  color: #8a1d1d;
}

.purchase-preview,
.admin-note {
  display: grid;
  gap: 0.9rem;
  padding: 1rem;
  border: 1px solid rgba(106, 27, 44, 0.12);
  border-radius: 18px;
  background: #fbf7f5;
}

.option-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(120px, 0.45fr);
  gap: 0.75rem;
}

label {
  display: grid;
  gap: 0.4rem;
  color: #5d4f54;
  font-size: 0.9rem;
  font-weight: 600;
}

select,
input {
  width: 100%;
  min-width: 0;
  padding: 0.78rem 0.85rem;
  border: 1px solid rgba(106, 27, 44, 0.16);
  border-radius: 11px;
  background: #fff;
  color: #4b3a3a;
  font: inherit;
}

select:focus,
input:focus {
  outline: 3px solid rgba(106, 27, 44, 0.12);
  border-color: #6a1b2c;
}

.purchase-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
}

.primary-action,
.secondary-action,
.primary-link {
  border: 0;
  border-radius: 999px;
  padding: 0.82rem 1rem;
  font-weight: 700;
  cursor: pointer;
  text-align: center;
  text-decoration: none;
}

.primary-action,
.primary-link {
  background: #6a1b2c;
  color: #fff;
}

.secondary-action {
  background: #eee2dd;
  color: #5b1a26;
}

.action-message {
  padding: 0.75rem 0.85rem;
  border-radius: 12px;
  background: #fff5dc;
  color: #6a4d0c;
  line-height: 1.45;
}

.admin-note {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.state-card {
  display: grid;
  justify-items: start;
  gap: 0.8rem;
  padding: clamp(1.5rem, 4vw, 3rem);
  border-radius: 22px;
  background: #fff;
  border: 1px solid rgba(106, 27, 44, 0.1);
}

@media (max-width: 800px) {
  .product-details {
    grid-template-columns: 1fr;
  }

  .image-panel :deep(.product-media) {
    max-height: 560px;
  }
}

@media (max-width: 520px) {
  .details-page {
    width: calc(100% - 0.25rem);
  }

  .product-details {
    padding: 0.85rem;
    border-radius: 20px;
  }

  .option-grid,
  .admin-note {
    grid-template-columns: 1fr;
  }

  .purchase-actions {
    display: grid;
  }

  .primary-action,
  .secondary-action,
  .primary-link {
    width: 100%;
  }
}
</style>
