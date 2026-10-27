import type { CapacitorConfig } from '@capacitor/cli'

// Native iOS / Android shells load the same Vue build from `dist/`.
// For device builds set VITE_API_BASE_URL (e.g. https://api.example.com) before `npm run build`,
// because native apps are served from capacitor://localhost / https://localhost, not your API host.
const config: CapacitorConfig = {
  appId: 'com.cyberstock.pulse',
  appName: 'CyberStock Pulse',
  webDir: 'dist',
  backgroundColor: '#050508',
  ios: {
    contentInset: 'never', // we handle safe areas in CSS via env(safe-area-inset-*)
  },
  android: {
    allowMixedContent: false,
  },
  plugins: {
    StatusBar: { style: 'DARK', backgroundColor: '#0a0b10', overlaysWebView: true },
  },
}

export default config
