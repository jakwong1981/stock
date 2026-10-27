import { defineStore } from 'pinia'
import { ref, shallowRef, triggerRef } from 'vue'
import type { Quote } from '@/api/types'
import { compare } from '@/utils/decimal'

/** UI refresh period for high-frequency ticks (AGENT.MD §4.3). */
export const FLUSH_INTERVAL_MS = 500

export interface TickState {
  dir: -1 | 0 | 1 // direction of the last price change
  seq: number // increments on every change, used to re-trigger flash animations
}

/**
 * Real-time quote store. Incoming ticks are buffered and applied in one batch every
 * 500 ms, so the DOM updates at most twice per second regardless of tick rate.
 */
export const useQuoteStore = defineStore('quotes', () => {
  const quotes = shallowRef<Record<string, Quote>>({})
  const ticks = shallowRef<Record<string, TickState>>({})
  const lastFlush = ref(0)

  let pending = new Map<string, Quote>()
  let timer: ReturnType<typeof setInterval> | null = null

  function ingest(q: Quote): void {
    const prev = pending.get(q.symbol)
    if (!prev || q.ts >= prev.ts) pending.set(q.symbol, q) // keep only the newest per symbol
    ensureTimer()
  }

  function ingestMany(list: Quote[]): void {
    list.forEach(ingest)
  }

  function ensureTimer(): void {
    if (!timer) timer = setInterval(flush, FLUSH_INTERVAL_MS)
  }

  function flush(): void {
    if (pending.size === 0) return
    const batch = pending
    pending = new Map()
    const nextQuotes = { ...quotes.value }
    const nextTicks = { ...ticks.value }
    for (const [sym, q] of batch) {
      const old = nextQuotes[sym]
      if (old && q.ts < old.ts) continue // stale
      const dir = old ? compare(q.price, old.price) : 0
      if (dir !== 0) nextTicks[sym] = { dir, seq: (nextTicks[sym]?.seq ?? 0) + 1 }
      nextQuotes[sym] = q
    }
    quotes.value = nextQuotes
    ticks.value = nextTicks
    triggerRef(quotes)
    lastFlush.value = Date.now()
  }

  function stop(): void {
    if (timer) clearInterval(timer)
    timer = null
  }

  return { quotes, ticks, lastFlush, ingest, ingestMany, flush, stop }
})
