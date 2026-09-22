<script setup>
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import ProdutoImagem from '@/components/produtos/ProdutoImagem.vue'
import { useCartStore } from '@/stores/cartStore'
import { formatCurrency } from '@/utils/currency'
import { buscarPedido, criarCheckout } from '@/services/clienteApi'

const route = useRoute()

const {
  cart,
  loading,
  error,
  loadCart,
  updateItem,
  removeItem,
  clearCart,
} = useCartStore()

const quantities = ref({})
const busyItemId = ref(null)
const clearing = ref(false)
const feedback = ref('')
const checkingOut = ref(false)
const paymentFeedback = ref('')
const paymentFeedbackType = ref('')

async function checkout() {
  checkingOut.value = true
  feedback.value = ''
  try {
    const response = await criarCheckout()
    window.location.assign(response.checkoutUrl)
  } catch (checkoutError) {
    feedback.value = checkoutError.message || 'Não foi possível abrir o Mercado Pago.'
  } finally {
    checkingOut.value = false
  }
}

watch(
  () => cart.value.itens,
  (items) => {
    quantities.value = Object.fromEntries(items.map((item) => [item.id, item.quantidade]))
  },
  { immediate: true, deep: true },
)

function normalizedQuantity(item) {
  const value = Math.trunc(Number(quantities.value[item.id]))
  if (!Number.isFinite(value)) return item.quantidade
  return Math.min(Math.max(value, 1), item.estoqueDisponivel)
}

async function commitQuantity(item, requestedQuantity = normalizedQuantity(item)) {
  if (requestedQuantity === item.quantidade) {
    quantities.value[item.id] = item.quantidade
    return
  }

  busyItemId.value = item.id
  feedback.value = ''
  try {
    await updateItem(item.id, requestedQuantity)
    feedback.value = 'Quantidade atualizada.'
  } catch {
    quantities.value[item.id] = item.quantidade
  } finally {
    busyItemId.value = null
  }
}

function changeQuantity(item, difference) {
  const next = Math.min(
    Math.max(Number(quantities.value[item.id] || item.quantidade) + difference, 1),
    item.estoqueDisponivel,
  )
  quantities.value[item.id] = next
  commitQuantity(item, next)
}

async function remove(item) {
  busyItemId.value = item.id
  feedback.value = ''
  try {
    await removeItem(item.id)
    feedback.value = `${item.nome} foi removido do carrinho.`
  } finally {
    busyItemId.value = null
  }
}

async function clear() {
  if (!window.confirm('Remover todos os produtos do carrinho?')) return

  clearing.value = true
  feedback.value = ''
  try {
    await clearCart()
    feedback.value = 'Carrinho esvaziado.'
  } finally {
    clearing.value = false
  }
}

onMounted(async () => {
  try {
    await loadCart({ force: true })
  } catch {
    // A mensagem da store é exibida na própria tela.
  }

  const pedidoId = Number(route.query.pedido)
  if (Number.isInteger(pedidoId) && pedidoId > 0) {
    try {
      const pedido = await buscarPedido(pedidoId)
      const messages = {
        PAGO: ['Pagamento confirmado! Seu pedido foi aprovado.', 'success'],
        PENDENTE: ['Pagamento pendente. Avisaremos quando houver confirmação.', 'pending'],
        AGUARDANDO_PAGAMENTO: ['Estamos aguardando a confirmação do Mercado Pago.', 'pending'],
        RECUSADO: ['O pagamento foi recusado. Você pode tentar novamente.', 'error'],
        CANCELADO: ['O pagamento foi cancelado.', 'error'],
        ERRO: ['Não foi possível concluir este pagamento.', 'error'],
      }
      const [message, type] = messages[pedido.status] || messages.ERRO
      paymentFeedback.value = message
      paymentFeedbackType.value = type
    } catch (pedidoError) {
      paymentFeedback.value = pedidoError.message || 'Não foi possível consultar o pedido.'
      paymentFeedbackType.value = 'error'
    }
  }
})
</script>

<template>
  <section class="cart-page">
    <div
      v-if="paymentFeedback"
      class="payment-feedback"
      :class="`payment-feedback--${paymentFeedbackType}`"
      role="status"
    >
      {{ paymentFeedback }}
      <RouterLink to="/meus-pedidos">Ver pedido e opções de pagamento</RouterLink>
    </div>
    <header class="cart-hero">
      <div>
        <p class="eyebrow">Sua seleção</p>
        <h1>Meu carrinho</h1>
        <p>Revise tamanhos e quantidades antes de continuar.</p>
      </div>
      <RouterLink class="secondary-link" to="/home">Continuar comprando</RouterLink>
    </header>

    <div v-if="loading && !cart.itens.length" class="state-card" role="status">
      Carregando seu carrinho...
    </div>

    <div v-else-if="error && !cart.itens.length" class="state-card error-state" role="alert">
      <strong>Não foi possível carregar o carrinho.</strong>
      <p>{{ error }}</p>
      <button type="button" @click="loadCart({ force: true })">Tentar novamente</button>
    </div>

    <div v-else-if="!cart.itens.length" class="state-card empty-state">
      <span class="empty-icon" aria-hidden="true">◇</span>
      <h2>Seu carrinho está vazio</h2>
      <p>Explore os produtos e escolha algo especial para você.</p>
      <RouterLink class="primary-link" to="/home">Explorar produtos</RouterLink>
    </div>

    <div v-else class="cart-layout">
      <section class="items-panel" aria-label="Produtos no carrinho">
        <div class="items-heading">
          <div>
            <p class="panel-kicker">Produtos escolhidos</p>
            <h2>{{ cart.quantidadeTotal }} {{ cart.quantidadeTotal === 1 ? 'item' : 'itens' }}</h2>
          </div>
          <button type="button" class="clear-button" :disabled="clearing" @click="clear">
            {{ clearing ? 'Limpando...' : 'Limpar carrinho' }}
          </button>
        </div>

        <article v-for="item in cart.itens" :key="item.id" class="cart-item">
          <RouterLink :to="{ name: 'produto-detalhes', params: { id: item.produtoId } }" class="item-image">
            <ProdutoImagem :src="item.imagem" :alt="item.nome" ratio="1 / 1" />
          </RouterLink>

          <div class="item-info">
            <span class="item-code">{{ item.codigo }}</span>
            <RouterLink :to="{ name: 'produto-detalhes', params: { id: item.produtoId } }">
              {{ item.nome }}
            </RouterLink>
            <span class="size-pill">Tamanho {{ item.tamanho }}</span>
            <small v-if="item.quantidade > item.estoqueDisponivel" class="stock-warning">
              Quantidade superior ao estoque atual.
            </small>
            <small v-else>{{ item.estoqueDisponivel }} unidade(s) disponíveis</small>
          </div>

          <div class="quantity-column">
            <span>Quantidade</span>
            <div class="quantity-control">
              <button
                type="button"
                aria-label="Diminuir quantidade"
                :disabled="busyItemId === item.id || quantities[item.id] <= 1"
                @click="changeQuantity(item, -1)"
              >
                −
              </button>
              <input
                v-model.number="quantities[item.id]"
                type="number"
                min="1"
                :max="item.estoqueDisponivel"
                :disabled="busyItemId === item.id"
                :aria-label="`Quantidade de ${item.nome}`"
                @change="commitQuantity(item)"
              />
              <button
                type="button"
                aria-label="Aumentar quantidade"
                :disabled="busyItemId === item.id || quantities[item.id] >= item.estoqueDisponivel"
                @click="changeQuantity(item, 1)"
              >
                +
              </button>
            </div>
          </div>

          <div class="item-price">
            <small>{{ formatCurrency(item.precoUnitario) }} cada</small>
            <strong>{{ formatCurrency(item.subtotal) }}</strong>
            <button type="button" :disabled="busyItemId === item.id" @click="remove(item)">
              {{ busyItemId === item.id ? 'Aguarde...' : 'Remover' }}
            </button>
          </div>
        </article>

        <p v-if="feedback" class="feedback" role="status">{{ feedback }}</p>
        <p v-if="error" class="feedback error-message" role="alert">{{ error }}</p>
      </section>

      <aside class="summary-card">
        <p class="panel-kicker">Resumo</p>
        <h2>Resumo do pedido</h2>

        <dl>
          <div>
            <dt>Produtos</dt>
            <dd>{{ cart.quantidadeTotal }}</dd>
          </div>
          <div>
            <dt>Subtotal</dt>
            <dd>{{ formatCurrency(cart.subtotal) }}</dd>
          </div>
          <div>
            <dt>Frete</dt>
            <dd>Calculado depois</dd>
          </div>
        </dl>

        <div class="summary-total">
          <span>Total parcial</span>
          <strong>{{ formatCurrency(cart.subtotal) }}</strong>
        </div>

        <button class="checkout-button" type="button" :disabled="checkingOut" @click="checkout">
          {{ checkingOut ? 'Abrindo Mercado Pago...' : 'Finalizar com Mercado Pago' }}
        </button>
        <small>O estoque será confirmado novamente ao finalizar o pedido.</small>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.cart-page {
  width: min(1180px, calc(100% - 0.5rem));
  display: grid;
  gap: 1rem;
}

.payment-feedback {
  padding: 1rem 1.2rem;
  border: 1px solid transparent;
  border-radius: 16px;
  font-weight: 700;
}

.payment-feedback--success {
  border-color: #8fc5a1;
  background: #edf8f0;
  color: #24613a;
}

.payment-feedback--pending {
  border-color: #dfc580;
  background: #fff8e6;
  color: #755713;
}

.payment-feedback--error {
  border-color: #dda0a0;
  background: #fff0f0;
  color: #8a1d1d;
}

.cart-hero,
.items-panel,
.summary-card,
.state-card {
  border: 1px solid rgba(106, 27, 44, 0.09);
  background: #fff;
  box-shadow: 0 16px 38px rgba(106, 27, 44, 0.07);
}

.cart-hero {
  padding: clamp(1.1rem, 2.5vw, 1.6rem);
  border-radius: 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.eyebrow,
.panel-kicker {
  margin: 0 0 0.3rem;
  color: #8c6a4d;
  text-transform: uppercase;
  letter-spacing: 0.15em;
  font-size: 0.72rem;
}

h1,
h2,
p,
dl {
  margin: 0;
}

h1 {
  color: #5b1a26;
  font: 600 clamp(2rem, 4vw, 3.2rem) / 1.05 'Palatino Linotype', Georgia, serif;
}

h2 {
  color: #5b1a26;
}

.cart-hero p:last-child {
  margin-top: 0.45rem;
  color: #6d5c61;
}

.secondary-link,
.primary-link {
  border-radius: 999px;
  padding: 0.8rem 1rem;
  text-decoration: none;
  font-weight: 700;
  text-align: center;
}

.secondary-link {
  color: #5b1a26;
  background: #f3ebe8;
}

.primary-link {
  color: #fff;
  background: #6a1b2c;
}

.cart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 0.65fr);
  gap: 1rem;
  align-items: start;
}

.items-panel,
.summary-card {
  border-radius: 22px;
  padding: 1.15rem;
}

.items-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding-bottom: 0.9rem;
}

.clear-button {
  border: 0;
  background: transparent;
  color: #8a1d2d;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.cart-item {
  display: grid;
  grid-template-columns: 112px minmax(160px, 1fr) auto minmax(110px, auto);
  align-items: center;
  gap: 1rem;
  padding: 1rem 0;
  border-top: 1px solid rgba(106, 27, 44, 0.08);
}

.item-image {
  display: block;
}

.item-image :deep(.product-media) {
  border-radius: 14px;
}

.item-info {
  display: grid;
  justify-items: start;
  gap: 0.35rem;
}

.item-info a {
  color: #4f1a25;
  font-size: 1.05rem;
  font-weight: 700;
  text-decoration: none;
}

.item-code {
  color: #927b81;
  font-size: 0.7rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.size-pill {
  padding: 0.28rem 0.55rem;
  border-radius: 999px;
  background: #f3ebe8;
  color: #5b1a26;
  font-size: 0.78rem;
  font-weight: 700;
}

.item-info small {
  color: #77686c;
}

.item-info .stock-warning {
  color: #9a3a1d;
}

.quantity-column {
  display: grid;
  justify-items: center;
  gap: 0.4rem;
  color: #66565a;
  font-size: 0.8rem;
}

.quantity-control {
  display: grid;
  grid-template-columns: 34px 48px 34px;
  border: 1px solid rgba(106, 27, 44, 0.14);
  border-radius: 999px;
  overflow: hidden;
}

.quantity-control button,
.quantity-control input {
  height: 36px;
  border: 0;
  background: #fff;
  color: #5b1a26;
  text-align: center;
  font: inherit;
  font-weight: 700;
}

.quantity-control button {
  cursor: pointer;
}

.quantity-control button:disabled {
  color: #c8bec0;
  cursor: default;
}

.quantity-control input {
  width: 48px;
  appearance: textfield;
}

.quantity-control input::-webkit-inner-spin-button {
  appearance: none;
}

.item-price {
  display: grid;
  justify-items: end;
  gap: 0.25rem;
}

.item-price small {
  color: #77686c;
}

.item-price strong {
  color: #5b1a26;
  font-size: 1.05rem;
}

.item-price button {
  margin-top: 0.25rem;
  border: 0;
  background: transparent;
  color: #8a1d2d;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
}

.summary-card {
  position: sticky;
  top: 1rem;
  display: grid;
  gap: 1rem;
}

.summary-card dl {
  display: grid;
  gap: 0.7rem;
}

.summary-card dl div,
.summary-total {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.summary-card dt,
.summary-card dd {
  margin: 0;
  color: #66565a;
}

.summary-total {
  padding-top: 0.9rem;
  border-top: 1px solid rgba(106, 27, 44, 0.1);
  color: #5b1a26;
}

.summary-total strong {
  font-size: 1.2rem;
}

.checkout-button {
  min-height: 50px;
  border: 0;
  border-radius: 999px;
  padding: 0.8rem 1.25rem;
  background: linear-gradient(135deg, #7d2032 0%, #a52f46 100%);
  color: #fff;
  font: inherit;
  font-weight: 700;
  letter-spacing: 0.01em;
  cursor: pointer;
  box-shadow: 0 10px 22px rgba(125, 32, 50, 0.24);
  transition: transform 160ms ease, box-shadow 160ms ease, filter 160ms ease;
}

.checkout-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 14px 28px rgba(125, 32, 50, 0.3);
  filter: brightness(1.06);
}

.checkout-button:focus-visible {
  outline: 3px solid rgba(201, 170, 115, 0.55);
  outline-offset: 3px;
}

.checkout-button:disabled {
  background: #b7aaad;
  cursor: wait;
  box-shadow: none;
  opacity: 0.72;
}

.summary-card > small {
  color: #786b6f;
  line-height: 1.45;
  text-align: center;
}

.state-card {
  min-height: 280px;
  padding: 2rem;
  border-radius: 22px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 0.75rem;
  color: #65565a;
  text-align: center;
}

.empty-icon {
  color: #c9aa73;
  font-size: 3rem;
}

.error-state strong,
.error-message {
  color: #8a1d1d;
}

.error-state button {
  border: 0;
  border-radius: 999px;
  padding: 0.75rem 1rem;
  background: #6a1b2c;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

.feedback {
  padding: 0.7rem 0.8rem;
  border-radius: 12px;
  background: #f5eee9;
  color: #5b1a26;
}

@media (max-width: 900px) {
  .cart-layout {
    grid-template-columns: 1fr;
  }

  .summary-card {
    position: static;
  }
}

@media (max-width: 700px) {
  .cart-hero {
    align-items: stretch;
    flex-direction: column;
  }

  .cart-item {
    grid-template-columns: 88px 1fr;
    align-items: start;
  }

  .quantity-column {
    grid-column: 1;
  }

  .item-price {
    grid-column: 2;
    justify-items: start;
  }
}
</style>
