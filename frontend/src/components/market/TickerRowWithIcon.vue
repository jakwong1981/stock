<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Instrument, Quote } from '@/api/types'
import type { TickState } from '@/stores/quotes'
import { useSettingsStore } from '@/stores/settings'
import { sign } from '@/utils/decimal'
import { formatCompact, formatPercent, formatPrice, formatSigned } from '@/utils/format'
import StockLogo from '@/components/common/StockLogo.vue'

/** One quote row with brand logo and a neon flash on every price change. */
const props = defineProps<{ instrument?: Instrument; symbol: string; quote?: Quote; tick?: TickState }>()
defineEmits<{ select: [string] }>()

const { t } = useI18n()
const settings = useSettingsStore()
const dir = computed(() => sign(props.quote?.change))
const flashKey = computed(() => `${props.symbol}-${props.tick?.seq ?? 0}`)
</script>

<template>
  <li>
    <button
      type="button"
      class="grid min-h-14 w-full grid-cols-[auto_1fr_auto] items-center gap-3 rounded-lg px-2 py-2 text-left
        hover:bg-neon-cyan/5 sm:grid-cols-[auto_1fr_auto_auto]"
      @click="$emit('select', symbol)"
    >
      <StockLogo :symbol="symbol" :src="instrument?.logoUrl" :size="36" />
      <span class="min-w-0">
        <span class="num block text-sm font-bold text-ink">{{ symbol }}</span>
        <span class="block truncate text-xs text-ink-dim">{{ instrument?.shortName ?? '' }}</span>
      </span>
      <span class="hidden text-right text-xs text-ink-dim sm:block">
        <span class="sr-only">{{ t('market.volume') }}</span>
        <span class="num">{{ formatCompact(quote?.volume, settings.locale) }}</span>
      </span>
      <span :key="flashKey" class="rounded px-1 text-right" :class="settings.flashClass(tick?.dir ?? 0)">
        <span class="num block text-base font-semibold text-ink">{{ formatPrice(quote?.price, settings.locale) }}</span>
        <span class="num block text-xs" :class="settings.directionClass(dir)">
          {{ formatSigned(quote?.change, settings.locale) }} ({{ formatPercent(quote?.changePercent, settings.locale) }})
        </span>
      </span>
    </button>
  </li>
</template>
