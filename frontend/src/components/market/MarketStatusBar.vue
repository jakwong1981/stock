<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { marketApi } from '@/api'
import type { MarketStatus } from '@/api/types'

const { t } = useI18n()
const status = ref<MarketStatus[]>([])
let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  status.value = await marketApi.status().catch(() => status.value)
}

onMounted(() => {
  void load()
  timer = setInterval(load, 60_000)
})
onBeforeUnmount(() => timer && clearInterval(timer))
</script>

<template>
  <ul :aria-label="t('market.marketStatus')" class="flex flex-wrap gap-2 text-xs">
    <li
      v-for="s in status"
      :key="s.exchange"
      class="flex items-center gap-1.5 rounded-full border border-line px-2.5 py-1"
    >
      <span
        aria-hidden="true"
        class="h-1.5 w-1.5 rounded-full"
        :class="s.session === 'REGULAR' ? 'bg-up' : s.session === 'CLOSED' ? 'bg-ink-dim' : 'bg-neon-yellow'"
      />
      <span class="font-semibold text-ink">{{ s.exchange }}</span>
      <span class="text-ink-dim">{{ t(`market.session.${s.session}`) }}</span>
    </li>
  </ul>
</template>
