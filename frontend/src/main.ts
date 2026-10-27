import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { i18n } from './i18n'
import { configureHttp } from './api/http'
import { useAuthStore } from './stores/auth'
import { useSettingsStore } from './stores/settings'
import { vTilt } from './directives/tilt'
import './styles/main.css'

async function bootstrap() {
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia).use(i18n).use(router)
  app.directive('tilt', vTilt)

  const auth = useAuthStore()
  configureHttp({
    token: () => auth.token,
    unauthorized: () => void auth.logout(),
  })

  // Restore session first so the account's preferred language can be honoured
  await auth.restore().catch(() => undefined)
  await useSettingsStore().init(auth.user?.preferredLocale ?? null)

  app.mount('#app')
}

void bootstrap()
