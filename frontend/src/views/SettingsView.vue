<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import GlassPanel from '@/components/cyberpunk/GlassPanel.vue'
import GlowButton from '@/components/cyberpunk/GlowButton.vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import { authApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { usePortfolioStore } from '@/stores/portfolio'
import { useSettingsStore, type ColorScheme } from '@/stores/settings'

const { t } = useI18n()
const router = useRouter()
const settings = useSettingsStore()
const auth = useAuthStore()
const portfolio = usePortfolioStore()
const version = '0.1.0'
const SCHEMES: ColorScheme[] = ['GREEN_UP', 'RED_UP']

async function setScheme(c: ColorScheme) {
  await settings.setColorScheme(c)
  if (auth.isAuthenticated) await authApi.update({ colorScheme: c }).catch(() => undefined)
}

async function logout() {
  await auth.logout()
  portfolio.reset()
  void router.push('/')
}
</script>

<template>
  <div class="mx-auto grid max-w-2xl gap-3">
    <GlassPanel :title="t('settings.title')" glow="cyan">
      <div class="space-y-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <span class="text-sm">{{ t('settings.language') }}</span>
          <LanguageSwitcher />
        </div>

        <fieldset>
          <legend class="mb-2 text-sm">{{ t('settings.colorScheme') }}</legend>
          <div class="grid grid-cols-2 gap-2">
            <label v-for="c in SCHEMES" :key="c"
              class="flex min-h-11 cursor-pointer items-center justify-center gap-2 rounded-lg border text-sm"
              :class="settings.colorScheme === c ? 'border-neon-cyan text-neon-cyan' : 'border-line text-ink-dim'">
              <input type="radio" name="scheme" class="sr-only" :checked="settings.colorScheme === c" @change="setScheme(c)" />
              <span aria-hidden="true" class="h-2 w-2 rounded-full" :class="c === 'GREEN_UP' ? 'bg-up' : 'bg-down'" />
              {{ t(`settings.colorSchemes.${c}`) }}
            </label>
          </div>
        </fieldset>

        <label class="flex min-h-11 cursor-pointer items-center justify-between gap-3">
          <span>
            <span class="block text-sm">{{ t('settings.highContrast') }}</span>
            <span class="block text-xs text-ink-dim">{{ t('settings.highContrastHint') }}</span>
          </span>
          <input v-model="settings.highContrast" type="checkbox" role="switch" class="h-6 w-6 accent-[var(--color-neon-cyan)]" />
        </label>

        <label class="flex min-h-11 cursor-pointer items-center justify-between gap-3">
          <span class="text-sm">{{ t('settings.haptics') }}</span>
          <input v-model="settings.haptics" type="checkbox" role="switch" class="h-6 w-6 accent-[var(--color-neon-cyan)]" />
        </label>
      </div>
    </GlassPanel>

    <GlassPanel :title="t('settings.account')">
      <div v-if="auth.isAuthenticated" class="flex flex-wrap items-center justify-between gap-3">
        <span class="text-sm">{{ auth.user?.displayName || auth.user?.email }}</span>
        <GlowButton variant="ghost" @click="logout">{{ t('common.signOut') }}</GlowButton>
      </div>
      <RouterLink v-else to="/login"><GlowButton>{{ t('common.signIn') }}</GlowButton></RouterLink>
    </GlassPanel>

    <GlassPanel :title="t('settings.about')">
      <p class="text-sm text-ink-dim">{{ t('app.name') }} — {{ t('app.tagline') }}</p>
      <p class="text-xs text-ink-dim">{{ t('settings.version', { version }) }}</p>
      <p class="mt-2 text-xs text-ink-dim">{{ t('ai.disclaimer') }}</p>
    </GlassPanel>
  </div>
</template>
