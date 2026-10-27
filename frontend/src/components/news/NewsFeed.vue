<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ExternalLink } from '@lucide/vue'
import type { NewsItem } from '@/api/types'
import { useSettingsStore } from '@/stores/settings'
import { formatRelative } from '@/utils/format'

defineProps<{ items: NewsItem[]; compact?: boolean }>()
const { t } = useI18n()
const settings = useSettingsStore()

// Sentiment tags use colour AND text so meaning is not conveyed by colour alone
function tagClass(label: NewsItem['sentimentLabel']): string {
  if (label === 'BULLISH') return settings.directionClass(1) + ' border-current'
  if (label === 'BEARISH') return settings.directionClass(-1) + ' border-current'
  if (label === 'NEUTRAL') return 'text-neon-yellow border-neon-yellow/60'
  return 'text-ink-dim border-line'
}
</script>

<template>
  <p v-if="!items.length" class="text-sm text-ink-dim">{{ t('news.empty') }}</p>
  <ul v-else class="divide-y divide-line/70">
    <li v-for="n in items" :key="n.id" class="py-3">
      <div class="mb-1 flex flex-wrap items-center gap-2 text-xs">
        <span v-if="n.isBreaking" class="rounded bg-neon-purple/20 px-1.5 py-0.5 font-bold text-neon-purple">
          {{ t('news.breaking') }}
        </span>
        <span class="rounded border px-1.5 py-0.5 font-semibold" :class="tagClass(n.sentimentLabel)">
          {{ t(`news.sentiment.${n.sentimentLabel}`) }}
          <span v-if="n.sentimentScore" class="num">{{ n.sentimentScore }}</span>
        </span>
        <span v-for="s in n.symbols" :key="s" class="num text-neon-yellow">{{ s }}</span>
        <span class="text-ink-dim">{{ n.source }} · <time :datetime="n.publishedAt">{{
          formatRelative(n.publishedAt, settings.locale) }}</time></span>
      </div>
      <h3 class="text-sm font-semibold leading-snug text-ink">{{ n.title }}</h3>
      <p v-if="!compact && n.summary" class="mt-1 text-sm text-ink-dim">
        <span class="sr-only">{{ t('news.aiSummary') }}: </span>{{ n.summary }}
      </p>
      <a
        v-if="!compact"
        :href="n.url"
        target="_blank"
        rel="noopener noreferrer nofollow"
        class="mt-1 inline-flex min-h-9 items-center gap-1 text-xs text-neon-cyan hover:underline"
      >{{ t('news.readMore') }} <ExternalLink :size="12" aria-hidden="true" /></a>
    </li>
  </ul>
</template>
