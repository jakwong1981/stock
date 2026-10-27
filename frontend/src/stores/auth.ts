import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import type { Me, TokenResponse } from '@/api/types'
import { storage } from '@/platform/native'

const K_TOKEN = 'csp.token'
const K_EXPIRES = 'csp.tokenExpires'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const expiresAt = ref<number>(0)
  const user = ref<Me | null>(null)

  const isAuthenticated = computed(() => !!token.value && expiresAt.value > Date.now())

  async function restore(): Promise<void> {
    const [t, exp] = await Promise.all([storage.get(K_TOKEN), storage.get(K_EXPIRES)])
    if (t && exp && Number(exp) > Date.now()) {
      token.value = t
      expiresAt.value = Number(exp)
      try {
        user.value = await authApi.me()
      } catch {
        await clear()
      }
    }
  }

  async function apply(res: TokenResponse): Promise<void> {
    token.value = res.accessToken
    expiresAt.value = new Date(res.expiresAt).getTime()
    user.value = res.user
    await Promise.all([storage.set(K_TOKEN, res.accessToken), storage.set(K_EXPIRES, String(expiresAt.value))])
  }

  async function login(email: string, password: string): Promise<void> {
    await apply(await authApi.login(email, password))
  }

  async function register(email: string, password: string, displayName: string, locale: string): Promise<void> {
    await apply(await authApi.register(email, password, displayName, locale))
  }

  async function clear(): Promise<void> {
    token.value = null
    expiresAt.value = 0
    user.value = null
    await Promise.all([storage.remove(K_TOKEN), storage.remove(K_EXPIRES)])
  }

  return { token, user, isAuthenticated, restore, login, register, logout: clear }
})
