/**
 * Locale-aware formatting via Intl. Values stay decimal strings; formatting uses
 * Intl.NumberFormat's string input support so no precision is lost for display.
 */
import { Decimal, dec } from './decimal'
import type { AppLocale } from '@/i18n/locales'

type NumInput = string | null | undefined

const cache = new Map<string, Intl.NumberFormat>()
function nf(locale: string, opts: Intl.NumberFormatOptions): Intl.NumberFormat {
  const key = locale + JSON.stringify(opts)
  let f = cache.get(key)
  if (!f) {
    f = new Intl.NumberFormat(locale, opts)
    cache.set(key, f)
  }
  return f
}

/** Intl.NumberFormat accepts numeric strings as exact decimals (ES2023 "Intl.NumberFormat v3"). */
function fmt(f: Intl.NumberFormat, v: string): string {
  return f.format(v as unknown as number)
}

export function formatPrice(v: NumInput, locale: AppLocale, digits = 2): string {
  const d = dec(v)
  if (!d) return '—'
  return fmt(nf(locale, { minimumFractionDigits: digits, maximumFractionDigits: Math.max(digits, 3) }), d.toFixed())
}

export function formatMoney(v: NumInput, currency: string, locale: AppLocale): string {
  const d = dec(v)
  if (!d) return '—'
  return fmt(nf(locale, { style: 'currency', currency, currencyDisplay: 'narrowSymbol' }),
    d.toFixed(2, Decimal.ROUND_HALF_UP))
}

export function formatSigned(v: NumInput, locale: AppLocale, digits = 2): string {
  const d = dec(v)
  if (!d) return '—'
  // Display rounding is half-up (away from zero), the convention users expect on quote screens
  return fmt(nf(locale, { minimumFractionDigits: digits, maximumFractionDigits: digits, signDisplay: 'exceptZero' }),
    d.toFixed(digits, Decimal.ROUND_HALF_UP))
}

export function formatPercent(v: NumInput, locale: AppLocale): string {
  const d = dec(v)
  if (!d) return '—'
  // input is already a percentage (e.g. "1.25" = 1.25 %)
  return `${formatSigned(d.toFixed(), locale, 2)}%`
}

/** Compact volume: en → 1.2M / 3.4B ; zh → 1.2萬 / 3.4億 (Intl handles CJK units natively). */
export function formatCompact(v: NumInput, locale: AppLocale): string {
  const d = dec(v)
  if (!d) return '—'
  return fmt(nf(locale, { notation: 'compact', maximumFractionDigits: 2 }), d.toFixed(0))
}

export function formatQuantity(v: NumInput, locale: AppLocale): string {
  const d = dec(v)
  if (!d) return '—'
  return fmt(nf(locale, { maximumFractionDigits: 6 }), d.toFixed())
}

export function formatDateTime(iso: string | number, locale: AppLocale, timeZone?: string): string {
  const date = typeof iso === 'number' ? new Date(iso) : new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(date)
}

export function formatTime(epochMs: number, locale: AppLocale, timeZone?: string): string {
  return new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone })
    .format(new Date(epochMs))
}

export function formatRelative(iso: string, locale: AppLocale, now = Date.now()): string {
  const diffSec = Math.round((new Date(iso).getTime() - now) / 1000)
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' })
  const abs = Math.abs(diffSec)
  if (abs < 60) return rtf.format(diffSec, 'second')
  if (abs < 3600) return rtf.format(Math.round(diffSec / 60), 'minute')
  if (abs < 86400) return rtf.format(Math.round(diffSec / 3600), 'hour')
  return rtf.format(Math.round(diffSec / 86400), 'day')
}
