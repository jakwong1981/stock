export const SUPPORTED_LOCALES = ['zh-HK', 'zh-TW', 'zh-CN', 'en-US'] as const
export type AppLocale = (typeof SUPPORTED_LOCALES)[number]
export const DEFAULT_LOCALE: AppLocale = 'en-US'

export function isAppLocale(v: unknown): v is AppLocale {
  return typeof v === 'string' && (SUPPORTED_LOCALES as readonly string[]).includes(v)
}

/**
 * Maps a BCP-47 tag to a supported locale.
 * zh-MO → zh-HK, zh-SG → zh-CN, generic zh-Hant → zh-HK, generic zh / zh-Hans → zh-CN.
 */
export function mapLocale(tag: string | null | undefined): AppLocale | null {
  if (!tag) return null
  const t = tag.trim().toLowerCase().replace(/_/g, '-')
  if (t === 'zh-tw' || t.startsWith('zh-hant-tw')) return 'zh-TW'
  if (t === 'zh-hk' || t === 'zh-mo' || t.startsWith('zh-hant')) return 'zh-HK'
  if (t === 'zh' || t === 'zh-cn' || t === 'zh-sg' || t.startsWith('zh-hans')) return 'zh-CN'
  if (t.startsWith('en')) return 'en-US'
  return null
}

/** Picks the first supported locale: saved choice → account preference → device languages → en-US. */
export function resolveLocale(saved: string | null, account: string | null, device: string[]): AppLocale {
  if (isAppLocale(saved)) return saved
  if (isAppLocale(account)) return account
  for (const tag of device) {
    const m = mapLocale(tag)
    if (m) return m
  }
  return DEFAULT_LOCALE
}

/** Default price colour convention per region (user can override in settings). */
export function defaultColorScheme(locale: AppLocale): 'GREEN_UP' | 'RED_UP' {
  return locale === 'zh-CN' || locale === 'zh-TW' ? 'RED_UP' : 'GREEN_UP'
}
