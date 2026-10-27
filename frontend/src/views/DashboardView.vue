<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import GlassPanel from '@/components/cyberpunk/GlassPanel.vue'
import TickerRowWithIcon from '@/components/market/TickerRowWithIcon.vue'
import MarketStatusBar from '@/components/market/MarketStatusBar.vue'
import NewsFeed from '@/components/news/NewsFeed.vue'
import CandlestickChart from '@/components/charts/CandlestickChart.vue'
import { useMarketStream } from '@/composables/useMarketStream'
import { useInstrumentStore } from '@/stores/instruments'
import { useNewsStore } from '@/stores/news'
import { useQuoteStore } from '@/stores/quotes'
import { useWatchlistStore } from '@/stores/watchlist'

const { t } = useI18n()
const router = useRouter()
const instruments = useInstrumentStore()
const quotes = useQuoteStore()
const watchlist = useWatchlistStore()
const news = useNewsStore()
const stream = useMarketStream()

const focus = computed(() => watchlist.symbols[0] ?? instruments.marquee[0]?.symbol ?? 'AAPL')
const focusInstrument = computed(() => instruments.get(focus.value))
const latestNews = computed(() => news.items.slice(0, 8))

watch(() => watchlist.symbols, (s) => stream.subscribe(s), { immediate: true })

const open = (symbol: string) => router.push({ name: 'stock', params: { symbol } })
</script>

<template>
  <div class="grid gap-3 lg:grid-cols-12">
    <div class="lg:col-span-12"><MarketStatusBar /></div>

    <GlassPanel :title="t('market.watchlist')" glow="cyan" class="lg:col-span-4 lg:row-span-2">
      <p v-if="!watchlist.symbols.length" class="text-sm text-ink-dim">{{ t('market.emptyWatchlist') }}</p>
      <ul v-else class="-mx-2">
        <TickerRowWithIcon
          v-for="s in watchlist.symbols"
          :key="s"
          :symbol="s"
          :instrument="instruments.get(s)"
          :quote="quotes.quotes[s]"
          :tick="quotes.ticks[s]"
          @select="open"
        />
      </ul>
    </GlassPanel>

    <GlassPanel class="lg:col-span-8">
      <template #actions>
        <button type="button" class="min-h-11 text-left" @click="open(focus)">
          <span class="num text-lg font-bold text-neon-yellow">{{ focus }}</span>
          <span class="ml-2 text-sm text-ink-dim">{{ focusInstrument?.name }}</span>
        </button>
      </template>
      <CandlestickChart
        v-if="focusInstrument"
        :key="focus"
        :symbol="focus"
        :timezone="focusInstrument.timezone"
      />
    </GlassPanel>

    <GlassPanel :title="t('news.title')" glow="purple" class="lg:col-span-8">
      <template #actions>
        <RouterLink to="/news" class="inline-flex min-h-11 items-center text-xs text-neon-cyan hover:underline">
          {{ t('common.viewAll') }}
        </RouterLink>
      </template>
      <NewsFeed :items="latestNews" compact />
    </GlassPanel>
  </div>
</template>
