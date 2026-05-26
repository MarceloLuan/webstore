<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getUser } from '@/services/auth'

const user = ref(getUser())
const role = ref(user.value?.role || 'CLIENTE')

const mockProducts = ref([
  { id: 1, nome: 'Vestido Floral', preco: 'R$ 129,90' },
  { id: 2, nome: 'Blusa de Seda', preco: 'R$ 89,90' },
  { id: 3, nome: 'Saia Midi', preco: 'R$ 109,90' },
])
</script>

<template>
  <section class="home-shell">
    <div class="home-card">
      <h1>Bem-vindo, {{ user?.nome || 'usuário' }}</h1>
      <p class="muted">Perfil: <strong>{{ role }}</strong></p>

      <div class="actions">
        <RouterLink :to="`/minha-conta/${user?.id}`" class="card">
          <h3>Gerenciar Conta</h3>
          <p>Atualize seus dados pessoais e senha. Excluir = inativar conta.</p>
        </RouterLink>

        <RouterLink v-if="role === 'ADMIN'" to="/produtos" class="card">
          <h3>Gerenciar Produtos</h3>
          <p>Cadastrar, editar e remover produtos (admin).</p>
        </RouterLink>

        <div v-if="role === 'CLIENTE'" class="card products-card">
          <h3>Produtos em destaque</h3>
          <ul>
            <li v-for="p in mockProducts" :key="p.id">{{ p.nome }} — {{ p.preco }}</li>
          </ul>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.home-shell {
  display: flex;
  justify-content: center;
  padding: 2rem 1rem;
}
.home-card {
  width: min(980px, 96vw);
  background: #fff;
  border-radius: 16px;
  padding: 1.6rem;
  box-shadow: 0 16px 36px rgba(0,0,0,0.08);
}
.muted { color: #6b6b6b }
.actions { display: flex; gap: 1rem; flex-wrap: wrap; margin-top: 1rem }
.card { flex: 1 1 220px; padding: 1rem; border-radius: 12px; background: #faf7f5; text-decoration: none; color: inherit }
.products-card ul { margin: 0.6rem 0 0; padding-left: 1.1rem }
</style>
