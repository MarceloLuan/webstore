<script setup>
import { computed, ref, watch } from 'vue'
import placeholderImage from '@/assets/images/default.jpg'

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
      <img class="placeholder-image" :src="placeholderImage" alt="" />
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

.placeholder-image {
  object-fit: contain;
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
