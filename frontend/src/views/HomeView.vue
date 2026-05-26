<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { clearUser, getUser } from '@/services/auth'
import { useProductStore } from '@/services/produtoStore'

const router = useRouter()
const user = ref(getUser())
const { activeProducts, loadProducts } = useProductStore()

const role = computed(() => user.value?.role || 'CLIENTE')
const roleLabel = computed(() => (role.value === 'ADMIN' ? 'Administrador' : 'Cliente'))
const firstName = computed(() => user.value?.nome?.split(' ')[0] || 'usuário')

const clientHighlights = [
  'Acompanhe novidades em vitrine destacada',
  'Gerencie sua conta em poucos cliques',
  'Tenha acesso rápido ao seu histórico',
]

const adminHighlights = [
  'Gerencie sua conta e atualize seus dados',
  'Organize cadastro e edição de produtos',
  'Tenha uma visão rápida do painel de loja',
]

const quickStats = computed(() => {
  if (role.value === 'ADMIN') {
    return [
      { label: 'Ações rápidas', value: '2' },
      { label: 'Produtos em foco', value: `${activeProducts.value.length}` },
      { label: 'Conta', value: 'Ativa' },
    ]
  }

  return [
    { label: 'Produtos em destaque', value: `${activeProducts.value.length}` },
    { label: 'Conta', value: 'Ativa' },
    { label: 'Acesso', value: 'Livre' },
  ]
})

function logout() {
  clearUser()
  router.push('/login')
}

onMounted(async () => {
  if (!user.value) {
    router.replace('/login')
    return
  }

  await loadProducts()
})
</script>

<template>
  <section class="home-page">
    <div class="home-hero">
      <div>
        <p class="eyebrow">WebStore</p>
        <h1>Olá, {{ firstName }}.</h1>
        <p class="lead">
          Sua área inicial foi desenhada para juntar conta, navegação e próximos atalhos numa só tela.
        </p>
      </div>

      <div class="hero-badge">
        <span class="badge-label">Perfil</span>
        <strong>{{ roleLabel }}</strong>
        <small>{{ role }}</small>
      </div>
    </div>

    <div class="stats-grid">
      <article v-for="stat in quickStats" :key="stat.label" class="stat-card">
        <span>{{ stat.label }}</span>
        <strong>{{ stat.value }}</strong>
      </article>
    </div>

    <div class="content-grid">
      <section class="panel panel-main">
        <div class="panel-heading">
          <div>
            <p class="panel-kicker">Próximos passos</p>
            <h2>Seu painel inicial</h2>
          </div>
          <button class="ghost-button" type="button" @click="logout">Sair</button>
        </div>

        <div class="action-grid">
          <RouterLink class="action-card accent" to="/minha-conta">
            <span>Conta</span>
            <h3>Gerenciar conta</h3>
            <p>Atualizar nome, e-mail, telefone e senha. Excluir deixa a conta inativa.</p>
          </RouterLink>

          <RouterLink v-if="role === 'ADMIN'" class="action-card" to="/produtos">
            <span>Admin</span>
            <h3>Gerenciar produtos</h3>
            <p>Acesse o painel para cadastrar, editar e remover produtos.</p>
          </RouterLink>

          <div v-else class="action-card product-wall">
            <span>Vitrine</span>
            <h3>Produtos em destaque</h3>
            <div class="product-list">
              <article v-for="product in activeProducts" :key="product.id" class="product-card">
                <small>{{ product.destaque }}</small>
                <strong>{{ product.nome }}</strong>
                <span>R$ {{ Number(product.preco).toFixed(2).replace('.', ',') }}</span>
              </article>
            </div>
          </div>
        </div>
      </section>

      <aside class="panel panel-side">
        <p class="panel-kicker">Resumo</p>
        <h2>{{ roleLabel }}</h2>

        <ul class="benefits">
          <li v-for="item in role === 'ADMIN' ? adminHighlights : clientHighlights" :key="item">
            {{ item }}
          </li>
        </ul>

        <div class="notice">
          <strong>Conta ativa</strong>
          <p>
            Essa estrutura já deixa pronto o caminho para a tela de conta e, depois, para produtos.
          </p>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.home-page {
  width: min(1140px, 100%);
  margin: 0 auto;
  padding: 1rem 0 2rem;
  display: grid;
  gap: 1rem;
}

.home-hero {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: stretch;
  padding: 1.4rem;
  border-radius: 24px;
  background:
    radial-gradient(circle at top right, rgba(106, 27, 44, 0.16), transparent 35%),
    linear-gradient(135deg, #fff 0%, #fbf6f3 100%);
  border: 1px solid rgba(106, 27, 44, 0.08);
  box-shadow: 0 18px 50px rgba(106, 27, 44, 0.09);
}

.eyebrow,
.panel-kicker,
.badge-label {
  margin: 0 0 0.35rem;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 0.74rem;
  color: #8f5a67;
}

h1,
h2,
h3,
p {
  margin: 0;
}

h1 {
  color: #5c1a2a;
  font-size: clamp(2rem, 4vw, 3.2rem);
  line-height: 1.05;
  max-width: 12ch;
}

.lead {
  margin-top: 0.9rem;
  max-width: 60ch;
  color: #62525b;
  font-size: 1.02rem;
  line-height: 1.6;
}

.hero-badge {
  min-width: 200px;
  align-self: center;
  border-radius: 20px;
  padding: 1rem 1.1rem;
  background: #6a1b2c;
  color: #fff;
  display: grid;
  gap: 0.2rem;
  box-shadow: 0 18px 40px rgba(106, 27, 44, 0.22);
}

.hero-badge small {
  opacity: 0.8;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.85rem;
}

.stat-card {
  padding: 1rem 1.1rem;
  border-radius: 18px;
  background: #fff;
  border: 1px solid rgba(106, 27, 44, 0.08);
  display: grid;
  gap: 0.4rem;
}

.stat-card span {
  color: #7c6a70;
  font-size: 0.9rem;
}

.stat-card strong {
  color: #5c1a2a;
  font-size: 1.4rem;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(260px, 0.8fr);
  gap: 1rem;
}

.panel {
  border-radius: 24px;
  padding: 1.3rem;
  background: #fff;
  border: 1px solid rgba(106, 27, 44, 0.08);
  box-shadow: 0 18px 40px rgba(106, 27, 44, 0.08);
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.ghost-button {
  border: 1px solid rgba(106, 27, 44, 0.18);
  background: #fff;
  color: #5c1a2a;
  border-radius: 999px;
  padding: 0.72rem 1rem;
  font-weight: 700;
  cursor: pointer;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.9rem;
}

.action-card {
  min-height: 190px;
  border-radius: 20px;
  padding: 1.1rem;
  display: grid;
  gap: 0.6rem;
  text-decoration: none;
  color: inherit;
  background: linear-gradient(180deg, #fbf7f5 0%, #f6eeea 100%);
  border: 1px solid rgba(106, 27, 44, 0.08);
}

.action-card.accent {
  background: linear-gradient(180deg, #6a1b2c 0%, #8d2a43 100%);
  color: #fff;
}

.action-card span {
  font-size: 0.76rem;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  opacity: 0.85;
}

.action-card h3 {
  font-size: 1.2rem;
}

.action-card p {
  line-height: 1.6;
  color: inherit;
  opacity: 0.88;
}

.product-wall {
  grid-column: span 2;
}

.product-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
}

.product-card {
  border-radius: 16px;
  padding: 0.95rem;
  background: rgba(255, 255, 255, 0.6);
  display: grid;
  gap: 0.2rem;
}

.product-card small {
  color: #7e6470;
}

.product-card strong {
  color: #5c1a2a;
}

.product-card span {
  color: #4f4a4d;
}

.panel-side {
  display: grid;
  align-content: start;
  gap: 0.9rem;
}

.benefits {
  margin: 0;
  padding-left: 1.1rem;
  color: #5a5054;
  display: grid;
  gap: 0.6rem;
}

.notice {
  border-radius: 18px;
  padding: 1rem;
  background: #f9f3f0;
  border: 1px solid rgba(106, 27, 44, 0.08);
  color: #5c1a2a;
}

.notice p {
  margin-top: 0.4rem;
  color: #6b5b61;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .content-grid,
  .home-hero {
    grid-template-columns: 1fr;
    display: grid;
  }

  .stats-grid,
  .action-grid,
  .product-list {
    grid-template-columns: 1fr;
  }

  .product-wall {
    grid-column: auto;
  }
}

@media (max-width: 640px) {
  .home-page {
    padding-top: 0.5rem;
  }

  .home-hero,
  .panel {
    padding: 1rem;
    border-radius: 18px;
  }

  .panel-heading {
    flex-direction: column;
  }

  .hero-badge {
    min-width: 0;
    width: 100%;
  }
}
</style>
