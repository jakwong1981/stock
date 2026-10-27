<script setup lang="ts">
import { computed, ref, watch } from 'vue'

/**
 * Company logo with graceful fallback to a neon monogram badge when the image is
 * missing or fails to load. Images are lazy-loaded.
 */
const props = withDefaults(defineProps<{ symbol: string; src?: string | null; size?: number }>(), {
  src: null,
  size: 32,
})

const failed = ref(false)
watch(() => props.src, () => (failed.value = false))

const initials = computed(() => props.symbol.replace(/\.HK$/, '').replace(/^0+/, '').slice(0, 2) || props.symbol[0])
// Deterministic hue per symbol so badges are distinguishable
const hue = computed(() => [...props.symbol].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7))
</script>

<template>
  <img
    v-if="src && !failed"
    :src="src"
    :width="size"
    :height="size"
    alt=""
    loading="lazy"
    decoding="async"
    class="shrink-0 rounded-md bg-white/5 object-contain"
    :style="{ width: `${size}px`, height: `${size}px` }"
    @error="failed = true"
  />
  <span
    v-else
    aria-hidden="true"
    class="num inline-flex shrink-0 items-center justify-center rounded-md border font-bold"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      fontSize: `${Math.round(size * 0.38)}px`,
      color: `hsl(${hue} 100% 70%)`,
      borderColor: `hsl(${hue} 100% 60% / 0.6)`,
      boxShadow: `0 0 10px hsl(${hue} 100% 60% / 0.25)`,
    }"
  >{{ initials }}</span>
</template>
