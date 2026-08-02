import { computed, readonly, ref } from 'vue'
import {
  adicionarItemCarrinho,
  atualizarItemCarrinho,
  buscarCarrinho,
  limparCarrinho,
  removerItemCarrinho,
} from '@/services/clienteApi'

const emptyCart = () => ({
  id: null,
  itens: [],
  quantidadeTotal: 0,
  subtotal: 0,
})

const cart = ref(emptyCart())
const loading = ref(false)
const error = ref('')
const loaded = ref(false)

const itemCount = computed(() => Number(cart.value.quantidadeTotal || 0))
const isEmpty = computed(() => !cart.value.itens?.length)

function applyCart(payload) {
  cart.value = {
    ...emptyCart(),
    ...(payload || {}),
    itens: Array.isArray(payload?.itens) ? payload.itens : [],
    quantidadeTotal: Number(payload?.quantidadeTotal || 0),
    subtotal: Number(payload?.subtotal || 0),
  }
  loaded.value = true
  error.value = ''
  return cart.value
}

async function loadCart({ force = false } = {}) {
  if (loaded.value && !force) return cart.value

  loading.value = true
  error.value = ''
  try {
    return applyCart(await buscarCarrinho())
  } catch (requestError) {
    error.value = requestError.message
    throw requestError
  } finally {
    loading.value = false
  }
}

async function addItem(produtoTamanhoId, quantidade) {
  error.value = ''
  try {
    return applyCart(await adicionarItemCarrinho(produtoTamanhoId, quantidade))
  } catch (requestError) {
    error.value = requestError.message
    throw requestError
  }
}

async function updateItem(itemId, quantidade) {
  error.value = ''
  try {
    return applyCart(await atualizarItemCarrinho(itemId, quantidade))
  } catch (requestError) {
    error.value = requestError.message
    throw requestError
  }
}

async function removeItem(itemId) {
  error.value = ''
  try {
    return applyCart(await removerItemCarrinho(itemId))
  } catch (requestError) {
    error.value = requestError.message
    throw requestError
  }
}

async function clearCart() {
  error.value = ''
  try {
    return applyCart(await limparCarrinho())
  } catch (requestError) {
    error.value = requestError.message
    throw requestError
  }
}

function resetCart() {
  cart.value = emptyCart()
  loaded.value = false
  loading.value = false
  error.value = ''
}

export function useCartStore() {
  return {
    cart: readonly(cart),
    loading: readonly(loading),
    error: readonly(error),
    loaded: readonly(loaded),
    itemCount,
    isEmpty,
    loadCart,
    addItem,
    updateItem,
    removeItem,
    clearCart,
    resetCart,
  }
}
