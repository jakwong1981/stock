import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { marketApi } from '@/api'
import type { Instrument } from '@/api/types'

export const useInstrumentStore = defineStore('instruments', () => {
  const bySymbol = ref<Record<string, Instrument>>({})
  const loaded = ref(false)

  const all = computed(() => Object.values(bySymbol.value).sort((a, b) => a.symbol.localeCompare(b.symbol)))
  const marquee = computed(() => all.value.filter((i) => i.marquee))

  /** (Re)loads names in the current language (Accept-Language is sent automatically). */
  async function load(): Promise<void> {
    const list = await marketApi.instruments()
    bySymbol.value = Object.fromEntries(list.map((i) => [i.symbol, i]))
    loaded.value = true
  }

  function get(symbol: string): Instrument | undefined {
    return bySymbol.value[symbol]
  }

  return { bySymbol, loaded, all, marquee, load, get }
})
