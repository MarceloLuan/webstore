<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import ProdutoImagem from './ProdutoImagem.vue'
import { formatCurrency } from '@/utils/currency'

const props = defineProps({ products: { type: Array, required: true } })
// Os IDs são sequenciais no cadastro; uma edição não transforma a peça em novidade.
const latest = computed(() => [...props.products]
  .filter((product) => product.tamanhos?.some((size) => Number(size.quantidade) > 0))
  .sort((a, b) => b.id - a.id)
  .slice(0, 6))
const track = ref(null)
const position = ref(0)

function updatePosition() {
  const element = track.value
  if (!element?.children.length) return
  const first = element.children[0]
  const step = first.getBoundingClientRect().width + parseFloat(getComputedStyle(element).columnGap)
  position.value = Math.round(element.scrollLeft / step)
}

function move(direction) {
  const element = track.value
  if (!element) return
  const step = element.children[0].getBoundingClientRect().width + parseFloat(getComputedStyle(element).columnGap)
  const atEnd = element.scrollLeft + element.clientWidth >= element.scrollWidth - 2
  const left = direction > 0 && atEnd ? 0
    : direction < 0 && element.scrollLeft < 2 ? element.scrollWidth
      : element.scrollLeft + direction * step
  element.scrollTo({ left, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
}

watch(() => latest.value.map((product) => product.id).join(','), async () => {
  await nextTick()
  track.value?.scrollTo({ left: 0, behavior: 'instant' })
  position.value = 0
})
</script>

<template>
  <section v-if="latest.length" class="arrivals" aria-labelledby="arrivals-title" aria-roledescription="carrossel">
    <div class="arrivals-heading">
      <div>
        <p class="eyebrow">Acabaram de chegar</p>
        <h1 id="arrivals-title">Seu próximo look está aqui.</h1>
        <p class="subtitle">Novas peças para vestir do seu jeito.</p>
      </div>
      <div v-if="latest.length > 1" class="controls">
        <button type="button" aria-label="Ver novidades anteriores" aria-controls="arrivals-track" @click="move(-1)">←</button>
        <button type="button" aria-label="Ver próximas novidades" aria-controls="arrivals-track" @click="move(1)">→</button>
      </div>
    </div>
    <div id="arrivals-track" ref="track" class="arrivals-track" tabindex="0" aria-label="Roupas recém-cadastradas. Use as setas para navegar."
      @scroll.passive="updatePosition" @keydown.left.prevent="move(-1)" @keydown.right.prevent="move(1)">
      <RouterLink v-for="(product, index) in latest" :key="product.id" class="arrival-card"
        :to="{ name: 'produto-detalhes', params: { id: product.id } }" :aria-label="`Ver ${product.nome}, ${formatCurrency(product.preco)}`">
        <div class="arrival-photo">
          <ProdutoImagem :src="product.imagem" :alt="product.nome" ratio="4 / 3" />
          <span v-if="index === 0" class="new-badge">Última novidade</span>
        </div>
        <div class="arrival-details">
          <div><small>{{ product.categoria || 'Nova seleção' }}</small><h2>{{ product.nome }}</h2></div>
          <strong>{{ formatCurrency(product.preco) }}</strong>
          <span class="arrival-link">Quero conhecer <span aria-hidden="true">↗</span></span>
        </div>
      </RouterLink>
    </div>
    <p v-if="latest.length > 1" class="navigation-hint">Explore as novidades <span aria-hidden="true">· {{ position + 1 }} / {{ latest.length }}</span></p>
  </section>
</template>

<style scoped>
.arrivals { min-width: 0; padding: clamp(1rem, 2vw, 1.5rem); border-radius: 24px; background: #eadbd1; border: 1px solid var(--border-soft); }
.arrivals-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: 1rem; }
.eyebrow { margin: 0 0 .35rem; color: #754635; font-size: .7rem; letter-spacing: .16em; text-transform: uppercase; }
h1 { margin: 0; color: #531724; font: clamp(1.5rem, 2.8vw, 2.4rem)/1.1 'Palatino Linotype', Georgia, serif; }
.subtitle { margin: .4rem 0 0; color: #695454; font-size: .85rem; }
.controls { display: flex; gap: .4rem; flex-shrink: 0; }
.controls button { width: 44px; height: 44px; border: 1px solid #c6a9a5; border-radius: 50%; background: #fffaf7; color: #531724; font-size: 1.3rem; cursor: pointer; }
.controls button:hover { background: #531724; color: #fffaf7; }
.arrivals-track { display: flex; gap: 1rem; overflow-x: auto; scroll-snap-type: x mandatory; scrollbar-width: thin; scrollbar-color: #b78e87 transparent; padding-bottom: .65rem; }
.arrival-card { flex: 0 0 calc((100% - 2rem) / 3); min-width: 0; scroll-snap-align: start; background: #fffaf7; border-radius: 16px; overflow: hidden; text-decoration: none; color: #531724; }
.arrival-photo { position: relative; }
.arrival-photo :deep(.product-media) { border: 0; border-radius: 0; min-height: 0; }
.arrival-photo :deep(.product-image) { object-fit: contain; }
.new-badge { position: absolute; top: .7rem; left: .7rem; padding: .35rem .6rem; border-radius: 100px; background: #531724; color: #fffaf7; font-size: .65rem; letter-spacing: .04em; }
.arrival-details { padding: .9rem; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: .5rem; align-items: center; }
.arrival-details small { color: #80625a; font-size: .65rem; text-transform: uppercase; letter-spacing: .1em; }
h2 { margin: .2rem 0 0; font-size: 1rem; overflow-wrap: anywhere; }
.arrival-details strong { font-size: .95rem; white-space: nowrap; }
.arrival-link { grid-column: 1 / -1; display: flex; justify-content: space-between; border-top: 1px solid #eadbd1; padding-top: .6rem; font-size: .75rem; }
.navigation-hint { margin: .35rem 0 0; text-align: right; color: #75594f; font-size: .7rem; }
button:focus-visible, .arrival-card:focus-visible, .arrivals-track:focus-visible { outline: 3px solid #9a654f; outline-offset: -3px; }
@media (max-width: 960px) { .arrival-card { flex-basis: calc((100% - 1rem) / 2); } }
@media (max-width: 600px) {
  .arrivals { padding: .85rem; border-radius: 18px; }
  .arrival-card { flex-basis: 88%; }
  .arrivals-track { gap: .65rem; }
  .arrivals-heading { gap: .5rem; }
  .controls { gap: .2rem; }
  .controls button { width: 40px; height: 40px; }
  .subtitle { font-size: .75rem; }
  .arrival-details { grid-template-columns: 1fr; }
}
</style>
