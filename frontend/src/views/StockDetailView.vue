<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Star } from '@lucide/vue'
import GlassPanel from '@/components/cyberpunk/GlassPanel.vue'
import CyberNeonCard from '@/components/cyberpunk/CyberNeonCard.vue'
import GlowButton from '@/components/cyberpunk/GlowButton.vue'
import StockLogo from '@/components/common/StockLogo.vue'
import CandlestickChart from '@/components/charts/CandlestickChart.vue'
import NewsFeed from '@/components/news/NewsFeed.vue'
import { useMarketStream } from '@/composables/useMarketStream'
import { useInstrumentStore } from '@/stores/instruments'
import { useNewsStore } from '@/stores/news'
import { useQuoteStore } from '@/stores/quotes'
import { useSettingsStore } from '@/stores/settings'
import { useWatchlistStore } from '@/stores/watchlist'
import { sign } from '@/utils/decimal'
import { formatCompact, formatPercent, formatPrice, formatSigned, formatTime } from '@/utils/format'

const props = defineProps<{ symbol: string }>()
const { t } = useI18n()
const instruments = useInstrumentStore()
const quotes = useQuoteStore()
const settings = useSettingsStore()
const watchlist = useWatchlistStore()
const news = useNewsStore()
const stream = useMarketStream()

const sym = computed(() => props.symbol.toUpperCase())
const inst = computed(() => instruments.get(sym.value))
const q = computed(() => quotes.quotes[sym.value])
const tick = computed(() => quotes.ticks[sym.value])
const dir = computed(() => sign(q.value?.change))
const related = computed(() => news.forSymbol(sym.value).slice(0, 20))

watch(sym, (s, old) => {
  stream.subscribe([s])
  if (old && !watchlist.has(old)) stream.unsubscribe([old])
}, { immediate: true })
onBeforeUnmount(() => {
  if (!watchlist.has(sym.value)) stream.unsubscribe([sym.value])
})
</script>

<template>
  <div v-if="inst" class="grid gap-3 lg:grid-cols-12">
    <GlassPanel glow="cyan" class="lg:col-span-12">
      <div class="flex flex-wrap items-center gap-x-4 gap-y-3">
        <StockLogo :symbol="sym" :src="inst.logoUrl" :size="52" />
        <!-- basis-48: name keeps a readable width and wraps the price row below on phones -->
        <div class="min-w-0 flex-1 basis-48">
          <h1 class="text-xl font-bold text-ink">
            <span class="num text-neon-yellow">{{ sym }}</span> <span class="break-keep">{{ inst.name }}</span>
          </h1>
          <p class="text-xs text-ink-dim">{{ inst.exchange }} · {{ inst.currency }}</p>
        </div>
        <div :key="tick?.seq" class="rounded px-2 text-right max-sm:ml-auto"
          :class="settings.flashClass(tick?.dir ?? 0)">
          <p class="num text-3xl font-bold text-ink" aria-live="off">{{ formatPrice(q?.price, settings.locale) }}</p>
          <p class="num text-sm" :class="settings.directionClass(dir)">
            {{ formatSigned(q?.change, settings.locale) }} ({{ formatPercent(q?.changePercent, settings.locale) }})
          </p>
          <p v-if="q" class="text-[11px] text-ink-dim">{{ formatTime(q.ts, settings.locale, inst.timezone) }}</p>
        </div>
        <GlowButton :variant="watchlist.has(sym) ? 'ghost' : 'primary'" class="max-sm:w-full"
          @click="watchlist.toggle(sym)">
          <Star :size="16" aria-hidden="true" :fill="watchlist.has(sym) ? 'currentColor' : 'none'" />
          {{ watchlist.has(sym) ? t('market.removeFromWatchlist') : t('market.addToWatchlist') }}
        </GlowButton>
      </div>
    </GlassPanel>

    <div class="grid grid-cols-2 gap-2 sm:grid-cols-5 lg:col-span-12">
      <CyberNeonCard :label="t('market.open')"><span class="num">{{ formatPrice(q?.open, settings.locale) }}</span></CyberNeonCard>
      <CyberNeonCard :label="t('market.high')"><span class="num">{{ formatPrice(q?.high, settings.locale) }}</span></CyberNeonCard>
      <CyberNeonCard :label="t('market.low')"><span class="num">{{ formatPrice(q?.low, settings.locale) }}</span></CyberNeonCard>
      <CyberNeonCard :label="t('market.prevClose')" accent="purple"><span class="num">{{ formatPrice(q?.prevClose, settings.locale) }}</span></CyberNeonCard>
      <CyberNeonCard :label="t('market.volume')" accent="yellow"><span class="num">{{ formatCompact(q?.volume, settings.locale) }}</span></CyberNeonCard>
    </div>

    <GlassPanel :title="t('chart.title')" class="lg:col-span-8">
      <CandlestickChart :symbol="sym" :timezone="inst.timezone" />
    </GlassPanel>

    <GlassPanel :title="t('news.title')" glow="purple" class="lg:col-span-4">
      <NewsFeed :items="related" />
    </GlassPanel>
  </div>
  <p v-else-if="instruments.loaded" class="text-ink-dim">{{ t('errors.INSTRUMENT_NOT_FOUND') }}</p>
  <p v-else class="text-ink-dim">{{ t('common.loading') }}</p>
</template>
