<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import ProdutoImagem from '@/components/produtos/ProdutoImagem.vue'
import RetiradaInfo from '@/components/RetiradaInfo.vue'
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
const modalidade = ref('')
const endereco = ref({ destinatario: '', cep: '', rua: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '' })
const ufs = ['AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO']
const camposEndereco = [
  { nome: 'destinatario', label: 'Destinatário', max: 120, autocomplete: 'shipping name' },
  { nome: 'cep', label: 'CEP', max: 9, autocomplete: 'shipping postal-code' },
  { nome: 'rua', label: 'Rua', max: 160, autocomplete: 'shipping address-line1' },
  { nome: 'numero', label: 'Número (ou S/N)', max: 20, autocomplete: 'off' },
  { nome: 'complemento', label: 'Complemento (opcional)', max: 120, autocomplete: 'shipping address-line2' },
  { nome: 'bairro', label: 'Bairro', max: 100, autocomplete: 'shipping address-line3' },
  { nome: 'cidade', label: 'Cidade', max: 100, autocomplete: 'shipping address-level2' },
]

async function checkout() {
  if (checkingOut.value || loading.value || busyItemId.value !== null || clearing.value) return
  feedback.value = ''
  if (!['ENTREGA', 'RETIRADA'].includes(modalidade.value)) {
    feedback.value = 'Selecione entrega ou retirada.'
    return
  }
  const destino = Object.fromEntries(Object.entries(endereco.value).map(([campo, valor]) => [campo, valor.trim()]))
  if (modalidade.value === 'ENTREGA') {
    const invalido = camposEndereco.find(campo =>
      (campo.nome !== 'complemento' && !destino[campo.nome]) || destino[campo.nome].length > campo.max)
    if (invalido) {
      feedback.value = `Preencha corretamente: ${invalido.label}.`
      return
    }
    if (!/^[0-9]{5}-?[0-9]{3}$/.test(destino.cep) || destino.cep.replace('-', '') === '00000000' || !ufs.includes(destino.uf)) {
      feedback.value = 'Confira o CEP (8 dígitos) e a UF.'
      return
    }
  }
  checkingOut.value = true
  feedback.value = ''
  try {
    const response = await criarCheckout({ modalidade: modalidade.value, endereco: modalidade.value === 'ENTREGA' ? destino : null })
    window.location.assign(response.checkoutUrl)
  } catch (checkoutError) {
    feedback.value = checkoutError.message || 'Não foi possível abrir o Mercado Pago.'
    checkingOut.value = false
  }
}

function restoreCheckout(event) {
  if (event.persisted) checkingOut.value = false
}

onMounted(() => window.addEventListener('pageshow', restoreCheckout))
onBeforeUnmount(() => window.removeEventListener('pageshow', restoreCheckout))

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
        PAGO_EM_REVISAO: ['Pagamento recebido, mas o estoque precisa de revisão. A loja verificará a disponibilidade ou o reembolso. Não pague novamente.', 'pending'],
        PENDENTE: ['Pagamento pendente. Avisaremos quando houver confirmação.', 'pending'],
        AGUARDANDO_PAGAMENTO: ['Estamos aguardando a confirmação do Mercado Pago.', 'pending'],
        RECUSADO: ['O pagamento foi recusado. Você pode tentar novamente.', 'error'],
        CANCELADO: ['O pagamento foi cancelado.', 'error'],
        EXPIRADO: ['O pagamento expirou. Consulte Meus pedidos.', 'error'],
        REEMBOLSADO: ['O pagamento foi reembolsado.', 'pending'],
        REEMBOLSADO_PARCIAL: ['O pagamento foi parcialmente reembolsado.', 'pending'],
        CHARGEBACK: ['O pagamento sofreu chargeback.', 'error'],
        EM_MEDIACAO: ['O pagamento está em mediação. Não pague novamente.', 'pending'],
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
            <dt>Recebimento</dt>
            <dd>{{ modalidade === 'RETIRADA' ? 'Retirada na loja' : modalidade === 'ENTREGA' ? 'Entrega' : 'Selecione abaixo' }}</dd>
          </div>
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
            <dd>{{ modalidade === 'RETIRADA' ? 'Retirada' : 'Não incluído nesta etapa' }}</dd>
          </div>
        </dl>

        <div class="summary-total">
          <span>Total parcial</span>
          <strong>{{ formatCurrency(cart.subtotal) }}</strong>
        </div>

        <form id="recebimento" class="delivery-form" @submit.prevent="checkout">
          <fieldset :disabled="checkingOut">
            <legend>Como deseja receber?</legend>
            <label for="modalidade">Modalidade</label>
            <select id="modalidade" v-model="modalidade" required>
              <option disabled value="">Selecione</option>
              <option value="ENTREGA">Entrega</option>
              <option value="RETIRADA">Retirada na loja</option>
            </select>
            <template v-if="modalidade === 'ENTREGA'">
              <p>Informe o endereço desta compra. O frete ainda não é calculado nem incluído no pagamento.</p>
              <div v-for="campo in camposEndereco" :key="campo.nome" class="delivery-field">
                <label :for="`entrega-${campo.nome}`">{{ campo.label }}</label>
                <input :id="`entrega-${campo.nome}`" v-model="endereco[campo.nome]" :name="campo.nome"
                  :required="campo.nome !== 'complemento'" :maxlength="campo.max" :autocomplete="campo.autocomplete"
                  :pattern="campo.nome === 'cep' ? '[0-9]{5}-?[0-9]{3}' : undefined"
                  :inputmode="campo.nome === 'cep' ? 'numeric' : 'text'" />
              </div>
              <label for="entrega-uf">UF</label>
              <select id="entrega-uf" v-model="endereco.uf" required autocomplete="shipping address-level1">
                <option disabled value="">Selecione a UF</option>
                <option v-for="uf in ufs" :key="uf" :value="uf">{{ uf }}</option>
              </select>
            </template>
          </fieldset>
        </form>
        <RetiradaInfo v-if="modalidade === 'RETIRADA'" />
        <section v-else-if="modalidade === 'ENTREGA'" class="destination-summary" aria-label="Resumo do endereço de entrega">
          <strong>Entrega no endereço informado</strong>
          <p>{{ endereco.destinatario.trim() || 'Informe o destinatário' }}</p>
          <p>{{ endereco.rua.trim() || 'Rua' }}, {{ endereco.numero.trim() || 'número' }}</p>
          <p v-if="endereco.complemento.trim()">{{ endereco.complemento.trim() }}</p>
          <p>{{ endereco.bairro.trim() || 'Bairro' }} · {{ endereco.cidade.trim() || 'Cidade' }}/{{ endereco.uf || 'UF' }}</p>
          <p>CEP {{ endereco.cep.trim() || 'não informado' }}</p>
          <small>Confira os dados antes de pagar. Este endereço ficará registrado no pedido.</small>
        </section>
        <p v-else class="delivery-hint">Escolha uma modalidade para continuar para o pagamento.</p>
        <p v-if="feedback" class="feedback" role="alert">{{ feedback }}</p>
        <button form="recebimento" class="checkout-button" type="submit" :disabled="!['ENTREGA', 'RETIRADA'].includes(modalidade) || checkingOut || loading || busyItemId !== null || clearing" :aria-busy="checkingOut">
          {{ checkingOut ? 'Abrindo Mercado Pago...' : 'Finalizar com Mercado Pago' }}
        </button>
        <small>As peças são reservadas por tempo limitado ao abrir o pagamento. Consulte o prazo em Meus pedidos.</small>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.destination-summary { padding: 1rem; border: 1px solid #e3d5d8; border-radius: 12px; color: #5b1a26; background: #faf5f3; line-height: 1.5; overflow-wrap: anywhere; }
.destination-summary p { margin: 0.3rem 0; }
.destination-summary small, .delivery-hint { color: #786b6f; line-height: 1.5; }
.delivery-form fieldset { display: grid; gap: 0.65rem; min-width: 0; margin: 0; padding: 0; border: 0; }
.delivery-form legend { margin-bottom: 0.8rem; color: #5b1a26; font-weight: 700; }
.delivery-form label { color: #66565a; font-size: 0.88rem; }
.delivery-form p { color: #786b6f; font-size: 0.85rem; line-height: 1.5; }
.delivery-field { display: grid; gap: 0.3rem; }
.delivery-form input, .delivery-form select { width: 100%; box-sizing: border-box; min-width: 0; min-height: 42px; padding: 0.6rem; border: 1px solid #cfbfc3; border-radius: 8px; background: white; color: #5b1a26; font: inherit; }
.delivery-form input:focus-visible, .delivery-form select:focus-visible { outline: 2px solid #7d2032; outline-offset: 2px; }
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
