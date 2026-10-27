<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, type AppLocale } from '@/i18n/locales'
import { useSettingsStore } from '@/stores/settings'
import { useAuthStore } from '@/stores/auth'
import { useInstrumentStore } from '@/stores/instruments'
import { authApi } from '@/api'

const { t } = useI18n()
const settings = useSettingsStore()
const auth = useAuthStore()
const instruments = useInstrumentStore()

async function change(e: Event) {
  const l = (e.target as HTMLSelectElement).value as AppLocale
  await settings.setLocale(l)
  await instruments.load().catch(() => undefined) // localised company names
  if (auth.isAuthenticated) await authApi.update({ preferredLocale: l }).catch(() => undefined)
}
</script>

<template>
  <label class="flex items-center gap-2 text-sm">
    <span class="sr-only">{{ t('settings.language') }}</span>
    <select
      :value="settings.locale"
      class="min-h-11 rounded-lg border border-line bg-panel px-3 text-ink focus:border-neon-cyan"
      @change="change"
    >
      <option v-for="l in SUPPORTED_LOCALES" :key="l" :value="l" :lang="l">{{ t(`lang.${l}`) }}</option>
    </select>
  </label>
</template>
