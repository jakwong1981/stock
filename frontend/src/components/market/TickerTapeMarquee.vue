<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useInstrumentStore } from '@/stores/instruments'
import { useQuoteStore } from '@/stores/quotes'
import { useSettingsStore } from '@/stores/settings'
import { sign } from '@/utils/decimal'
import { formatPercent, formatPrice } from '@/utils/format'
import { hapticTap } from '@/platform/native'
import StockLogo from '@/components/common/StockLogo.vue'

/**
 * Infinite neon ticker tape. The list is rendered twice and translated by -50% with a
 * GPU-accelerated translate3d animation; hovering or focusing pauses it. With
 * prefers-reduced-motion the animation stops and the tape becomes horizontally scrollable.
 */
const { t } = useI18n()
const router = useRouter()
const instruments = useInstrumentStore()
const quotes = useQuoteStore()
const settings = useSettingsStore()

const items = computed(() => instruments.marquee)
const duration = computed(() => `${Math.max(20, items.value.length * 6)}s`)

function select(symbol: string) {
  void hapticTap()
  void router.push({ name: 'stock', params: { symbol } })
}
</script>

<template>
  <nav
    :aria-label="t('market.hotStocks')"
    class="marquee group relative overflow-hidden border-y border-neon-cyan/30 bg-night/90"
  >
    <div
      class="marquee-track flex w-max animate-marquee group-hover:[animation-play-state:paused]
        group-focus-within:[animation-play-state:paused]"
      :style="{ '--marquee-duration': duration }"
    >
      <template v-for="copy in 2" :key="copy">
        <button
          v-for="i in items"
          :key="`${copy}-${i.symbol}`"
          type="button"
          :tabindex="copy === 2 ? -1 : 0"
          :aria-hidden="copy === 2 ? 'true' : undefined"
          class="flex min-h-11 shrink-0 items-center gap-2 border-r border-line/60 px-4 hover:bg-neon-cyan/5"
          @click="select(i.symbol)"
        >
          <StockLogo :symbol="i.symbol" :src="i.logoUrl" :size="20" />
          <span class="num text-xs font-bold text-neon-yellow">{{ i.symbol }}</span>
          <span class="num text-sm text-ink">{{ formatPrice(quotes.quotes[i.symbol]?.price, settings.locale) }}</span>
          <span
            class="num text-xs"
            :class="settings.directionClass(sign(quotes.quotes[i.symbol]?.change))"
          >{{ formatPercent(quotes.quotes[i.symbol]?.changePercent, settings.locale) }}</span>
        </button>
      </template>
    </div>
    <!-- edge fades -->
    <div aria-hidden="true" class="pointer-events-none absolute inset-y-0 left-0 w-8 bg-gradient-to-r from-night" />
    <div aria-hidden="true" class="pointer-events-none absolute inset-y-0 right-0 w-8 bg-gradient-to-l from-night" />
  </nav>
</template>

<style scoped>
.marquee-track {
  will-change: transform;
}
@media (prefers-reduced-motion: reduce) {
  .marquee {
    overflow-x: auto;
  }
  .marquee-track {
    animation: none !important;
  }
}
</style>
