import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { setI18nLocale } from '@/i18n'
import { defaultColorScheme, resolveLocale, type AppLocale } from '@/i18n/locales'
import { deviceLanguages, setHapticsEnabled, storage } from '@/platform/native'

export type ColorScheme = 'GREEN_UP' | 'RED_UP'

const K_LOCALE = 'csp.locale'
const K_COLORS = 'csp.colorScheme'
const K_CONTRAST = 'csp.highContrast'
const K_HAPTICS = 'csp.haptics'

export const useSettingsStore = defineStore('settings', () => {
  const locale = ref<AppLocale>('en-US')
  const colorScheme = ref<ColorScheme>('GREEN_UP')
  const highContrast = ref(false)
  const haptics = ref(true)
  const ready = ref(false)

  /** Detection order: saved choice → account preference → device language → en-US. */
  async function init(accountLocale: string | null = null): Promise<void> {
    const [savedLocale, savedColors, savedContrast, savedHaptics, device] = await Promise.all([
      storage.get(K_LOCALE),
      storage.get(K_COLORS),
      storage.get(K_CONTRAST),
      storage.get(K_HAPTICS),
      deviceLanguages(),
    ])
    locale.value = resolveLocale(savedLocale, accountLocale, device)
    colorScheme.value =
      savedColors === 'GREEN_UP' || savedColors === 'RED_UP' ? savedColors : defaultColorScheme(locale.value)
    highContrast.value = savedContrast === 'true'
    haptics.value = savedHaptics !== 'false'
    setHapticsEnabled(haptics.value)
    await setI18nLocale(locale.value)
    ready.value = true
  }

  async function setLocale(l: AppLocale): Promise<void> {
    locale.value = l
    await setI18nLocale(l) // switches instantly, no reload
    await storage.set(K_LOCALE, l)
  }

  async function setColorScheme(c: ColorScheme): Promise<void> {
    colorScheme.value = c
    await storage.set(K_COLORS, c)
  }

  watch(highContrast, (v) => {
    document.documentElement.classList.toggle('high-contrast', v)
    void storage.set(K_CONTRAST, String(v))
  })
  watch(haptics, (v) => {
    setHapticsEnabled(v)
    void storage.set(K_HAPTICS, String(v))
  })

  /** CSS colour classes for a price direction honouring the user's convention. */
  function directionClass(sign: -1 | 0 | 1): string {
    if (sign === 0) return 'text-flat'
    const up = sign > 0
    const green = colorScheme.value === 'GREEN_UP' ? up : !up
    return green ? 'text-up' : 'text-down'
  }

  function flashClass(sign: -1 | 0 | 1): string {
    if (sign === 0) return ''
    const up = sign > 0
    const green = colorScheme.value === 'GREEN_UP' ? up : !up
    return green ? 'animate-flash-up' : 'animate-flash-down'
  }

  return { locale, colorScheme, highContrast, haptics, ready, init, setLocale, setColorScheme, directionClass, flashClass }
})
