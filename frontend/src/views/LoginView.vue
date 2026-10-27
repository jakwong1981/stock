<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import GlassPanel from '@/components/cyberpunk/GlassPanel.vue'
import GlowButton from '@/components/cyberpunk/GlowButton.vue'
import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'
import { useWatchlistStore } from '@/stores/watchlist'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const settings = useSettingsStore()
const watchlist = useWatchlistStore()

const mode = ref<'login' | 'register'>('login')
const email = ref('')
const password = ref('')
const displayName = ref('')
const error = ref('')
const busy = ref(false)
const valid = computed(() => /\S+@\S+\.\S+/.test(email.value) && password.value.length >= 8)

async function submit() {
  if (!valid.value) return
  busy.value = true
  error.value = ''
  try {
    if (mode.value === 'login') await auth.login(email.value, password.value)
    else await auth.register(email.value, password.value, displayName.value, settings.locale)
    await watchlist.load()
    const next = typeof route.query.next === 'string' && route.query.next.startsWith('/') ? route.query.next : '/'
    void router.replace(next)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-sm">
    <GlassPanel :title="mode === 'login' ? t('auth.loginTitle') : t('auth.registerTitle')" glow="cyan">
      <form class="space-y-3" novalidate @submit.prevent="submit">
        <label class="block text-xs text-ink-dim">{{ t('auth.email') }}
          <input v-model="email" type="email" autocomplete="email" required
            class="mt-1 min-h-11 w-full rounded-lg border border-line bg-panel px-3 text-sm text-ink" />
        </label>
        <label v-if="mode === 'register'" class="block text-xs text-ink-dim">{{ t('auth.displayName') }}
          <input v-model="displayName" autocomplete="nickname" maxlength="80"
            class="mt-1 min-h-11 w-full rounded-lg border border-line bg-panel px-3 text-sm text-ink" />
        </label>
        <label class="block text-xs text-ink-dim">{{ t('auth.password') }}
          <input v-model="password" type="password" required minlength="8"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
            aria-describedby="pw-hint"
            class="mt-1 min-h-11 w-full rounded-lg border border-line bg-panel px-3 text-sm text-ink" />
          <span id="pw-hint" class="mt-1 block">{{ t('auth.passwordHint') }}</span>
        </label>
        <p v-if="error" role="alert" class="text-sm text-down">{{ error }}</p>
        <GlowButton type="submit" class="w-full" :disabled="!valid || busy">
          {{ mode === 'login' ? t('common.signIn') : t('common.signUp') }}
        </GlowButton>
      </form>
      <p class="mt-4 text-center text-xs text-ink-dim">
        {{ mode === 'login' ? t('auth.noAccount') : t('auth.haveAccount') }}
        <button type="button" class="min-h-11 px-1 text-neon-cyan hover:underline"
          @click="mode = mode === 'login' ? 'register' : 'login'">
          {{ mode === 'login' ? t('common.signUp') : t('common.signIn') }}
        </button>
      </p>
    </GlassPanel>
  </div>
</template>
