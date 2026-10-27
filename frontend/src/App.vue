<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppShell from '@/layouts/AppShell.vue'
import SplashScreen from '@/components/cyberpunk/SplashScreen.vue'
import { marketApi } from '@/api'
import { useCapacitorApp } from '@/composables/useCapacitorApp'
import { useMarketStream } from '@/composables/useMarketStream'
import { useNewsStream } from '@/composables/useNewsStream'
import { useInstrumentStore } from '@/stores/instruments'
import { useWatchlistStore } from '@/stores/watchlist'

const instruments = useInstrumentStore()
const watchlist = useWatchlistStore()
const stream = useMarketStream()
const dataSource = ref('')
// The opening animation plays while the initial data loads underneath it
const showSplash = ref(true)
const ready = ref(false)

useCapacitorApp()

onMounted(async () => {
  await Promise.all([
    instruments.load().catch(() => undefined),
    watchlist.load().catch(() => undefined),
    marketApi.provider().then((p) => (dataSource.value = p.provider)).catch(() => undefined),
  ])
  // Marquee + watchlist symbols stream continuously
  stream.subscribe([...new Set([...instruments.marquee.map((i) => i.symbol), ...watchlist.symbols])])
  useNewsStream()
  ready.value = true
})
</script>

<template>
  <!-- inert: the app underneath cannot be focused or clicked while the splash is shown -->
  <AppShell :data-source="dataSource" :inert="showSplash" />
  <SplashScreen v-if="showSplash" :ready="ready" @done="showSplash = false" />
</template>
