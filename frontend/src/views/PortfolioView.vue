<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Trash2 } from '@lucide/vue'
import GlassPanel from '@/components/cyberpunk/GlassPanel.vue'
import CyberNeonCard from '@/components/cyberpunk/CyberNeonCard.vue'
import GlowButton from '@/components/cyberpunk/GlowButton.vue'
import StockLogo from '@/components/common/StockLogo.vue'
import { errorMessage } from '@/api/http'
import type { Side } from '@/api/types'
import { useMarketStream } from '@/composables/useMarketStream'
import { useAuthStore } from '@/stores/auth'
import { useInstrumentStore } from '@/stores/instruments'
import { usePortfolioStore } from '@/stores/portfolio'
import { useSettingsStore } from '@/stores/settings'
import { dec, isValidDecimalInput, sign } from '@/utils/decimal'
import { formatDateTime, formatMoney, formatPercent, formatQuantity } from '@/utils/format'

const { t } = useI18n()
const auth = useAuthStore()
const portfolio = usePortfolioStore()
const instruments = useInstrumentStore()
const settings = useSettingsStore()
const stream = useMarketStream()

const SIDES: Side[] = ['BUY', 'SELL', 'DIVIDEND']
const form = reactive({ symbol: 'AAPL', side: 'BUY' as Side, quantity: '', price: '', fee: '0', date: '' })
const error = ref('')
const busy = ref(false)

const open = computed(() => portfolio.result.positions.filter((p) => !dec(p.quantity)?.isZero()))
const valid = computed(() =>
  isValidDecimalInput(form.quantity) && dec(form.quantity)!.gt(0) && isValidDecimalInput(form.price) &&
  isValidDecimalInput(form.fee || '0'))

async function load() {
  if (!auth.isAuthenticated) return
  try {
    await portfolio.load()
    stream.subscribe(open.value.map((p) => p.symbol))
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function submit() {
  if (!valid.value) return
  busy.value = true
  error.value = ''
  try {
    await portfolio.add({
      symbol: form.symbol, side: form.side, quantity: form.quantity.trim(), price: form.price.trim(),
      fee: (form.fee || '0').trim(), executedAt: form.date ? new Date(form.date).toISOString() : undefined,
    })
    form.quantity = ''
    form.price = ''
    form.fee = '0'
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

async function remove(id: string) {
  if (!confirm(t('portfolio.confirmDelete'))) return
  try {
    await portfolio.remove(id)
  } catch (e) {
    error.value = errorMessage(e)
  }
}

onMounted(load)
watch(() => auth.isAuthenticated, load)
</script>

<template>
  <div v-if="!auth.isAuthenticated" class="mx-auto max-w-md">
    <GlassPanel :title="t('portfolio.title')" glow="cyan">
      <p class="mb-4 text-sm text-ink-dim">{{ t('portfolio.signInRequired') }}</p>
      <RouterLink to="/login?next=/portfolio"><GlowButton>{{ t('common.signIn') }}</GlowButton></RouterLink>
    </GlassPanel>
  </div>

  <div v-else class="grid gap-3 lg:grid-cols-12">
    <p v-if="error" role="alert" class="rounded-lg border border-down/60 bg-down/10 p-3 text-sm text-ink lg:col-span-12">
      {{ error }}
    </p>

    <div class="grid gap-2 sm:grid-cols-2 lg:col-span-12 xl:grid-cols-4">
      <template v-for="s in portfolio.result.summaries" :key="s.currency">
        <CyberNeonCard :label="t('portfolio.summary', { currency: s.currency }) + ' · ' + t('portfolio.marketValue')">
          <span class="num">{{ formatMoney(s.marketValue, s.currency, settings.locale) }}</span>
        </CyberNeonCard>
        <CyberNeonCard :label="t('portfolio.unrealized')" accent="purple">
          <span class="num" :class="settings.directionClass(sign(s.unrealizedPnl))">
            {{ formatMoney(s.unrealizedPnl, s.currency, settings.locale) }}</span>
        </CyberNeonCard>
      </template>
    </div>

    <GlassPanel :title="t('portfolio.positions')" glow="cyan" class="lg:col-span-8">
      <p v-if="!open.length" class="text-sm text-ink-dim">{{ t('portfolio.empty') }}</p>
      <div v-else class="-mx-3 overflow-x-auto sm:mx-0">
        <table class="w-full min-w-[640px] text-sm">
          <thead class="text-xs text-ink-dim">
            <tr class="text-right">
              <th scope="col" class="px-2 py-2 text-left">{{ t('portfolio.symbol') }}</th>
              <th scope="col" class="px-2">{{ t('portfolio.quantity') }}</th>
              <th scope="col" class="px-2">{{ t('portfolio.avgCost') }}</th>
              <th scope="col" class="px-2">{{ t('portfolio.marketValue') }}</th>
              <th scope="col" class="px-2">{{ t('portfolio.unrealized') }}</th>
              <th scope="col" class="px-2">{{ t('portfolio.realized') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-line/60">
            <tr v-for="p in open" :key="p.symbol" class="num text-right">
              <th scope="row" class="px-2 py-2 text-left font-normal">
                <RouterLink :to="`/stock/${p.symbol}`" class="flex items-center gap-2">
                  <StockLogo :symbol="p.symbol" :src="instruments.get(p.symbol)?.logoUrl" :size="24" />
                  <span class="font-bold text-ink">{{ p.symbol }}</span>
                </RouterLink>
              </th>
              <td class="px-2">{{ formatQuantity(p.quantity, settings.locale) }}</td>
              <td class="px-2">{{ formatMoney(p.averageCost, p.currency, settings.locale) }}</td>
              <td class="px-2">{{ formatMoney(p.marketValue, p.currency, settings.locale) }}</td>
              <td class="px-2" :class="settings.directionClass(sign(p.unrealizedPnl))">
                {{ formatMoney(p.unrealizedPnl, p.currency, settings.locale) }}
                <span class="block text-xs">{{ formatPercent(p.unrealizedPnlPercent, settings.locale) }}</span>
              </td>
              <td class="px-2" :class="settings.directionClass(sign(p.realizedPnl))">
                {{ formatMoney(p.realizedPnl, p.currency, settings.locale) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </GlassPanel>

    <GlassPanel :title="t('portfolio.addTransaction')" glow="yellow" class="lg:col-span-4">
      <form class="grid grid-cols-2 gap-3" novalidate @submit.prevent="submit">
        <label class="col-span-2 text-xs text-ink-dim">{{ t('portfolio.symbol') }}
          <select v-model="form.symbol" class="mt-1 min-h-11 w-full rounded-lg border border-line bg-panel px-2 text-sm text-ink">
            <option v-for="i in instruments.all" :key="i.symbol" :value="i.symbol">{{ i.symbol }} · {{ i.shortName }}</option>
          </select>
        </label>
        <fieldset class="col-span-2">
          <legend class="text-xs text-ink-dim">{{ t('portfolio.side') }}</legend>
          <div class="mt-1 grid grid-cols-3 gap-1">
            <label v-for="s in SIDES" :key="s"
              class="flex min-h-11 cursor-pointer items-center justify-center rounded-lg border text-sm"
              :class="form.side === s ? 'border-neon-cyan text-neon-cyan' : 'border-line text-ink-dim'">
              <input v-model="form.side" type="radio" :value="s" class="sr-only" />{{ t(`portfolio.sides.${s}`) }}
            </label>
          </div>
        </fieldset>
        <label class="text-xs text-ink-dim">{{ t('portfolio.quantity') }}
          <input v-model="form.quantity" inputmode="decimal" required
            class="num mt-1 min-h-11 w-full rounded-lg border border-line bg-panel px-2 text-sm text-ink" />
        </label>
        <label class="text-xs text-ink-dim">{{ t('portfolio.price') }}
          <input v-model="form.price" inputmode="decimal" required
            class="num mt-1 min-h-11 w-full rounded-lg border border-line bg-panel px-2 text-sm text-ink" />
        </label>
        <label class="text-xs text-ink-dim">{{ t('portfolio.fee') }}
          <input v-model="form.fee" inputmode="decimal"
            class="num mt-1 min-h-11 w-full rounded-lg border border-line bg-panel px-2 text-sm text-ink" />
        </label>
        <label class="text-xs text-ink-dim">{{ t('portfolio.executedAt') }}
          <input v-model="form.date" type="datetime-local"
            class="mt-1 min-h-11 w-full rounded-lg border border-line bg-panel px-2 text-sm text-ink" />
        </label>
        <GlowButton type="submit" class="col-span-2" :disabled="!valid || busy">{{ t('common.save') }}</GlowButton>
      </form>
    </GlassPanel>

    <GlassPanel :title="t('portfolio.transactions')" class="lg:col-span-12">
      <p v-if="!portfolio.transactions.length" class="text-sm text-ink-dim">{{ t('portfolio.empty') }}</p>
      <ul v-else class="divide-y divide-line/60">
        <li v-for="tx in [...portfolio.transactions].reverse()" :key="tx.id" class="flex flex-wrap items-center gap-3 py-2 text-sm">
          <span class="w-16 font-semibold" :class="tx.side === 'SELL' ? 'text-neon-purple' : 'text-neon-cyan'">
            {{ t(`portfolio.sides.${tx.side}`) }}</span>
          <span class="num w-20 font-bold">{{ tx.symbol }}</span>
          <span class="num">{{ formatQuantity(tx.quantity, settings.locale) }} × {{ formatMoney(tx.price, tx.currency, settings.locale) }}</span>
          <span class="text-xs text-ink-dim">{{ formatDateTime(tx.executedAt, settings.locale) }}</span>
          <button type="button" class="ml-auto inline-flex min-h-11 min-w-11 items-center justify-center text-ink-dim hover:text-down"
            :aria-label="t('common.delete')" @click="remove(tx.id)">
            <Trash2 :size="16" aria-hidden="true" />
          </button>
        </li>
      </ul>
    </GlassPanel>
  </div>
</template>
