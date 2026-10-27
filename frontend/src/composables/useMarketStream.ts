/**
 * Singleton WebSocket connection to /ws/quotes with:
 *  - exponential back-off + jitter reconnects (1 s → 30 s)
 *  - heartbeat: ping every 20 s, reconnect if nothing received for 45 s
 *  - immediate reconnect on network change (Wi-Fi ↔ 5G) and when the app returns to foreground
 *  - automatic re-subscription after reconnect
 */
import { ref } from 'vue'
import { wsUrl } from '@/api/http'
import type { Quote } from '@/api/types'
import { onAppStateChange, onNetworkChange } from '@/platform/native'
import { useQuoteStore } from '@/stores/quotes'

export type StreamStatus = 'connecting' | 'connected' | 'reconnecting' | 'offline'

const PING_MS = 20_000
const STALE_MS = 45_000
const MAX_BACKOFF_MS = 30_000

const status = ref<StreamStatus>('connecting')
const subscribed = new Set<string>()
let ws: WebSocket | null = null
let attempts = 0
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let pingTimer: ReturnType<typeof setInterval> | null = null
let lastMessageAt = 0
let started = false
let online = typeof navigator === 'undefined' ? true : navigator.onLine
let foreground = true

export function backoffDelay(attempt: number, random = Math.random): number {
  const base = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** attempt)
  return Math.round(base / 2 + random() * (base / 2)) // "equal jitter"
}

function send(msg: object): void {
  if (ws?.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg))
}

function clearTimers(): void {
  if (reconnectTimer) clearTimeout(reconnectTimer)
  if (pingTimer) clearInterval(pingTimer)
  reconnectTimer = null
  pingTimer = null
}

function teardown(): void {
  clearTimers()
  if (ws) {
    ws.onopen = ws.onmessage = ws.onclose = ws.onerror = null
    try {
      ws.close()
    } catch {
      /* already closed */
    }
  }
  ws = null
}

function connect(): void {
  teardown()
  if (!online) {
    status.value = 'offline'
    return
  }
  if (!foreground) return
  status.value = attempts === 0 ? 'connecting' : 'reconnecting'
  const store = useQuoteStore()
  const socket = new WebSocket(wsUrl('/ws/quotes'))
  ws = socket

  socket.onopen = () => {
    attempts = 0
    status.value = 'connected'
    lastMessageAt = Date.now()
    if (subscribed.size) send({ action: 'subscribe', symbols: [...subscribed] })
    pingTimer = setInterval(() => {
      if (Date.now() - lastMessageAt > STALE_MS) {
        scheduleReconnect() // half-open connection (common after network switches)
        return
      }
      send({ action: 'ping' })
    }, PING_MS)
  }

  socket.onmessage = (ev) => {
    lastMessageAt = Date.now()
    try {
      const msg = JSON.parse(ev.data as string)
      if (msg.type === 'quote') store.ingest(msg.data as Quote)
      else if (msg.type === 'snapshot') store.ingestMany(msg.data as Quote[])
    } catch {
      /* ignore malformed frame */
    }
  }

  socket.onclose = () => scheduleReconnect()
  socket.onerror = () => socket.close()
}

function scheduleReconnect(): void {
  teardown()
  if (!online) {
    status.value = 'offline'
    return
  }
  if (!foreground) return
  status.value = 'reconnecting'
  reconnectTimer = setTimeout(connect, backoffDelay(attempts++))
}

function start(): void {
  if (started) return
  started = true
  onNetworkChange((isOnline) => {
    online = isOnline
    attempts = 0
    if (isOnline) connect()
    else {
      teardown()
      status.value = 'offline'
    }
  })
  onAppStateChange((active) => {
    foreground = active
    if (active) {
      attempts = 0
      connect() // resume immediately; snapshot on subscribe refreshes stale prices
    } else {
      teardown() // save battery & data while in background
    }
  })
  connect()
}

export function useMarketStream() {
  start()
  return {
    status,
    subscribe(symbols: string[]): void {
      const fresh = symbols.filter((s) => !subscribed.has(s))
      fresh.forEach((s) => subscribed.add(s))
      if (fresh.length) send({ action: 'subscribe', symbols: fresh })
    },
    unsubscribe(symbols: string[]): void {
      symbols.forEach((s) => subscribed.delete(s))
      send({ action: 'unsubscribe', symbols })
    },
  }
}
