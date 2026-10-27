<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { X, Zap } from '@lucide/vue'
import { useNewsStore } from '@/stores/news'

/** Neon pop-up alerts for breaking news. Announced to screen readers via aria-live. */
const { t } = useI18n()
const news = useNewsStore()
</script>

<template>
  <div
    class="pointer-events-none fixed inset-x-0 top-[calc(env(safe-area-inset-top)+4.5rem)] z-50 flex flex-col
      items-center gap-2 px-3"
    aria-live="assertive"
  >
    <article
      v-for="n in news.breaking"
      :key="n.id"
      class="glass pointer-events-auto flex w-full max-w-lg items-start gap-3 rounded-xl border-neon-purple/70 p-3
        shadow-glow-purple"
    >
      <Zap :size="18" class="mt-0.5 shrink-0 text-neon-yellow" aria-hidden="true" />
      <div class="min-w-0 flex-1">
        <p class="text-xs font-bold uppercase tracking-wider text-neon-purple">{{ t('news.breaking') }}</p>
        <p class="text-sm font-semibold text-ink">{{ n.title }}</p>
      </div>
      <button
        type="button"
        class="-m-1 inline-flex min-h-11 min-w-11 items-center justify-center text-ink-dim hover:text-ink"
        :aria-label="t('common.close')"
        @click="news.dismissBreaking(n.id)"
      >
        <X :size="16" aria-hidden="true" />
      </button>
    </article>
  </div>
</template>
