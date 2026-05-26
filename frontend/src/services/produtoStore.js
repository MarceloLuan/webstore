import { computed, ref } from 'vue'

const STORAGE_KEY = 'webstore-products'

const seedProducts = [
  { id: 1, nome: 'Vestido Floral', preco: 129.9, destaque: 'Mais vendido', ativo: true },
  { id: 2, nome: 'Blusa de Seda', preco: 89.9, destaque: 'Novo', ativo: true },
  { id: 3, nome: 'Saia Midi', preco: 109.9, destaque: 'Favorito', ativo: true },
]

function normalizePrice(value) {
  const numeric = Number.parseFloat(String(value).replace(',', '.'))
  return Number.isFinite(numeric) ? numeric : 0
}

function loadProducts() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return seedProducts
    }

    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) && parsed.length ? parsed : seedProducts
  } catch (error) {
    console.warn('Could not load products from localStorage', error)
    return seedProducts
  }
}

function persistProducts(products) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(products))
  } catch (error) {
    console.warn('Could not persist products to localStorage', error)
  }
}

const products = ref(loadProducts())

const activeProducts = computed(() => products.value.filter((product) => product.ativo))

const nextId = computed(() => {
  return products.value.reduce((highest, product) => Math.max(highest, product.id || 0), 0) + 1
})

function listProducts() {
  return products.value.filter((product) => product.ativo)
}

function createProduct(product) {
  const created = {
    id: nextId.value,
    nome: product.nome.trim(),
    preco: normalizePrice(product.preco),
    destaque: product.destaque.trim() || 'Destaque',
    ativo: true,
    imagem: product.imagem?.trim() || '',
    descricao: product.descricao?.trim() || '',
  }

  products.value = [created, ...products.value]
  persistProducts(products.value)
  return created
}

function updateProduct(id, product) {
  const updated = products.value.map((current) => {
    if (current.id !== id) {
      return current
    }

    return {
      ...current,
      nome: product.nome.trim(),
      preco: normalizePrice(product.preco),
      destaque: product.destaque.trim() || 'Destaque',
      imagem: product.imagem?.trim() || '',
      descricao: product.descricao?.trim() || '',
    }
  })

  products.value = updated
  persistProducts(products.value)

  return products.value.find((product) => product.id === id) || null
}

function deleteProduct(id) {
  products.value = products.value.filter((product) => product.id !== id)
  persistProducts(products.value)
}

function findProductById(id) {
  return products.value.find((product) => product.id === id) || null
}

export function useProductStore() {
  return {
    products,
    activeProducts,
    listProducts,
    createProduct,
    updateProduct,
    deleteProduct,
    findProductById,
  }
}
