<script setup>
defineProps({
  products: {
    type: Array,
    default: () => [],
  },
  adminMode: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['edit', 'delete'])
</script>

<template>
  <div v-if="products.length" class="product-list-shell" :class="{ admin: adminMode }">
    <article v-for="product in products" :key="product.id" class="product-item">
      <div class="product-card-header">
        <small>{{ product.destaque }}</small>
        <span v-if="adminMode" class="id-pill">#{{ product.id }}</span>
      </div>

      <h3>{{ product.nome }}</h3>
      <p v-if="product.descricao">{{ product.descricao }}</p>
      <strong>R$ {{ Number(product.preco).toFixed(2).replace('.', ',') }}</strong>

      <div v-if="adminMode" class="actions-row">
        <button class="secondary-button" type="button" @click="emit('edit', product)">Editar</button>
        <button class="danger-button" type="button" @click="emit('delete', product)">Excluir</button>
      </div>
    </article>
  </div>

  <p v-else class="empty-state">Nenhum produto cadastrado ainda.</p>
</template>

<style scoped>
.product-list-shell {
  display: grid;
  gap: 0.9rem;
}

.product-list-shell:not(.admin) {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.admin {
  grid-template-columns: 1fr;
}

.product-item {
  border-radius: 18px;
  padding: 1rem;
  background: linear-gradient(180deg, #fbf7f5 0%, #f7efeb 100%);
  border: 1px solid rgba(106, 27, 44, 0.08);
  display: grid;
  gap: 0.45rem;
}

.product-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

small {
  color: #7e6470;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-size: 0.72rem;
}

h3,
p {
  margin: 0;
}

h3 {
  color: #5c1a2a;
}

p {
  color: #5e5659;
  line-height: 1.5;
}

strong {
  color: #3f383c;
}

.id-pill {
  border-radius: 999px;
  padding: 0.25rem 0.55rem;
  background: #f0dfd8;
  color: #5c1a2a;
  font-size: 0.78rem;
  font-weight: 700;
}

.actions-row {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-top: 0.25rem;
}

.secondary-button,
.danger-button {
  border: none;
  border-radius: 999px;
  padding: 0.72rem 0.95rem;
  font-weight: 700;
  cursor: pointer;
}

.secondary-button {
  background: #f3ebe8;
  color: #5c1a2a;
}

.danger-button {
  background: #f4ded9;
  color: #7b1d24;
}

.empty-state {
  margin: 0;
  color: #6b5b61;
}

@media (max-width: 900px) {
  .product-list-shell:not(.admin) {
    grid-template-columns: 1fr;
  }
}
</style>
