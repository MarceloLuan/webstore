<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import logo from '@/assets/logo.png'
import { getUser } from '@/services/auth'

const route = useRoute()
const router = useRouter()
const user = ref(getUser())
const searchTerm = ref('')
let authSyncTimer = null

function refreshAuthState() {
  user.value = getUser()
}

const isAdmin = computed(() => user.value?.role === 'ADMIN')
const accountTarget = computed(() => (user.value ? '/minha-conta' : '/login'))
const selectedCategory = computed(() => (typeof route.query.categoria === 'string' ? route.query.categoria : ''))

const categoryLinks = [
  { label: 'Vestidos', value: 'Vestido' },
  { label: 'Blusas', value: 'Blusa' },
  { label: 'Camisas', value: 'Camisa' },
  { label: 'Calças', value: 'Calças' },
  { label: 'Saias', value: 'Saia' },
  { label: 'Conjuntos', value: 'Conjunto' },
  { label: 'Acessórios', value: 'Acessório' },
]

watch(
  () => route.query.busca,
  (value) => {
    searchTerm.value = typeof value === 'string' ? value : ''
  },
  { immediate: true },
)

function searchProducts() {
  const query = searchTerm.value.trim()
  const nextQuery = {}

  if (query) {
    nextQuery.busca = query
  }

  if (selectedCategory.value) {
    nextQuery.categoria = selectedCategory.value
  }

  router.replace({ name: 'home', query: nextQuery })
}

function clearSearch() {
  searchTerm.value = ''
  searchProducts()
}

onMounted(() => {
  refreshAuthState()
  authSyncTimer = setInterval(refreshAuthState, 500)
})

onBeforeUnmount(() => {
  if (authSyncTimer) {
    clearInterval(authSyncTimer)
    authSyncTimer = null
  }
})
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="header-top">
        <RouterLink to="/home" class="brand-block" aria-label="Ir para a home">
          <img :src="logo" alt="Logo da WebStore" class="brand-logo" />
        </RouterLink>

        <form class="header-search" role="search" @submit.prevent="searchProducts">
          <label for="product-search" class="sr-only">Pesquisar produtos pelo nome</label>
          <svg class="search-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="m21 19.6-5.2-5.2a7.5 7.5 0 1 0-1.4 1.4l5.2 5.2L21 19.6ZM5 10a5 5 0 1 1 10 0 5 5 0 0 1-10 0Z" />
          </svg>
          <input
            id="product-search"
            v-model="searchTerm"
            type="search"
            placeholder="Pesquisar produto..."
            autocomplete="off"
            @input="searchProducts"
          />
          <button
            v-if="searchTerm"
            class="clear-search"
            type="button"
            aria-label="Limpar pesquisa"
            @click="clearSearch"
          >
            ×
          </button>
        </form>

        <div class="header-actions">
          <RouterLink class="icon-button" :to="accountTarget" :aria-label="user ? 'Minha conta' : 'Entrar'">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4Zm0 2c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5Z" />
            </svg>
          </RouterLink>

          <button class="icon-button" type="button" aria-label="Carrinho em breve">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M7 4h-2l-1 2v2h2l3.6 7.59-1.35 2.41A2 2 0 0 0 10 21h9v-2h-8.42a.25.25 0 0 1-.21-.38L11 16h6.55a2 2 0 0 0 1.8-1.11L22 8H8.42L7.8 6.5h11.9V4H7Zm2 16a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Zm8 0a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z" />
            </svg>
          </button>
        </div>
      </div>

      <nav class="site-nav" aria-label="Categorias">
        <RouterLink v-if="isAdmin" class="nav-link nav-admin-link" to="/produtos">Gerenciar produtos</RouterLink>
        <RouterLink
          class="nav-link"
          :class="{ 'nav-link-selected': route.name === 'home' && !selectedCategory }"
          :to="{ name: 'home' }"
        >
          Início
        </RouterLink>
        <RouterLink
          v-for="category in categoryLinks"
          :key="category.value"
          class="nav-link"
          :class="{ 'nav-link-selected': selectedCategory === category.value }"
          :to="{ name: 'home', query: { categoria: category.value } }"
        >
          {{ category.label }}
        </RouterLink>
      </nav>
    </header>

    <main class="content-area">
      <RouterView />
    </main>
  </div>
</template>

<style>
:root {
  --bg-beige: #f3e8df;
  --card-white: #ffffff;
  --primary-wine: #6a1b2c;
  --primary-wine-deep: #531724;
  --gold-soft: #c9aa73;
  --input-gray: #f2efed;
  --text-dark: #4b3a3a;
  --border-soft: rgba(106, 27, 44, 0.12);
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  min-height: 100vh;
  font-family: 'Avenir Next', 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  background:
    radial-gradient(circle at top right, #f8f2ec 0%, #f3e8df 58%),
    linear-gradient(180deg, #fffdfb 0%, #f7f1ea 100%);
  color: var(--text-dark);
}

#app {
  min-height: 100vh;
}

.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.site-header {
  width: min(1280px, calc(100% - 1rem));
  margin: 0.65rem auto 0;
  padding: 0.55rem 0.85rem 0.7rem;
  display: grid;
  gap: 0.55rem;
  background: rgba(255, 252, 248, 0.92);
  border: 1px solid var(--border-soft);
  border-radius: 20px;
  box-shadow: 0 12px 34px rgba(106, 27, 44, 0.06);
  backdrop-filter: blur(10px);
}

.header-top {
  display: grid;
  grid-template-columns: auto minmax(220px, 560px) auto;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.brand-block {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
}

.brand-logo {
  max-height: 42px;
  max-width: 180px;
  object-fit: contain;
}

.header-search {
  width: 100%;
  min-width: 0;
  position: relative;
  display: flex;
  align-items: center;
}

.header-search input {
  width: 100%;
  min-width: 0;
  height: 42px;
  padding: 0.7rem 2.55rem;
  border: 1px solid rgba(106, 27, 44, 0.16);
  border-radius: 999px;
  background: #fff;
  color: var(--text-dark);
  font: inherit;
}

.header-search input:focus {
  outline: 3px solid rgba(106, 27, 44, 0.12);
  border-color: var(--primary-wine);
}

.header-search input::placeholder {
  color: #8b7d80;
}

.search-icon {
  position: absolute;
  left: 0.9rem;
  width: 18px;
  height: 18px;
  fill: var(--primary-wine);
  pointer-events: none;
}

.clear-search {
  position: absolute;
  right: 0.55rem;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #765f65;
  font-size: 1.35rem;
  line-height: 1;
  cursor: pointer;
}

.clear-search:hover {
  background: #f3ebe8;
  color: var(--primary-wine);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.icon-button {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  border: 1px solid var(--border-soft);
  background: linear-gradient(180deg, #fff 0%, #f7f0ea 100%);
  color: var(--primary-wine);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  cursor: pointer;
  text-decoration: none;
}

.icon-button svg {
  width: 18px;
  height: 18px;
  fill: currentColor;
}

.site-nav {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: clamp(0.65rem, 2.1vw, 2rem);
  padding: 0.55rem 0 0.05rem;
  border-top: 1px solid rgba(106, 27, 44, 0.06);
}

.nav-link {
  color: #4d3d42;
  text-decoration: none;
  font-size: 0.86rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 0.2rem 0;
  border-bottom: 1px solid transparent;
}

.nav-link:hover,
.nav-admin-link.router-link-active,
.nav-link-selected {
  color: var(--primary-wine);
  border-bottom-color: var(--gold-soft);
}

.content-area {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 1.1rem 0.5rem 2rem;
}

@media (max-width: 700px) {
  .site-header {
    width: calc(100% - 1rem);
    padding: 0.5rem 0.65rem 0.7rem;
  }

  .header-top {
    grid-template-columns: 1fr auto;
    gap: 0.65rem;
  }

  .brand-logo {
    max-width: 108px;
    max-height: 34px;
  }

  .header-search {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .header-search input {
    height: 40px;
  }

  .site-nav {
    gap: 0.9rem;
    overflow-x: auto;
    justify-content: flex-start;
    padding-bottom: 0.2rem;
  }

  .content-area {
    padding: 0.9rem 0.25rem 1.4rem;
    align-items: flex-start;
  }
}
</style>
