/**
 * Fetch wrapper. Adds the bearer token and Accept-Language, and converts error bodies
 * ({code, params}) into ApiError so the UI can translate them.
 */
import { currentLocale, i18n } from '@/i18n'

const BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

let tokenProvider: () => string | null = () => null
let onUnauthorized: () => void = () => undefined

export function configureHttp(opts: { token: () => string | null; unauthorized: () => void }): void {
  tokenProvider = opts.token
  onUnauthorized = opts.unauthorized
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    public readonly params: Record<string, unknown> = {},
  ) {
    super(code)
  }
}

export function apiUrl(path: string): string {
  return BASE + path
}

/** WebSocket URL for the same origin (or VITE_API_BASE_URL on native builds). */
export function wsUrl(path: string): string {
  if (BASE) return BASE.replace(/^http/, 'ws') + path
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${location.host}${path}`
}

export function authHeaders(): Record<string, string> {
  const h: Record<string, string> = { 'Accept-Language': currentLocale() }
  const t = tokenProvider()
  if (t) h.Authorization = `Bearer ${t}`
  return h
}

export async function request<T>(method: string, path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  let res: Response
  try {
    res = await fetch(apiUrl(path), {
      method,
      headers: { ...authHeaders(), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    })
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e
    throw new ApiError(0, 'NETWORK')
  }
  if (res.status === 401) onUnauthorized()
  if (!res.ok) {
    let code = res.status === 401 ? 'UNAUTHORIZED' : 'UNKNOWN'
    let params: Record<string, unknown> = {}
    try {
      const j = await res.json()
      // backend: {code, params}; FastAPI: {detail: {code}}
      const src = j?.detail && typeof j.detail === 'object' ? j.detail : j
      if (typeof src?.code === 'string') code = src.code
      if (src?.params && typeof src.params === 'object') params = src.params
    } catch {
      /* non-JSON error */
    }
    throw new ApiError(res.status, code, params)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const http = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>('GET', path, undefined, signal),
  post: <T>(path: string, body: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
}

/** Translates an error into the current language. */
export function errorMessage(e: unknown): string {
  const t = i18n.global.t as (k: string, p?: Record<string, unknown>) => string
  if (e instanceof ApiError) {
    const key = `errors.${e.code}`
    const msg = t(key, e.params)
    return msg === key ? t('errors.UNKNOWN') : msg
  }
  return t('errors.UNKNOWN')
}
