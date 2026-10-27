<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Send, Square } from '@lucide/vue'
import GlassPanel from '@/components/cyberpunk/GlassPanel.vue'
import GlowButton from '@/components/cyberpunk/GlowButton.vue'
import { streamChat } from '@/api'
import type { ChatMessage } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'

const { t } = useI18n()
const auth = useAuthStore()
const settings = useSettingsStore()

const messages = ref<ChatMessage[]>([])
const input = ref('')
const streaming = ref(false)
const toolInUse = ref<string | null>(null)
const error = ref('')
const log = ref<HTMLElement | null>(null)
let ctrl: AbortController | null = null

async function scroll() {
  await nextTick()
  log.value?.scrollTo({ top: log.value.scrollHeight })
}

async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) return
  error.value = ''
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  const answer: ChatMessage = { role: 'assistant', content: '' }
  messages.value.push(answer)
  const idx = messages.value.length - 1
  streaming.value = true
  ctrl = new AbortController()
  try {
    for await (const ev of streamChat(messages.value.slice(0, -1), settings.locale, ctrl.signal)) {
      if (ev.type === 'delta') {
        toolInUse.value = null
        messages.value[idx].content += ev.text
        void scroll()
      } else if (ev.type === 'tool') toolInUse.value = ev.name
      else if (ev.type === 'error') error.value = t(`errors.${ev.code}`)
    }
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = t('errors.NETWORK')
  } finally {
    streaming.value = false
    toolInUse.value = null
    if (!messages.value[idx].content) messages.value.splice(idx, 1)
  }
}

function stop() {
  ctrl?.abort()
}
</script>

<template>
  <div class="mx-auto flex h-[calc(100dvh-14rem)] max-w-3xl flex-col lg:h-[calc(100dvh-11rem)]">
    <GlassPanel :title="t('ai.title')" glow="purple" class="flex min-h-0 flex-1 flex-col">
      <template v-if="!auth.isAuthenticated">
        <p class="mb-4 text-sm text-ink-dim">{{ t('ai.signInRequired') }}</p>
        <RouterLink to="/login?next=/ai"><GlowButton>{{ t('common.signIn') }}</GlowButton></RouterLink>
      </template>
      <template v-else>
        <div ref="log" class="min-h-0 flex-1 space-y-3 overflow-y-auto pr-1" aria-live="polite">
          <p v-if="!messages.length" class="text-sm text-ink-dim">{{ t('ai.intro') }}</p>
          <div
            v-for="(m, i) in messages"
            :key="i"
            class="max-w-[85%] whitespace-pre-wrap rounded-xl px-3 py-2 text-sm leading-relaxed"
            :class="m.role === 'user' ? 'ml-auto bg-neon-cyan/10 text-ink' : 'border border-neon-purple/40 bg-panel text-ink'"
          >{{ m.content }}</div>
          <p v-if="toolInUse" class="text-xs text-neon-yellow">
            {{ t('ai.usingTool', { tool: t(`ai.tools.${toolInUse}`) }) }}
          </p>
          <p v-else-if="streaming" class="animate-pulse text-xs text-ink-dim">{{ t('ai.thinking') }}</p>
          <p v-if="error" role="alert" class="text-sm text-down">{{ error }}</p>
        </div>
        <form class="mt-3 flex gap-2" @submit.prevent="send">
          <label class="sr-only" for="ai-input">{{ t('ai.placeholder') }}</label>
          <input
            id="ai-input"
            v-model="input"
            :placeholder="t('ai.placeholder')"
            maxlength="4000"
            class="min-h-11 flex-1 rounded-lg border border-line bg-panel px-3 text-sm text-ink placeholder:text-ink-dim"
          />
          <GlowButton v-if="streaming" variant="danger" @click="stop">
            <Square :size="14" aria-hidden="true" />{{ t('ai.stop') }}
          </GlowButton>
          <GlowButton v-else type="submit" :disabled="!input.trim()">
            <Send :size="14" aria-hidden="true" />{{ t('ai.send') }}
          </GlowButton>
        </form>
        <p class="mt-2 text-[11px] text-ink-dim">{{ t('ai.disclaimer') }}</p>
      </template>
    </GlassPanel>
  </div>
</template>
