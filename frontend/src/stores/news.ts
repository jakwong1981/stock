import { defineStore } from 'pinia'
import { ref } from 'vue'
import { newsApi } from '@/api'
import type { NewsItem } from '@/api/types'

const MAX_ITEMS = 200

export const useNewsStore = defineStore('news', () => {
  const items = ref<NewsItem[]>([])
  const breaking = ref<NewsItem[]>([]) // queue of unseen breaking alerts
  const loading = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    try {
      items.value = await newsApi.list(undefined, 50)
    } finally {
      loading.value = false
    }
  }

  function push(n: NewsItem): void {
    if (items.value.some((x) => x.id === n.id)) return
    items.value = [n, ...items.value].slice(0, MAX_ITEMS)
    if (n.isBreaking) breaking.value = [...breaking.value, n].slice(-3)
  }

  function dismissBreaking(id: string): void {
    breaking.value = breaking.value.filter((n) => n.id !== id)
  }

  function forSymbol(symbol: string): NewsItem[] {
    return items.value.filter((n) => n.symbols.includes(symbol))
  }

  return { items, breaking, loading, load, push, dismissBreaking, forSymbol }
})
