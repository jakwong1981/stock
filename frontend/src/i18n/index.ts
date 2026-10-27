import type { Ref } from 'vue'
import { createI18n } from 'vue-i18n'
import enUS from '@/locales/en-US.json'
import { DEFAULT_LOCALE, type AppLocale } from './locales'

type Messages = typeof enUS

// en-US is bundled as the ultimate fallback; other locales are lazy-loaded to keep the
// mobile first-load bundle small.
export const i18n = createI18n<[Messages], AppLocale>({
  legacy: false,
  locale: DEFAULT_LOCALE,
  fallbackLocale: {
    'zh-HK': ['zh-TW', 'en-US'],
    'zh-TW': ['en-US'],
    'zh-CN': ['en-US'],
    default: ['en-US'],
  },
  messages: { 'en-US': enUS } as Record<AppLocale, Messages>,
  missingWarn: false,
  fallbackWarn: false,
})

const loaders: Record<AppLocale, () => Promise<{ default: Messages }>> = {
  'en-US': async () => ({ default: enUS }),
  'zh-HK': () => import('@/locales/zh-HK.json'),
  'zh-TW': () => import('@/locales/zh-TW.json'),
  'zh-CN': () => import('@/locales/zh-CN.json'),
}

const loaded = new Set<AppLocale>(['en-US'])

export async function setI18nLocale(locale: AppLocale): Promise<void> {
  // zh-HK falls back to zh-TW, so load it too
  const needed: AppLocale[] = locale === 'zh-HK' ? ['zh-TW', 'zh-HK'] : [locale]
  for (const l of needed) {
    if (!loaded.has(l)) {
      const mod = await loaders[l]()
      i18n.global.setLocaleMessage(l, mod.default)
      loaded.add(l)
    }
  }
  localeRef().value = locale
  document.documentElement.lang = locale
}

// Composition-mode global locale is a Ref; the generic typings expose it as a plain union.
function localeRef(): Ref<AppLocale> {
  return i18n.global.locale as unknown as Ref<AppLocale>
}

export function currentLocale(): AppLocale {
  return localeRef().value
}
