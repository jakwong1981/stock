import { defineStore } from 'pinia'
import { ref } from 'vue'
import { portfolioApi } from '@/api'
import type { PortfolioResult, Side, Transaction } from '@/api/types'

export const usePortfolioStore = defineStore('portfolio', () => {
  const transactions = ref<Transaction[]>([])
  const result = ref<PortfolioResult>({ positions: [], summaries: [] })
  const loading = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    try {
      const [tx, pos] = await Promise.all([portfolioApi.transactions(), portfolioApi.positions()])
      transactions.value = tx
      result.value = pos
    } finally {
      loading.value = false
    }
  }

  async function add(t: { symbol: string; side: Side; quantity: string; price: string; fee: string;
    executedAt?: string }): Promise<void> {
    await portfolioApi.add(t)
    await load()
  }

  async function remove(id: string): Promise<void> {
    await portfolioApi.remove(id)
    await load()
  }

  function reset(): void {
    transactions.value = []
    result.value = { positions: [], summaries: [] }
  }

  return { transactions, result, loading, load, add, remove, reset }
})
