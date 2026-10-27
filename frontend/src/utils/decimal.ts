/**
 * Decimal helpers. Prices arrive from the API as strings and are only ever handled with
 * decimal.js — never parsed into JS floating-point numbers for arithmetic (AGENT.MD §4.1).
 */
import Decimal from 'decimal.js'

Decimal.set({ precision: 34, rounding: Decimal.ROUND_HALF_EVEN })

export { Decimal }

export function dec(v: string | number | Decimal | null | undefined): Decimal | null {
  if (v === null || v === undefined || v === '') return null
  try {
    const d = new Decimal(v)
    return d.isFinite() ? d : null
  } catch {
    return null
  }
}

/** -1, 0 or 1 */
export function sign(v: string | Decimal | null | undefined): -1 | 0 | 1 {
  const d = v instanceof Decimal ? v : dec(v)
  if (!d || d.isZero()) return 0
  return d.isNegative() ? -1 : 1
}

/** Compares two decimal strings: -1, 0, 1 (null treated as equal). */
export function compare(a: string | null | undefined, b: string | null | undefined): -1 | 0 | 1 {
  const da = dec(a)
  const db = dec(b)
  if (!da || !db) return 0
  return da.comparedTo(db) as -1 | 0 | 1
}

/** Validates user input for a non-negative decimal with at most `fraction` digits. */
export function isValidDecimalInput(v: string, fraction = 6): boolean {
  return new RegExp(`^\\d{1,12}(\\.\\d{1,${fraction}})?$`).test(v.trim())
}
