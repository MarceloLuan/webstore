<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import ProdutoImagem from '@/components/produtos/ProdutoImagem.vue'
import { clearUser, getUser } from '@/services/auth'
import { useProductStore } from '@/services/produtoStore'

const router = useRouter()
const route = useRoute()
const user = ref(getUser())
const { activeProducts, loadProducts } = useProductStore()

const searchTerm = computed(() => (typeof route.query.busca === 'string' ? route.query.busca.trim() : ''))
const selectedCategory = computed(() => (
  typeof route.query.categoria === 'string' ? route.query.categoria.trim() : ''
))

function normalizeSearchText(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('pt-BR')
    .trim()
}

const filteredProducts = computed(() => {
  const search = normalizeSearchText(searchTerm.value)
  const category = normalizeSearchText(selectedCategory.value)

  return activeProducts.value.filter((product) => {
    const matchesSearch = !search || normalizeSearchText(product.nome).includes(search)
    const matchesCategory = !category || normalizeSearchText(product.categoria) === category
    return matchesSearch && matchesCategory
  })
})

const hasActiveFilter = computed(() => Boolean(searchTerm.value || selectedCategory.value))
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

const authenticated = computed(() => Boolean(user.value))
const role = computed(() => user.value?.role || 'VISITANTE')
const roleLabel = computed(() => {
  if (role.value === 'ADMIN') return 'Administrador'
  if (authenticated.value) return 'Cliente'
  return 'Visitante'
})
const firstName = computed(() => user.value?.nome?.split(' ')[0] || 'visitante')

function logout() {
  clearUser()
  user.value = null
  router.push('/home')
}

onMounted(async () => {
  await loadProducts()
})
</script>

<template>
  <section class="home-page">
    <section v-if="!hasActiveFilter" class="hero-banner">
      <div class="hero-copy">
        <p class="eyebrow">Novidades da semana</p>
        <h1>{{ authenticated ? `Olá, ${firstName}.` : 'Versatilidade e liberdade para o seu estilo' }}</h1>
        <p v-if="!authenticated" class="lead">
          Veja os produtos disponíveis, escolha seu look favorito e entre quando quiser.
        </p>

        <div class="hero-actions">
          <RouterLink v-if="!authenticated" class="hero-button hero-button-solid" to="/login">Entrar</RouterLink>
          <RouterLink v-if="!authenticated" class="hero-button hero-button-ghost" to="/cadastro">Criar conta</RouterLink>
          <RouterLink v-if="authenticated" class="hero-button hero-button-solid" to="/minha-conta">Minha conta</RouterLink>
          <button v-if="authenticated" class="hero-button hero-button-ghost" type="button" @click="logout">Sair</button>
        </div>
      </div>

      <div class="hero-accent">
        <span>Frete e troca</span>
        <strong>Compra segura</strong>
        <small>Atendimento rápido</small>
      </div>
    </section>

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

      <div v-if="filteredProducts.length" class="product-grid">
        <article v-for="product in filteredProducts" :key="product.id" class="product-card">
          <ProdutoImagem :src="product.imagem" :alt="product.nome" ratio="3 / 4" />
          <small>{{ product.destaque }}</small>
          <strong>{{ product.nome }}</strong>
          <span>R$ {{ Number(product.preco).toFixed(2).replace('.', ',') }}</span>
        </article>
      </div>

      <div v-else class="empty-search" role="status">
        <strong>Nenhum produto encontrado.</strong>
        <p>Tente outra pesquisa ou escolha uma categoria diferente.</p>
      </div>
    </section>

    <section class="info-strip">
      <article class="info-card">
        <span>Loja física</span>
        <strong>Rua Professor João Cândido, 156.</strong>
      </article>
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

.eyebrow,
.panel-kicker {
  margin: 0 0 0.35rem;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 0.74rem;
  color: #8c6a4d;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  color: #5b1a26;
  font-size: clamp(2rem, 3.9vw, 4rem);
  line-height: 1.05;
  max-width: 14ch;
  font-family: 'Iowan Old Style', 'Palatino Linotype', Georgia, serif;
}

.lead {
  margin-top: 0.9rem;
  max-width: 42ch;
  color: #65565a;
  font-size: 1rem;
  line-height: 1.7;
  text-wrap: balance;
}

.hero-banner {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(220px, 0.65fr);
  gap: 1rem;
  align-items: stretch;
  min-height: 340px;
  padding: clamp(1.2rem, 2vw, 2rem);
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(201, 170, 115, 0.26), transparent 34%),
    linear-gradient(135deg, #6b1f2a 0%, #7f2b39 54%, #efe2d6 100%);
  border: 1px solid rgba(106, 27, 44, 0.1);
  box-shadow: 0 20px 50px rgba(106, 27, 44, 0.12);
}

.hero-copy {
  color: #fff8f1;
  display: grid;
  align-content: center;
  gap: 0.35rem;
  padding-right: min(4vw, 1.6rem);
}

.hero-copy .eyebrow {
  color: rgba(238, 205, 127, 0.88);
}

.hero-copy h1,
.hero-copy .lead {
  color: #e2c06a;
}

.hero-actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-top: 0.5rem;
}

.hero-button {
  border-radius: 999px;
  padding: 0.82rem 1.15rem;
  text-decoration: none;
  font-weight: 700;
  letter-spacing: 0.03em;
  border: 1px solid transparent;
}

.hero-button-solid {
  background: #fff;
  color: #5b1a26;
}

.hero-button-ghost {
  background: transparent;
  color: #fff8f1;
  border-color: rgba(255, 248, 241, 0.45);
}

.hero-button-ghost:hover {
  text-transform: uppercase;
}

.hero-accent {
  border-radius: 22px;
  background: rgba(255, 249, 243, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.4);
  display: grid;
  align-content: center;
  gap: 0.4rem;
  padding: 1.15rem;
  color: #5b1a26;
}

.hero-accent span,
.hero-accent small {
  color: #8c6a4d;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 0.74rem;
}

.hero-accent strong {
  font-family: 'Iowan Old Style', 'Palatino Linotype', Georgia, serif;
  font-size: clamp(1.6rem, 2.7vw, 2.4rem);
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
}

.product-card :deep(.product-media) {
  margin-bottom: 0.45rem;
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

.info-strip {
  display: grid;
  gap: 0.9rem;
  justify-items: center;
}

.info-card {
  width: 100%;
  border-radius: 22px;
  padding: 1.2rem 1.4rem;
  background:
    radial-gradient(circle at top right, rgba(201, 170, 115, 0.14), transparent 28%),
    linear-gradient(135deg, #6b1f2a 0%, #7f2b39 100%);
  border: 1px solid rgba(201, 170, 115, 0.22);
  box-shadow: 0 16px 34px rgba(106, 27, 44, 0.12);
  display: grid;
  gap: 0.25rem;
  justify-items: center;
  text-align: center;
}

.info-card span {
  color: rgba(238, 205, 127, 0.9);
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-size: 0.72rem;
}

.info-card strong {
  color: #e2c06a;
  font-size: 1.08rem;
}

@media (max-width: 960px) {
  .hero-banner,
  .info-strip,
  .product-grid {
    grid-template-columns: 1fr;
  }

  .hero-copy {
    padding-right: 0;
  }

  .section-heading {
    align-items: start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .hero-banner {
    padding: 1rem;
    border-radius: 22px;
    min-height: auto;
  }

  .hero-copy h1,
  .hero-copy .lead {
    max-width: 100%;
  }

  .home-page {
    width: calc(100% - 0.25rem);
  }

  .product-grid {
    gap: 0.75rem;
  }
}
</style>
