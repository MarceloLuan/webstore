<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  src: {
    type: String,
    default: '',
  },
  alt: {
    type: String,
    default: 'Imagem do produto',
  },
  ratio: {
    type: String,
    default: '4 / 5',
  },
})

const hasError = ref(false)

const hasImage = computed(() => Boolean(props.src && props.src.trim()))
const showPlaceholder = computed(() => !hasImage.value || hasError.value)

const placeholderSvg =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 800 1000'%3E%3Cdefs%3E%3ClinearGradient id='g' x1='0' y1='0' x2='1' y2='1'%3E%3Cstop stop-color='%23f7ede7'/%3E%3Cstop offset='1' stop-color='%23f0ddd5'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width='800' height='1000' rx='48' fill='url(%23g)'/%3E%3Ccircle cx='400' cy='380' r='122' fill='%23ffffff' fill-opacity='.58'/%3E%3Cpath d='M252 690l111-150 94 108 65-74 126 116H252z' fill='%23ffffff' fill-opacity='.72'/%3E%3Ccircle cx='330' cy='332' r='28' fill='%23d5b1a4' fill-opacity='.75'/%3E%3C/svg%3E"

function handleError() {
  hasError.value = true
}

watch(
  () => props.src,
  () => {
    hasError.value = false
  },
)
</script>

<template>
  <div class="product-media" :style="{ aspectRatio: ratio }" :class="{ placeholder: showPlaceholder }">
    <img
      v-if="!showPlaceholder"
      class="product-image"
      :src="src"
      :alt="alt"
      loading="lazy"
      decoding="async"
      @error="handleError"
    />

    <div v-else class="product-placeholder" aria-hidden="true">
      <img class="placeholder-image" :src="placeholderSvg" alt="" />
      <span>Sem imagem</span>
    </div>
  </div>
</template>

<style scoped>
.product-media {
  width: 100%;
  overflow: hidden;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8f0ec 0%, #efe2dc 100%);
  border: 1px solid rgba(106, 27, 44, 0.08);
  min-height: 180px;
}

.product-image,
.placeholder-image {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.product-placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  position: relative;
}

.product-placeholder span {
  position: absolute;
  bottom: 0.85rem;
  left: 50%;
  transform: translateX(-50%);
  border-radius: 999px;
  padding: 0.35rem 0.7rem;
  background: rgba(91, 26, 38, 0.82);
  color: #fff8f1;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.04em;
}
</style>