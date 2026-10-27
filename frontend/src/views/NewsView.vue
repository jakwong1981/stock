<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import GlassPanel from '@/components/cyberpunk/GlassPanel.vue'
import NewsFeed from '@/components/news/NewsFeed.vue'
import { useNewsStore } from '@/stores/news'
import type { SentimentLabel } from '@/api/types'

const { t } = useI18n()
const news = useNewsStore()
const filter = ref<SentimentLabel | 'ALL'>('ALL')
const FILTERS: (SentimentLabel | 'ALL')[] = ['ALL', 'BULLISH', 'BEARISH', 'NEUTRAL']
const items = computed(() => (filter.value === 'ALL' ? news.items : news.items.filter((n) => n.sentimentLabel === filter.value)))
</script>

<template>
  <GlassPanel :title="t('news.title')" glow="purple">
    <template #actions>
      <div role="group" class="flex gap-1">
        <button
          v-for="f in FILTERS"
          :key="f"
          type="button"
          class="min-h-9 rounded-md border px-2 text-xs"
          :class="f === filter ? 'border-neon-cyan text-neon-cyan' : 'border-line text-ink-dim'"
          :aria-pressed="f === filter"
          @click="filter = f"
        >{{ f === 'ALL' ? t('common.viewAll') : t(`news.sentiment.${f}`) }}</button>
      </div>
    </template>
    <p v-if="news.loading" class="text-sm text-ink-dim">{{ t('common.loading') }}</p>
    <NewsFeed v-else :items="items" />
  </GlassPanel>
</template>
