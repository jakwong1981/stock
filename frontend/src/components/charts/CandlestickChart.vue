<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CandlestickSeries,
  ColorType,
  createChart,
  CrosshairMode,
  HistogramSeries,
  LineSeries,
  type CandlestickData,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts'
import { marketApi } from '@/api'
import type { Candle } from '@/api/types'
import { useQuoteStore } from '@/stores/quotes'
import { useSettingsStore } from '@/stores/settings'
import { Decimal, dec } from '@/utils/decimal'

/**
 * Cyber-themed K-line chart (TradingView lightweight-charts) with volume, MA20 and live
 * updates of the latest candle. Prices are decimal strings in the store; they are converted
 * to numbers only at the rendering boundary, which the chart library requires.
 */
const props = defineProps<{ symbol: string; timezone: string }>()

const INTERVALS = ['1m', '5m', '15m', '1h', '4h', '1d', '1w', '1M'] as const
const INTRADAY_SECONDS: Record<string, number> = { '1m': 60, '5m': 300, '15m': 900, '1h': 3600, '4h': 14400 }

const { t } = useI18n()
const settings = useSettingsStore()
const quotes = useQuoteStore()
const interval = ref<(typeof INTERVALS)[number]>('1d')
const el = ref<HTMLDivElement | null>(null)
const loading = ref(false)
const chart = shallowRef<IChartApi | null>(null)
let candleSeries: ISeriesApi<'Candlestick'> | null = null
let volumeSeries: ISeriesApi<'Histogram'> | null = null
let maSeries: ISeriesApi<'Line'> | null = null
let last: CandlestickData<UTCTimestamp> | null = null

const colors = computed(() => {
  const green = '#22f59a'
  const red = '#ff3b6b'
  return settings.colorScheme === 'GREEN_UP' ? { up: green, down: red } : { up: red, down: green }
})

const toNum = (s: string) => Number(s) // render boundary only

function ma(candles: Candle[], n: number) {
  const out: { time: UTCTimestamp; value: number }[] = []
  let sum = new Decimal(0)
  for (let i = 0; i < candles.length; i++) {
    sum = sum.plus(candles[i].close)
    if (i >= n) sum = sum.minus(candles[i - n].close)
    if (i >= n - 1) out.push({ time: candles[i].time as UTCTimestamp, value: sum.div(n).toNumber() })
  }
  return out
}

function formatTime(ts: number, withTime: boolean): string {
  return new Intl.DateTimeFormat(settings.locale, {
    timeZone: props.timezone,
    month: 'short',
    day: 'numeric',
    ...(withTime ? { hour: '2-digit', minute: '2-digit' } : { year: '2-digit' }),
  }).format(new Date(ts * 1000))
}

function build() {
  if (!el.value) return
  chart.value = createChart(el.value, {
    autoSize: true,
    layout: { background: { type: ColorType.Solid, color: 'transparent' }, textColor: '#a3abc2', fontSize: 11 },
    grid: { vertLines: { color: 'rgba(0,240,255,0.06)' }, horzLines: { color: 'rgba(0,240,255,0.06)' } },
    crosshair: { mode: CrosshairMode.Normal },
    rightPriceScale: { borderColor: '#1f2333' },
    timeScale: { borderColor: '#1f2333', timeVisible: true, secondsVisible: false },
    localization: {
      locale: settings.locale,
      timeFormatter: (ts: number) => formatTime(ts, interval.value in INTRADAY_SECONDS),
    },
  })
  candleSeries = chart.value.addSeries(CandlestickSeries, {
    upColor: colors.value.up, downColor: colors.value.down, wickUpColor: colors.value.up,
    wickDownColor: colors.value.down, borderVisible: false,
  })
  volumeSeries = chart.value.addSeries(HistogramSeries, { priceScaleId: '', priceFormat: { type: 'volume' } })
  volumeSeries.priceScale().applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } })
  maSeries = chart.value.addSeries(LineSeries, { color: '#bd00ff', lineWidth: 1, priceLineVisible: false,
    lastValueVisible: false })
}

async function load() {
  if (!candleSeries || !volumeSeries || !maSeries) return
  loading.value = true
  try {
    const data = await marketApi.candles(props.symbol, interval.value, 300)
    const c = data.map((d) => ({ time: d.time as UTCTimestamp, open: toNum(d.open), high: toNum(d.high),
      low: toNum(d.low), close: toNum(d.close) }))
    candleSeries.setData(c)
    volumeSeries.setData(data.map((d) => ({
      time: d.time as UTCTimestamp, value: toNum(d.volume),
      color: (dec(d.close)?.gte(d.open) ? colors.value.up : colors.value.down) + '55',
    })))
    maSeries.setData(ma(data, 20))
    last = c.at(-1) ?? null
    chart.value?.timeScale().fitContent()
  } finally {
    loading.value = false
  }
}

// Live update of the latest candle from the throttled quote store
watch(() => quotes.quotes[props.symbol], (q) => {
  if (!q || !last || !candleSeries) return
  const price = toNum(q.price)
  const sec = INTRADAY_SECONDS[interval.value]
  const nowSec = Math.floor(q.ts / 1000)
  const bucket = (sec ? nowSec - (nowSec % sec) : last.time) as UTCTimestamp
  if (bucket > last.time) {
    last = { time: bucket, open: last.close, high: Math.max(last.close, price), low: Math.min(last.close, price),
      close: price }
  } else {
    last = { ...last, high: Math.max(last.high, price), low: Math.min(last.low, price), close: price }
  }
  candleSeries.update(last)
})

watch([interval, () => props.symbol], load)
watch(colors, (c) => {
  candleSeries?.applyOptions({ upColor: c.up, downColor: c.down, wickUpColor: c.up, wickDownColor: c.down })
  void load()
})
watch(() => settings.locale, (l) => chart.value?.applyOptions({ localization: { locale: l } }))

onMounted(() => {
  build()
  void load()
})
onBeforeUnmount(() => chart.value?.remove())
</script>

<template>
  <div>
    <div role="group" :aria-label="t('chart.interval')" class="mb-2 flex items-center gap-1">
      <button
        v-for="iv in INTERVALS"
        :key="iv"
        type="button"
        class="num min-h-9 min-w-0 flex-1 rounded-md border px-1 text-xs sm:min-w-10 sm:flex-none sm:px-2"
        :class="iv === interval ? 'border-neon-cyan bg-neon-cyan/10 text-neon-cyan' : 'border-line text-ink-dim'"
        :aria-pressed="iv === interval"
        @click="interval = iv"
      >{{ iv }}</button>
      <span v-if="loading" class="ml-2 hidden text-xs text-ink-dim sm:inline">{{ t('common.loading') }}</span>
    </div>
    <div ref="el" class="h-72 w-full sm:h-96" role="img" :aria-label="`${t('chart.title')} ${symbol}`" />
  </div>
</template>
