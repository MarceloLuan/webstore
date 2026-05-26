<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({
      nome: '',
      preco: '',
      destaque: '',
      imagem: '',
      descricao: '',
    }),
  },
  loading: {
    type: Boolean,
    default: false,
  },
  submitLabel: {
    type: String,
    default: 'Salvar produto',
  },
})

const emit = defineEmits(['update:modelValue', 'submit', 'cancel'])

const form = reactive({ ...props.modelValue })

watch(
  () => props.modelValue,
  (value) => {
    Object.assign(form, value)
  },
  { deep: true },
)

watch(
  form,
  (value) => {
    emit('update:modelValue', { ...value })
  },
  { deep: true },
)

function handleSubmit() {
  emit('submit')
}

</script>

<template>
  <form class="product-form" @submit.prevent="handleSubmit">
    <label for="nome">Nome do produto</label>
    <input id="nome" v-model="form.nome" type="text" placeholder="Ex.: Vestido Floral" />

    <label for="preco">Preço</label>
    <input id="preco" v-model="form.preco" type="text" placeholder="129,90" />

    <label for="destaque">Destaque</label>
    <input id="destaque" v-model="form.destaque" type="text" placeholder="Mais vendido" />

    <label for="imagem">Imagem (opcional)</label>
    <input id="imagem" v-model="form.imagem" type="text" placeholder="https://..." />

    <label for="descricao">Descrição curta</label>
    <textarea id="descricao" v-model="form.descricao" rows="4" placeholder="Uma descrição curta para a vitrine"></textarea>

    <div class="actions-row">
      <button class="primary-button" type="submit" :disabled="loading">
        {{ loading ? 'SALVANDO...' : submitLabel }}
      </button>
      <button class="secondary-button" type="button" @click="emit('cancel')">Cancelar</button>
    </div>
  </form>
</template>

<style scoped>
.product-form {
  display: grid;
  gap: 0.7rem;
}

label {
  color: #5d4f54;
  font-size: 0.94rem;
}

input,
textarea {
  border: 1px solid #eadfdb;
  border-radius: 12px;
  padding: 0.8rem 0.95rem;
  background: #fdfafa;
  font-size: 0.96rem;
  font-family: inherit;
}

input:focus,
textarea:focus {
  outline: 2px solid rgba(106, 27, 44, 0.18);
  border-color: #6a1b2c;
}

.actions-row {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  margin-top: 0.2rem;
}

.primary-button,
.secondary-button {
  border: none;
  border-radius: 999px;
  padding: 0.82rem 1rem;
  font-weight: 700;
  cursor: pointer;
}

.primary-button {
  background: #6a1b2c;
  color: #fff;
}

.secondary-button {
  background: #f3ebe8;
  color: #5c1a2a;
}
</style>
