<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Search } from '@lucide/vue'
import { marketApi } from '@/api'
import type { Instrument } from '@/api/types'
import StockLogo from './StockLogo.vue'

/** Accessible combobox: searches symbols / names in any of the supported scripts. */
const { t } = useI18n()
const router = useRouter()
const q = ref('')
const results = ref<Instrument[]>([])
const open = ref(false)
const active = ref(-1)
let ctrl: AbortController | null = null
let debounce: ReturnType<typeof setTimeout> | null = null

watch(q, (v) => {
  if (debounce) clearTimeout(debounce)
  if (!v.trim()) {
    results.value = []
    open.value = false
    return
  }
  debounce = setTimeout(async () => {
    ctrl?.abort()
    ctrl = new AbortController()
    try {
      results.value = await marketApi.search(v.trim(), ctrl.signal)
      open.value = true
      active.value = results.value.length ? 0 : -1
    } catch {
      /* aborted or network */
    }
  }, 200)
})

onBeforeUnmount(() => ctrl?.abort())

function go(i: Instrument) {
  q.value = ''
  open.value = false
  void router.push({ name: 'stock', params: { symbol: i.symbol } })
}

function onBlur() {
  setTimeout(() => (open.value = false), 150)
}

function onKey(e: KeyboardEvent) {
  if (!open.value || !results.value.length) return
  if (e.key === 'ArrowDown') active.value = (active.value + 1) % results.value.length
  else if (e.key === 'ArrowUp') active.value = (active.value - 1 + results.value.length) % results.value.length
  else if (e.key === 'Enter' && active.value >= 0) go(results.value[active.value])
  else if (e.key === 'Escape') open.value = false
  else return
  e.preventDefault()
}
</script>

<template>
  <div class="relative w-full max-w-md">
    <label class="glass flex min-h-11 items-center gap-2 rounded-lg px-3 focus-within:border-neon-cyan">
      <Search :size="16" class="text-ink-dim" aria-hidden="true" />
      <span class="sr-only">{{ t('common.search') }}</span>
      <input
        v-model="q"
        type="search"
        role="combobox"
        :aria-expanded="open"
        aria-controls="search-results"
        aria-autocomplete="list"
        :aria-activedescendant="active >= 0 ? `sr-${active}` : undefined"
        :placeholder="t('common.searchPlaceholder')"
        class="w-full bg-transparent text-sm text-ink placeholder:text-ink-dim focus:outline-none"
        autocomplete="off"
        @keydown="onKey"
        @blur="onBlur"
      />
    </label>
    <ul
      v-if="open"
      id="search-results"
      role="listbox"
      class="glass absolute z-30 mt-1 max-h-80 w-full overflow-auto rounded-lg py-1"
    >
      <li v-if="!results.length" class="px-3 py-2 text-sm text-ink-dim">{{ t('common.noResults') }}</li>
      <li
        v-for="(r, i) in results"
        :id="`sr-${i}`"
        :key="r.symbol"
        role="option"
        :aria-selected="i === active"
        class="flex min-h-11 cursor-pointer items-center gap-3 px-3"
        :class="i === active ? 'bg-neon-cyan/10' : ''"
        @mousedown.prevent="go(r)"
      >
        <StockLogo :symbol="r.symbol" :src="r.logoUrl" :size="24" />
        <span class="num text-sm font-semibold">{{ r.symbol }}</span>
        <span class="truncate text-sm text-ink-dim">{{ r.name }}</span>
      </li>
    </ul>
  </div>
</template>
