/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Public API origin for native (Capacitor) builds. Empty = same origin. Never put secrets in VITE_* vars. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
