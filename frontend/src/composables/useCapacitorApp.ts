/** Native-shell integration: status bar styling and Android hardware back button. */
import { App } from '@capacitor/app'
import { StatusBar, Style } from '@capacitor/status-bar'
import { useRouter } from 'vue-router'
import { isNative, platform } from '@/platform/native'

let initialised = false

export function useCapacitorApp(): void {
  if (initialised || !isNative()) return
  initialised = true
  const router = useRouter()

  void StatusBar.setStyle({ style: Style.Dark }).catch(() => undefined)
  if (platform() === 'android') {
    void StatusBar.setOverlaysWebView({ overlay: true }).catch(() => undefined)
    void App.addListener('backButton', ({ canGoBack }) => {
      if (canGoBack) router.back()
      else void App.exitApp()
    })
  }
}
