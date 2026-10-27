<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { StreamStatus } from '@/composables/useMarketStream'

defineProps<{ status: StreamStatus }>()
const { t } = useI18n()
</script>

<template>
  <span class="inline-flex items-center gap-1.5 text-xs text-ink-dim" role="status" aria-live="polite">
    <span
      aria-hidden="true"
      class="h-2 w-2 rounded-full"
      :class="{
        'bg-up shadow-[0_0_8px_var(--color-up)]': status === 'connected',
        'animate-pulse bg-neon-yellow': status === 'connecting' || status === 'reconnecting',
        'bg-down': status === 'offline',
      }"
    />
    {{ t(`stream.${status}`) }}
  </span>
</template>
