import { computed, ref } from 'vue'
import {
  criarProduto,
  excluirProduto,
  listarProdutos,
  listarProdutoOpcoes,
  listarProdutosAdmin,
  atualizarProduto,
} from '@/services/clienteApi'

const STORAGE_KEY = 'webstore-products'

const fallbackCategoryOptions = [
  'Vestido',
  'Blusa',
  'Camisa',
  'Camiseta',
  'Cropped',
  'Calças',
  'Shorts',
  'Saia',
  'Jaqueta',
  'Casaco',
  'Conjunto',
  'Moda Íntima',
  'Macacão',
  'Acessório',
]

const fallbackSizeOptions = ['36', '38', '40', '42', '44', 'P', 'M', 'G', 'GG', 'G1', 'G2', 'Tamanho Único']

const seedProducts = [
  { id: 1, codigo: 'PROD-1', nome: 'Vestido Floral', preco: 129.9, destaque: 'Mais vendido', ativo: true },
  { id: 2, codigo: 'PROD-2', nome: 'Blusa de Seda', preco: 89.9, destaque: 'Novo', ativo: true },
  { id: 3, codigo: 'PROD-3', nome: 'Saia Midi', preco: 109.9, destaque: 'Favorito', ativo: true },
]

function normalizeProduct(product) {
  return {
    ...product,
    id: Number(product.id),
    codigo: product.codigo || `PROD-${product.id}`,
    nome: product.nome ?? '',
    preco: Number(product.preco ?? 0),
    destaque: product.destaque || 'Destaque',
    imagem: product.imagem || '',
    descricao: product.descricao || '',
    ativo: product.ativo !== false,
    categoria: product.categoria || '',
    tamanhos: Array.isArray(product.tamanhos)
      ? product.tamanhos.map((item) => ({
          tamanho: item?.tamanho || '',
          quantidade: Number(item?.quantidade ?? 0),
        }))
      : [],
  }
}

function loadCachedProducts() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return seedProducts
    }

    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) && parsed.length ? parsed.map(normalizeProduct) : seedProducts
  } catch (error) {
    console.warn('Could not load products from localStorage', error)
    return seedProducts
  }
}


function persistProducts(productsToPersist) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(productsToPersist))
  } catch (error) {
    console.warn('Could not persist products to localStorage', error)
  }
}

const products = ref(loadCachedProducts())

const activeProducts = computed(() => products.value.filter((product) => product.ativo))

async function loadProducts({ adminMode = false } = {}) {
  try {
    const items = adminMode ? await listarProdutosAdmin() : await listarProdutos()
    const normalized = Array.isArray(items) ? items.map(normalizeProduct) : []
    products.value = normalized
    persistProducts(products.value)
    return products.value
  } catch (error) {
    console.warn('Could not load products from backend, using cache', error)
    const fallback = loadCachedProducts()
    products.value = fallback
    return products.value
  }
}

async function loadProductOptions() {
  try {
    const options = await listarProdutoOpcoes()

    return {
      categorias: Array.isArray(options?.categorias) && options.categorias.length ? options.categorias : fallbackCategoryOptions,
      tamanhos: Array.isArray(options?.tamanhos) && options.tamanhos.length ? options.tamanhos : fallbackSizeOptions,
    }
  } catch (error) {
    console.warn('Could not load product options from backend, using fallback options', error)
    return {
      categorias: fallbackCategoryOptions,
      tamanhos: fallbackSizeOptions,
    }
  }
}

function listProducts() {
  return [...products.value]
}

async function createProduct(product) {
  const created = normalizeProduct(await criarProduto({
    nome: product.nome.trim(),
    codigo: product.codigo.trim(),
    preco: product.preco,
    destaque: product.destaque?.trim() || 'Destaque',
    imagem: product.imagem?.trim() || '',
    descricao: product.descricao?.trim() || '',
    categoria: product.categoria,
    tamanhos: Array.isArray(product.tamanhos)
      ? product.tamanhos.map((item) => ({
          tamanho: item.tamanho,
          quantidade: Number(item.quantidade),
        }))
      : [],
    ativo: true,
  }))

  products.value = [created, ...products.value]
  persistProducts(products.value)
  return created
}

async function updateProduct(id, product) {
  const updated = normalizeProduct(await atualizarProduto(id, {
    nome: product.nome.trim(),
    codigo: product.codigo.trim(),
    preco: product.preco,
    destaque: product.destaque?.trim() || 'Destaque',
    imagem: product.imagem?.trim() || '',
    descricao: product.descricao?.trim() || '',
    categoria: product.categoria,
    tamanhos: Array.isArray(product.tamanhos)
      ? product.tamanhos.map((item) => ({
          tamanho: item.tamanho,
          quantidade: Number(item.quantidade),
        }))
      : [],
    ativo: product.ativo ?? true,
  }))

  products.value = products.value.map((current) => (current.id === id ? updated : current))
  persistProducts(products.value)

  return updated
}

async function deleteProduct(id) {
  await excluirProduto(id)
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
    loadProducts,
    loadProductOptions,
    listProducts,
    createProduct,
    updateProduct,
    deleteProduct,
    findProductById,
  }
}
