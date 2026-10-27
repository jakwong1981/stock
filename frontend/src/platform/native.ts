/**
 * Thin wrappers over Capacitor plugins with web fallbacks (AGENT.MD §4.7), so the same code
 * runs on desktop web, mobile web/PWA, iOS and Android.
 */
import { Capacitor } from '@capacitor/core'
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics'
import { Network } from '@capacitor/network'
import { App } from '@capacitor/app'
import { Preferences } from '@capacitor/preferences'
import { Device } from '@capacitor/device'

export const isNative = (): boolean => Capacitor.isNativePlatform()
export const platform = (): string => Capacitor.getPlatform() // 'ios' | 'android' | 'web'

let hapticsEnabled = true
export function setHapticsEnabled(v: boolean): void {
  hapticsEnabled = v
}

export async function hapticTap(): Promise<void> {
  if (!hapticsEnabled) return
  if (isNative()) {
    await Haptics.impact({ style: ImpactStyle.Light }).catch(() => undefined)
  } else if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
    navigator.vibrate?.(10) // silently ignored by browsers without support (e.g. iOS Safari)
  }
}

export async function hapticAlert(): Promise<void> {
  if (!hapticsEnabled) return
  if (isNative()) {
    await Haptics.notification({ type: NotificationType.Warning }).catch(() => undefined)
  } else {
    navigator.vibrate?.([30, 40, 30])
  }
}

/** Calls `cb(online)` whenever connectivity changes (Wi-Fi ↔ 5G, airplane mode…). */
export function onNetworkChange(cb: (online: boolean) => void): () => void {
  if (isNative()) {
    const handle = Network.addListener('networkStatusChange', (s) => cb(s.connected))
    return () => void handle.then((h) => h.remove())
  }
  const on = () => cb(true)
  const off = () => cb(false)
  window.addEventListener('online', on)
  window.addEventListener('offline', off)
  return () => {
    window.removeEventListener('online', on)
    window.removeEventListener('offline', off)
  }
}

/** Calls `cb(active)` when the app moves between foreground and background. */
export function onAppStateChange(cb: (active: boolean) => void): () => void {
  if (isNative()) {
    const handle = App.addListener('appStateChange', (s) => cb(s.isActive))
    return () => void handle.then((h) => h.remove())
  }
  const handler = () => cb(document.visibilityState === 'visible')
  document.addEventListener('visibilitychange', handler)
  return () => document.removeEventListener('visibilitychange', handler)
}

/** Key/value storage: Capacitor Preferences on native, localStorage on web. */
export const storage = {
  async get(key: string): Promise<string | null> {
    if (isNative()) return (await Preferences.get({ key })).value
    try {
      return localStorage.getItem(key)
    } catch {
      return null
    }
  },
  async set(key: string, value: string): Promise<void> {
    if (isNative()) return Preferences.set({ key, value })
    try {
      localStorage.setItem(key, value)
    } catch {
      /* private mode / quota: ignore */
    }
  },
  async remove(key: string): Promise<void> {
    if (isNative()) return Preferences.remove({ key })
    try {
      localStorage.removeItem(key)
    } catch {
      /* ignore */
    }
  },
}

/** Device language tags in preference order (e.g. ['zh-Hant-HK', 'en-US']). */
export async function deviceLanguages(): Promise<string[]> {
  if (isNative()) {
    try {
      const { value } = await Device.getLanguageTag()
      return [value]
    } catch {
      return []
    }
  }
  return typeof navigator !== 'undefined' ? [...(navigator.languages ?? [navigator.language])] : []
}
