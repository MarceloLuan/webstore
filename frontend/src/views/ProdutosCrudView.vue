<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProdutoForm from '@/components/produtos/ProdutoForm.vue'
import ProdutoList from '@/components/produtos/ProdutoList.vue'
import { clearUser, getUser } from '@/services/auth'
import { useProductStore } from '@/services/produtoStore'

const router = useRouter()
const user = ref(getUser())
const { activeProducts, createProduct, updateProduct, deleteProduct, loadProducts, loadProductOptions } = useProductStore()

const loading = ref(false)
const optionsLoading = ref(false)
const editingProductId = ref(null)
const feedbackMessage = ref('')
const feedbackTone = ref('')
const categoryOptions = ref([])
const sizeOptions = ref([])
const form = ref({
  nome: '',
  preco: '',
  imagem: '',
  descricao: '',
  categoria: '',
  tamanhos: [],
})

function createSizeRowId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  return `size-row-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function createEmptySizeRow() {
  return { rowId: createSizeRowId(), tamanho: '', quantidade: 1 }
}

function createInitialForm() {
  return {
    nome: '',
    preco: '',
    imagem: '',
    descricao: '',
    categoria: '',
    tamanhos: [createEmptySizeRow()],
  }
}

function setFeedback(message, tone = 'info') {
  feedbackMessage.value = message
  feedbackTone.value = tone
}

const isEditing = computed(() => editingProductId.value !== null)

function resetForm({ clearFeedback = true } = {}) {
  form.value = createInitialForm()
  editingProductId.value = null
  if (clearFeedback) {
    setFeedback('')
  }
}

function fillForm(product) {
  form.value = {
    nome: product.nome,
    preco: String(product.preco).replace('.', ','),
    imagem: product.imagem || '',
    descricao: product.descricao || '',
    categoria: product.categoria || '',
    tamanhos: Array.isArray(product.tamanhos) && product.tamanhos.length
      ? product.tamanhos.map((item) => ({
          rowId: item.rowId || item.id || createSizeRowId(),
          tamanho: item.tamanho || '',
          quantidade: Number(item.quantidade ?? 1),
        }))
      : [createEmptySizeRow()],
  }
  editingProductId.value = product.id
}

function validateForm() {
  if (!form.value.nome.trim()) return 'Informe o nome do produto.'
  if (!form.value.preco.toString().trim()) return 'Informe o preço do produto.'
  if (!form.value.categoria.trim()) return 'Selecione uma categoria.'
  if (!Array.isArray(form.value.tamanhos) || !form.value.tamanhos.length) return 'Adicione pelo menos um tamanho.'
  if (form.value.tamanhos.some((item) => !item.tamanho?.trim())) return 'Selecione todos os tamanhos adicionados.'
  if (form.value.tamanhos.some((item) => Number(item.quantidade) < 0 || Number.isNaN(Number(item.quantidade)))) {
    return 'Informe uma quantidade válida para cada tamanho.'
  }

  return ''
}

function addSizeRow() {
  form.value.tamanhos.push(createEmptySizeRow())
}

function removeSizeRow(rowId) {
  const nextRows = form.value.tamanhos.filter((item) => item.rowId !== rowId)

  form.value.tamanhos = nextRows.length ? nextRows : [createEmptySizeRow()]
}

async function submitProduct() {
  const validationError = validateForm()
  if (validationError) {
    setFeedback(validationError, 'error')
    return
  }

  loading.value = true
  setFeedback('')

  try {
    const payload = {
      nome: form.value.nome,
      preco: form.value.preco,
      destaque: 'Destaque',
      imagem: form.value.imagem,
      descricao: form.value.descricao,
      categoria: form.value.categoria,
      tamanhos: form.value.tamanhos
        .filter((item) => item.tamanho?.trim())
        .map((item) => ({
          tamanho: item.tamanho,
          quantidade: Number(item.quantidade),
        })),
    }

    if (isEditing.value) {
      await updateProduct(editingProductId.value, payload)
      setFeedback('Produto atualizado com sucesso.', 'success')
    } else {
      await createProduct(payload)
      setFeedback('Produto cadastrado com sucesso.', 'success')
    }

    resetForm({ clearFeedback: false })
  } catch (error) {
    setFeedback(error?.message || 'Não foi possível salvar o produto.', 'error')
  } finally {
    loading.value = false
  }
}

function handleEdit(product) {
  fillForm(product)
}

async function handleDelete(product) {
  const confirmed = window.confirm(`Excluir o produto "${product.nome}"?`)
  if (!confirmed) {
    return
  }

  await deleteProduct(product.id)

  if (editingProductId.value === product.id) {
    resetForm()
  }
}

function logout() {
  clearUser()
  router.push('/login')
}

onMounted(async () => {
  if (!user.value) {
    router.replace('/login')
    return
  }

  if (user.value.role !== 'ADMIN') {
    router.replace('/home')
    return
  }

  optionsLoading.value = true

  try {
    const options = await loadProductOptions()
    categoryOptions.value = options.categorias
    sizeOptions.value = options.tamanhos
  } finally {
    optionsLoading.value = false
  }

  await loadProducts({ adminMode: true })
})
</script>

<template>
  <section class="products-page">
    <div class="hero">
      <div>
        <p class="eyebrow">Admin</p>
        <h1>Painel de produtos</h1>
        <p class="lead">Cadastre, edite e exclua produtos.</p>
      </div>

      <div class="hero-actions">
        <button class="secondary-button" type="button" @click="router.push('/home')">Voltar</button>
        <button class="ghost-button" type="button" @click="logout">Sair</button>
      </div>
    </div>

    <div class="grid">
      <section class="panel">
        <div class="panel-heading">
          <div>
            <p class="panel-kicker">{{ isEditing ? 'Editando' : 'Novo produto' }}</p>
            <h2>{{ isEditing ? 'Atualizar produto' : 'Cadastrar produto' }}</h2>
          </div>
          <span class="counter">{{ activeProducts.length }} ativos</span>
        </div>

        <p v-if="feedbackMessage" class="feedback" :class="feedbackTone">
          {{ feedbackMessage }}
        </p>

        <ProdutoForm
          v-model="form"
          :loading="loading"
          :options-loading="optionsLoading"
          :category-options="categoryOptions"
          :size-options="sizeOptions"
          @add-size="addSizeRow"
          @remove-size="removeSizeRow"
          :submit-label="isEditing ? 'Salvar alterações' : 'Cadastrar produto'"
          @submit="submitProduct"
          @cancel="resetForm"
        />
      </section>

      <section class="panel full-span">
        <div class="panel-heading">
          <div>
            <p class="panel-kicker">Listagem</p>
            <h2>Gerenciar produtos existentes</h2>
          </div>
          <span class="counter counter-compact">{{ activeProducts.length }} cadastrados</span>
        </div>

        <ProdutoList
          :products="activeProducts"
          :admin-mode="true"
          @edit="handleEdit"
          @delete="handleDelete"
        />
      </section>
    </div>
  </section>
</template>

<style scoped>
.products-page {
  width: min(1140px, 100%);
  margin: 0 auto;
  padding: 1rem 0 2rem;
  display: grid;
  gap: 1rem;
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: stretch;
  padding: 1.4rem;
  border-radius: 24px;
  background: linear-gradient(135deg, #fff 0%, #fbf6f3 100%);
  border: 1px solid rgba(106, 27, 44, 0.08);
  box-shadow: 0 18px 40px rgba(106, 27, 44, 0.08);
}

.eyebrow,
.panel-kicker {
  margin: 0 0 0.35rem;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 0.74rem;
  color: #8f5a67;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  color: #5c1a2a;
  font-size: clamp(2rem, 4vw, 3rem);
  line-height: 1.05;
}

.lead {
  margin-top: 0.85rem;
  max-width: 60ch;
  color: #62525b;
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  justify-content: center;
}

.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
}

.panel {
  border-radius: 24px;
  padding: 1.3rem;
  background: #fff;
  border: 1px solid rgba(106, 27, 44, 0.08);
  box-shadow: 0 18px 40px rgba(106, 27, 44, 0.08);
}

.full-span {
  grid-column: 1 / -1;
}

.panel-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}

.counter,
.counter-compact {
  color: #5c1a2a;
  font-weight: 700;
}

.feedback {
  margin: 0 0 0.9rem;
  padding: 0.85rem 1rem;
  border-radius: 14px;
  border: 1px solid transparent;
  line-height: 1.5;
}

.feedback.error {
  background: #fdf0ee;
  color: #7b1d24;
  border-color: rgba(123, 29, 36, 0.16);
}

.feedback.success {
  background: #f3f8ef;
  color: #395c21;
  border-color: rgba(57, 92, 33, 0.16);
}

.feedback.info {
  background: #f8f3ef;
  color: #5b1a26;
  border-color: rgba(91, 26, 38, 0.12);
}

.ghost-button,
.secondary-button {
  border: none;
  border-radius: 999px;
  padding: 0.82rem 1rem;
  font-weight: 700;
  cursor: pointer;
}

.ghost-button {
  background: #fff;
  color: #5c1a2a;
  border: 1px solid rgba(106, 27, 44, 0.18);
}

.secondary-button {
  background: #f3ebe8;
  color: #5c1a2a;
}

.counter-compact {
  font-size: 0.84rem;
  letter-spacing: 0.02em;
}

@media (max-width: 900px) {
  .grid,
  .hero {
    grid-template-columns: 1fr;
    display: grid;
  }

  .hero-actions {
    flex-direction: row;
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .products-page {
    padding-top: 0.5rem;
  }

  .panel,
  .hero {
    padding: 1rem;
    border-radius: 18px;
  }
}
</style>
