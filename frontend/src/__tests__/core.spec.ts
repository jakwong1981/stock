import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import enUS from '@/locales/en-US.json'
import { defaultColorScheme, mapLocale, resolveLocale } from '@/i18n/locales'
import { formatCompact, formatPercent, formatPrice, formatSigned } from '@/utils/format'
import { compare, dec, isValidDecimalInput, sign } from '@/utils/decimal'
import { FLUSH_INTERVAL_MS, useQuoteStore } from '@/stores/quotes'
import { parseSseBlock } from '@/api'
import { backoffDelay } from '@/composables/useMarketStream'
import type { Quote } from '@/api/types'

describe('locale detection', () => {
  it('maps regional tags', () => {
    expect(mapLocale('zh-Hant-HK')).toBe('zh-HK')
    expect(mapLocale('zh-MO')).toBe('zh-HK')
    expect(mapLocale('zh-Hant')).toBe('zh-HK')
    expect(mapLocale('zh-Hant-TW')).toBe('zh-TW')
    expect(mapLocale('zh_TW')).toBe('zh-TW')
    expect(mapLocale('zh-Hans-CN')).toBe('zh-CN')
    expect(mapLocale('zh-SG')).toBe('zh-CN')
    expect(mapLocale('en-GB')).toBe('en-US')
    expect(mapLocale('ja-JP')).toBeNull()
  })

  it('resolves saved → account → device → default', () => {
    expect(resolveLocale('zh-TW', 'zh-CN', ['en-US'])).toBe('zh-TW')
    expect(resolveLocale(null, 'zh-CN', ['en-US'])).toBe('zh-CN')
    expect(resolveLocale(null, null, ['fr-FR', 'zh-HK'])).toBe('zh-HK')
    expect(resolveLocale('xx', null, ['fr-FR'])).toBe('en-US')
  })

  it('defaults price colours per region', () => {
    expect(defaultColorScheme('zh-CN')).toBe('RED_UP')
    expect(defaultColorScheme('zh-TW')).toBe('RED_UP')
    expect(defaultColorScheme('zh-HK')).toBe('GREEN_UP')
    expect(defaultColorScheme('en-US')).toBe('GREEN_UP')
  })
})

describe('decimal & formatting', () => {
  it('never uses binary floating point for arithmetic', () => {
    expect(dec('0.1')!.plus('0.2').toString()).toBe('0.3')
    expect(compare('100.10', '100.1')).toBe(0)
    expect(sign('-0.01')).toBe(-1)
    expect(sign('0')).toBe(0)
  })

  it('formats exact decimal strings beyond double precision', () => {
    expect(formatPrice('12345678901234.56', 'en-US')).toBe('12,345,678,901,234.56')
  })

  it('formats per locale', () => {
    expect(formatPrice('1234.5', 'en-US')).toBe('1,234.50')
    expect(formatSigned('1.5', 'en-US')).toBe('+1.50')
    expect(formatPercent('-2.345', 'en-US')).toBe('-2.35%')
    expect(formatCompact('12500000', 'en-US')).toBe('12.5M')
    expect(formatCompact('12500000', 'zh-CN')).toBe('1250万')
    expect(formatCompact('12500000', 'zh-HK')).toMatch(/1250萬|12.5M/)
    expect(formatPrice(null, 'en-US')).toBe('—')
  })

  it('validates decimal input', () => {
    expect(isValidDecimalInput('10.123456')).toBe(true)
    expect(isValidDecimalInput('10.1234567')).toBe(false)
    expect(isValidDecimalInput('-1')).toBe(false)
    expect(isValidDecimalInput('1e5')).toBe(false)
  })
})

describe('quote store throttling', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })
  afterEach(() => vi.useRealTimers())

  const q = (price: string, ts: number): Quote => ({
    symbol: 'AAPL', price, prevClose: '100', open: '100', high: price, low: price, change: '0',
    changePercent: '0', volume: '0', lastSize: '0', ts,
  })

  it('batches ticks and applies only the newest per symbol every 500 ms', () => {
    const store = useQuoteStore()
    store.ingest(q('100.00', 1))
    vi.advanceTimersByTime(FLUSH_INTERVAL_MS)
    expect(store.quotes.AAPL.price).toBe('100.00')

    for (let i = 0; i < 50; i++) store.ingest(q(String(101 + i), 10 + i))
    store.ingest(q('1.00', 5)) // out-of-order stale tick
    expect(store.quotes.AAPL.price).toBe('100.00') // nothing applied until next flush
    vi.advanceTimersByTime(FLUSH_INTERVAL_MS)
    expect(store.quotes.AAPL.price).toBe('150')
    expect(store.ticks.AAPL).toEqual({ dir: 1, seq: 1 })
    store.stop()
  })
})

describe('streaming helpers', () => {
  it('parses SSE chat events', () => {
    expect(parseSseBlock('event: delta\ndata: {"text":"Hi"}')).toEqual({ type: 'delta', text: 'Hi' })
    expect(parseSseBlock('event: error\ndata: {"code":"RATE_LIMITED"}')).toEqual({ type: 'error', code: 'RATE_LIMITED' })
    expect(parseSseBlock(': keep-alive')).toBeNull()
  })

  it('backs off exponentially with a 30 s cap', () => {
    expect(backoffDelay(0, () => 0)).toBe(500)
    expect(backoffDelay(0, () => 1)).toBe(1000)
    expect(backoffDelay(3, () => 1)).toBe(8000)
    expect(backoffDelay(20, () => 1)).toBe(30000)
  })
})

describe('i18n coverage', () => {
  function files(dir: string): string[] {
    return readdirSync(dir).flatMap((f) => {
      const p = join(dir, f)
      return statSync(p).isDirectory() ? files(p) : /\.(vue|ts)$/.test(p) && !p.includes('__tests__') ? [p] : []
    })
  }
  function has(obj: unknown, path: string): boolean {
    return path.split('.').reduce<unknown>((o, k) => (o && typeof o === 'object' ? (o as Record<string, unknown>)[k] : undefined), obj) !== undefined
  }

  it('every static t() key used in the source exists in en-US', () => {
    const missing: string[] = []
    for (const f of files(join(__dirname, '..'))) {
      for (const m of readFileSync(f, 'utf8').matchAll(/\bt\(\s*'([a-zA-Z0-9_.]+)'/g)) {
        if (!has(enUS, m[1])) missing.push(`${f}: ${m[1]}`)
      }
    }
    expect(missing).toEqual([])
  })
})
