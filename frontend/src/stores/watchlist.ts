import { defineStore } from 'pinia'
import { ref } from 'vue'
import { watchlistApi } from '@/api'
import { storage } from '@/platform/native'
import { useAuthStore } from './auth'

const K_WATCHLIST = 'csp.watchlist'
const DEFAULT_WATCHLIST = ['AAPL', 'NVDA', 'TSLA', '0700.HK', '9988.HK']

/** Guests keep the watchlist on-device; signed-in users sync it to their account. */
export const useWatchlistStore = defineStore('watchlist', () => {
  const symbols = ref<string[]>([])

  async function load(): Promise<void> {
    const auth = useAuthStore()
    if (auth.isAuthenticated) {
      try {
        const remote = (await watchlistApi.get()).symbols
        if (remote.length) {
          symbols.value = remote
          return
        }
      } catch {
        /* fall back to local */
      }
    }
    const local = await storage.get(K_WATCHLIST)
    symbols.value = local ? (JSON.parse(local) as string[]) : [...DEFAULT_WATCHLIST]
  }

  async function persist(): Promise<void> {
    await storage.set(K_WATCHLIST, JSON.stringify(symbols.value))
    if (useAuthStore().isAuthenticated) {
      symbols.value = (await watchlistApi.put(symbols.value)).symbols
    }
  }

  function has(s: string): boolean {
    return symbols.value.includes(s)
  }

  async function toggle(s: string): Promise<void> {
    symbols.value = has(s) ? symbols.value.filter((x) => x !== s) : [...symbols.value, s]
    await persist()
  }

  return { symbols, load, has, toggle }
})
