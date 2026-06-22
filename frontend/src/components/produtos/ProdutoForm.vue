<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({
      nome: '',
      preco: '',
      imagem: '',
      descricao: '',
      categoria: '',
      tamanhos: [{ tamanho: '', quantidade: 1 }],
    }),
  },
  categoryOptions: {
    type: Array,
    default: () => [],
  },
  sizeOptions: {
    type: Array,
    default: () => [],
  },
  optionsLoading: {
    type: Boolean,
    default: false,
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

const emit = defineEmits(['update:modelValue', 'submit', 'cancel', 'add-size', 'remove-size'])

function createSizeRowId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  return `size-row-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function normalizeSizeRows(value) {
  return Array.isArray(value) && value.length
    ? value.map((item) => ({
        rowId: item?.rowId || item?.id || createSizeRowId(),
        tamanho: item?.tamanho || '',
        quantidade: Number(item?.quantidade ?? 1),
      }))
    : [{ rowId: createSizeRowId(), tamanho: '', quantidade: 1 }]
}

function normalizeForm(value) {
  return {
    nome: value?.nome ?? '',
    preco: value?.preco ?? '',
    imagem: value?.imagem ?? '',
    descricao: value?.descricao ?? '',
    categoria: value?.categoria ?? '',
    tamanhos: normalizeSizeRows(value?.tamanhos),
  }
}

function isSameSizeRow(left, right) {
  return left?.rowId === right?.rowId
    && left?.tamanho === right?.tamanho
    && Number(left?.quantidade ?? 0) === Number(right?.quantidade ?? 0)
}

function isSameForm(left, right) {
  return left.nome === right.nome
    && left.preco === right.preco
    && left.imagem === right.imagem
    && left.descricao === right.descricao
    && left.categoria === right.categoria
    && Array.isArray(left.tamanhos)
    && Array.isArray(right.tamanhos)
    && left.tamanhos.length === right.tamanhos.length
    && left.tamanhos.every((item, index) => isSameSizeRow(item, right.tamanhos[index]))
}

const form = reactive(normalizeForm(props.modelValue))

watch(
  () => props.modelValue,
  (value) => {
    const nextForm = normalizeForm(value)

    if (!isSameForm(form, nextForm)) {
      Object.assign(form, nextForm)
    }
  },
  { deep: true },
)

watch(
  form,
  (value) => {
    const nextForm = normalizeForm(value)

    if (!isSameForm(nextForm, props.modelValue || {})) {
      emit('update:modelValue', nextForm)
    }
  },
  { deep: true },
)

function addSizeRow() {
  emit('add-size')
}

function removeSizeRow(index) {
  emit('remove-size', index)
}

function handleSubmit() {
  emit('submit')
}

</script>

<template>
  <form class="product-form" @submit.prevent="handleSubmit">
    <p class="required-note"><span aria-hidden="true">*</span> Campos obrigatórios</p>

    <label for="nome">Nome do produto <span class="required-mark" aria-hidden="true">*</span></label>
    <input id="nome" v-model="form.nome" type="text" placeholder="Ex.: Vestido Floral" required />

    <label for="preco">Preço <span class="required-mark" aria-hidden="true">*</span></label>
    <input id="preco" v-model="form.preco" type="text" placeholder="129,90" required />

    <label for="imagem">Imagem do produto</label>
    <input id="imagem" v-model="form.imagem" type="text" placeholder="https://..." />

    <label for="descricao">Descrição curta</label>
    <textarea id="descricao" v-model="form.descricao" rows="4" placeholder="Uma descrição curta para a vitrine"></textarea>

    <label for="categoria">Categoria <span class="required-mark" aria-hidden="true">*</span></label>
    <select id="categoria" v-model="form.categoria" :disabled="optionsLoading" required>
      <option value="">Selecione uma categoria</option>
      <option v-for="category in categoryOptions" :key="category" :value="category">{{ category }}</option>
    </select>

    <div class="sizes-section">
      <div class="sizes-header">
        <div>
          <p class="sizes-title">Tamanhos <span class="required-mark" aria-hidden="true">*</span></p>
          <p class="sizes-help">Selecione uma ou mais combinações de tamanho e estoque.</p>
        </div>
        <button class="secondary-button" type="button" @click="addSizeRow">Adicionar tamanho</button>
      </div>

      <div v-for="(sizeItem, index) in form.tamanhos" :key="sizeItem.rowId" class="size-row">
        <label :for="`tamanho-${index}`" class="sr-only">Tamanho {{ index + 1 }}</label>
        <select :id="`tamanho-${index}`" v-model="sizeItem.tamanho" :disabled="optionsLoading" required>
          <option value="">Selecione o tamanho</option>
          <option v-for="size in sizeOptions" :key="size" :value="size">{{ size }}</option>
        </select>

        <label :for="`quantidade-${index}`" class="sr-only">Quantidade {{ index + 1 }}</label>
        <input :id="`quantidade-${index}`" v-model="sizeItem.quantidade" type="number" min="0" step="1" placeholder="Qtd" required />

        <button class="remove-button" type="button" @click="removeSizeRow(sizeItem.rowId)">Remover</button>
      </div>
    </div>

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

.required-note {
  margin: 0 0 0.2rem;
  color: #6e6166;
  font-size: 0.82rem;
}

.required-note span,
.required-mark {
  color: #a4232f;
  font-weight: 700;
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

select {
  border: 1px solid #eadfdb;
  border-radius: 12px;
  padding: 0.8rem 0.95rem;
  background: #fdfafa;
  font-size: 0.96rem;
  font-family: inherit;
}

select:focus {
  outline: 2px solid rgba(106, 27, 44, 0.18);
  border-color: #6a1b2c;
}

.sizes-section {
  display: grid;
  gap: 0.65rem;
  padding: 0.9rem;
  border-radius: 16px;
  background: #fbf7f5;
  border: 1px solid #edded8;
}

.sizes-header {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  align-items: center;
}

.sizes-title {
  margin: 0;
  color: #5c1a2a;
  font-weight: 700;
}

.sizes-help {
  margin: 0.2rem 0 0;
  color: #6e6166;
  font-size: 0.9rem;
}

.size-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 110px auto;
  gap: 0.6rem;
  align-items: center;
}

.remove-button {
  border: none;
  border-radius: 999px;
  padding: 0.72rem 0.9rem;
  background: #f4ded9;
  color: #7b1d24;
  font-weight: 700;
  cursor: pointer;
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

@media (max-width: 720px) {
  .sizes-header,
  .size-row {
    grid-template-columns: 1fr;
  }
}
</style>
