<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProdutoForm from '@/components/produtos/ProdutoForm.vue'
import ProdutoList from '@/components/produtos/ProdutoList.vue'
import { clearUser, getUser } from '@/services/auth'
import { useProductStore } from '@/services/produtoStore'

const router = useRouter()
const user = ref(getUser())
const { activeProducts, createProduct, updateProduct, deleteProduct, loadProducts } = useProductStore()

const loading = ref(false)
const editingProductId = ref(null)
const form = reactive({
  nome: '',
  preco: '',
  destaque: '',
  imagem: '',
  descricao: '',
})

const isEditing = computed(() => editingProductId.value !== null)

function resetForm() {
  form.nome = ''
  form.preco = ''
  form.destaque = ''
  form.imagem = ''
  form.descricao = ''
  editingProductId.value = null
}

function fillForm(product) {
  form.nome = product.nome
  form.preco = String(product.preco).replace('.', ',')
  form.destaque = product.destaque
  form.imagem = product.imagem || ''
  form.descricao = product.descricao || ''
  editingProductId.value = product.id
}

function validateForm() {
  return Boolean(form.nome.trim() && form.preco.toString().trim())
}

async function submitProduct() {
  if (!validateForm()) {
    return
  }

  loading.value = true

  try {
    const payload = {
      nome: form.nome,
      preco: form.preco,
      destaque: form.destaque,
      imagem: form.imagem,
      descricao: form.descricao,
    }

    if (isEditing.value) {
      await updateProduct(editingProductId.value, payload)
    } else {
      await createProduct(payload)
    }

    resetForm()
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

  await loadProducts({ adminMode: true })
})
</script>

<template>
  <section class="products-page">
    <div class="hero">
      <div>
        <p class="eyebrow">Admin</p>
        <h1>Painel de produtos</h1>
        <p class="lead">Cadastre, edite e exclua produtos. Tudo que mudar aqui aparece na home do cliente.
        </p>
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

        <ProdutoForm
          v-model="form"
          :loading="loading"
          :submit-label="isEditing ? 'Salvar alterações' : 'Cadastrar produto'"
          @submit="submitProduct"
          @cancel="resetForm"
        />
      </section>

      <aside class="panel summary-panel">
        <p class="panel-kicker">Resumo</p>
        <h2>Produtos cadastrados</h2>
        <p class="summary-text">
          O cliente já enxerga automaticamente os produtos ativos na home.
        </p>

        <div class="summary-box">
          <strong>{{ activeProducts.length }}</strong>
          <span>produtos ativos</span>
        </div>
      </aside>

      <section class="panel full-span">
        <div class="panel-heading">
          <div>
            <p class="panel-kicker">Listagem</p>
            <h2>Gerenciar produtos existentes</h2>
          </div>
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
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
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
.summary-box strong {
  color: #5c1a2a;
  font-weight: 700;
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

.summary-panel {
  display: grid;
  gap: 0.8rem;
  align-content: start;
}

.summary-text {
  color: #62525b;
  line-height: 1.6;
}

.summary-box {
  border-radius: 18px;
  padding: 1rem;
  background: #f9f3f0;
  border: 1px solid rgba(106, 27, 44, 0.08);
  display: grid;
  gap: 0.2rem;
}

.summary-box strong {
  font-size: 2rem;
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
