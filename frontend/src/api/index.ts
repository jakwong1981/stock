import { apiUrl, authHeaders, http } from './http'
import type {
  Candle,
  ChatMessage,
  Instrument,
  MarketStatus,
  Me,
  NewsItem,
  PortfolioResult,
  Quote,
  Side,
  TokenResponse,
  Transaction,
} from './types'

export const marketApi = {
  instruments: (marquee = false) => http.get<Instrument[]>(`/api/instruments${marquee ? '?marquee=true' : ''}`),
  instrument: (symbol: string) => http.get<Instrument>(`/api/instruments/${encodeURIComponent(symbol)}`),
  search: (q: string, signal?: AbortSignal) =>
    http.get<Instrument[]>(`/api/instruments/search?q=${encodeURIComponent(q)}`, signal),
  quotes: (symbols: string[]) => http.get<Quote[]>(`/api/quotes?symbols=${encodeURIComponent(symbols.join(','))}`),
  candles: (symbol: string, interval: string, limit = 300) =>
    http.get<Candle[]>(`/api/candles?symbol=${encodeURIComponent(symbol)}&interval=${interval}&limit=${limit}`),
  status: () => http.get<MarketStatus[]>('/api/market/status'),
  provider: () => http.get<{ provider: string }>('/api/market/provider'),
}

export const authApi = {
  login: (email: string, password: string) => http.post<TokenResponse>('/api/auth/login', { email, password }),
  register: (email: string, password: string, displayName: string, locale: string) =>
    http.post<TokenResponse>('/api/auth/register', { email, password, displayName, locale }),
  me: () => http.get<Me>('/api/me'),
  update: (p: Partial<Pick<Me, 'displayName' | 'preferredLocale' | 'colorScheme'>>) => http.patch<Me>('/api/me', p),
}

export const watchlistApi = {
  get: () => http.get<{ symbols: string[] }>('/api/watchlist'),
  put: (symbols: string[]) => http.put<{ symbols: string[] }>('/api/watchlist', { symbols }),
}

export const portfolioApi = {
  transactions: () => http.get<Transaction[]>('/api/portfolio/transactions'),
  positions: () => http.get<PortfolioResult>('/api/portfolio/positions'),
  add: (t: { symbol: string; side: Side; quantity: string; price: string; fee: string; executedAt?: string }) =>
    http.post<Transaction>('/api/portfolio/transactions', t),
  remove: (id: string) => http.del<void>(`/api/portfolio/transactions/${encodeURIComponent(id)}`),
}

export const newsApi = {
  list: (symbol?: string, limit = 30) =>
    http.get<NewsItem[]>(`/ai/news?limit=${limit}${symbol ? `&symbol=${encodeURIComponent(symbol)}` : ''}`),
  streamUrl: (locale: string) => apiUrl(`/ai/news/stream?locale=${encodeURIComponent(locale)}`),
}

export type ChatEvent =
  | { type: 'delta'; text: string }
  | { type: 'tool'; name: string }
  | { type: 'done' }
  | { type: 'error'; code: string }

/** Streams the AI analyst's answer (Server-Sent Events over a POST fetch). */
export async function* streamChat(messages: ChatMessage[], locale: string, signal: AbortSignal):
  AsyncGenerator<ChatEvent> {
  const res = await fetch(apiUrl('/ai/chat'), {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ messages, locale }),
    signal,
  })
  if (!res.ok || !res.body) {
    let code = res.status === 401 ? 'UNAUTHORIZED' : res.status === 429 ? 'RATE_LIMITED' : 'LLM_UNAVAILABLE'
    try {
      const j = await res.json()
      if (typeof j?.detail?.code === 'string') code = j.detail.code
    } catch {
      /* ignore */
    }
    yield { type: 'error', code }
    return
  }
  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader()
  let buf = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buf += value
    let idx: number
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const block = buf.slice(0, idx)
      buf = buf.slice(idx + 2)
      const ev = parseSseBlock(block)
      if (ev) yield ev
    }
  }
}

export function parseSseBlock(block: string): ChatEvent | null {
  let event = 'message'
  let data = ''
  for (const line of block.split('\n')) {
    if (line.startsWith('event: ')) event = line.slice(7).trim()
    else if (line.startsWith('data: ')) data += line.slice(6)
  }
  if (!data) return null
  try {
    const d = JSON.parse(data)
    if (event === 'delta') return { type: 'delta', text: String(d.text ?? '') }
    if (event === 'tool') return { type: 'tool', name: String(d.name ?? '') }
    if (event === 'done') return { type: 'done' }
    if (event === 'error') return { type: 'error', code: String(d.code ?? 'UNKNOWN') }
  } catch {
    /* ignore malformed */
  }
  return null
}
