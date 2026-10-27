/**
 * Subscribes to the AI service's SSE news stream. EventSource reconnects automatically;
 * the stream is re-opened when the language changes so summaries arrive in that language.
 */
import { watch } from 'vue'
import { newsApi } from '@/api'
import type { NewsItem } from '@/api/types'
import { hapticAlert } from '@/platform/native'
import { useNewsStore } from '@/stores/news'
import { useSettingsStore } from '@/stores/settings'

let source: EventSource | null = null
let started = false

export function useNewsStream(): void {
  if (started) return
  started = true
  const news = useNewsStore()
  const settings = useSettingsStore()

  const open = () => {
    source?.close()
    if (typeof EventSource === 'undefined') return
    source = new EventSource(newsApi.streamUrl(settings.locale))
    source.addEventListener('news', (ev) => {
      try {
        const item = JSON.parse((ev as MessageEvent).data) as NewsItem
        news.push(item)
        if (item.isBreaking) void hapticAlert()
      } catch {
        /* ignore */
      }
    })
  }

  watch(
    () => settings.locale,
    async () => {
      await news.load().catch(() => undefined) // reload list in the new language
      open()
    },
    { immediate: true },
  )
}
