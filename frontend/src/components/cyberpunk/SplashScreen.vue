<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * Opening animation: spinning 3D neon cube, glitch brand title, a candlestick chart that
 * draws itself and a gradient progress bar. Stays at least MIN_MS, leaves as soon as the
 * app reports `ready` (or after MAX_MS), and can be skipped with a tap / click / key press.
 * With prefers-reduced-motion it becomes a short static fade.
 */
const props = defineProps<{ ready: boolean }>()
const emit = defineEmits<{ done: [] }>()
const { t } = useI18n()

const reduced = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
const MIN_MS = reduced ? 350 : 1700
const MAX_MS = 4500
const LEAVE_MS = 550

const minElapsed = ref(false)
const leaving = ref(false)
const timers: ReturnType<typeof setTimeout>[] = []

// Candles for the self-drawing chart: [x, open, close, high, low] in a 0–100 box (y down)
const candles = [
  [8, 70, 58, 52, 76], [20, 58, 64, 50, 70], [32, 64, 46, 40, 68], [44, 46, 52, 38, 58],
  [56, 52, 34, 28, 56], [68, 34, 40, 26, 46], [80, 40, 22, 16, 44], [92, 22, 14, 8, 28],
] as const
const trend = computed(() => candles.map(([x, , c], i) => `${i ? 'L' : 'M'}${x} ${c}`).join(' '))

function leave() {
  if (leaving.value) return
  leaving.value = true
  timers.push(setTimeout(() => emit('done'), reduced ? 150 : LEAVE_MS))
}

function maybeLeave() {
  if (minElapsed.value && props.ready) leave()
}

watch(() => props.ready, maybeLeave)

function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === ' ' || e.key === 'Escape') leave()
}

onMounted(() => {
  timers.push(setTimeout(() => {
    minElapsed.value = true
    maybeLeave()
  }, MIN_MS))
  timers.push(setTimeout(leave, MAX_MS))
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  timers.forEach(clearTimeout)
  window.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div
    class="splash fixed inset-0 z-[100] flex cursor-pointer select-none flex-col items-center justify-center
      overflow-hidden"
    :class="{ 'splash-leave': leaving, 'splash-reduced': reduced }"
    role="status"
    aria-live="polite"
    @click="leave"
  >
    <div class="splash-bg" aria-hidden="true" />

    <!-- 3D cube -->
    <div class="splash-scene" aria-hidden="true">
      <div class="splash-cube">
        <span class="face f1">
          <svg viewBox="0 0 64 64" class="h-1/2 w-1/2">
            <polyline points="8,46 22,32 32,38 46,18 56,24" fill="none" stroke="currentColor" stroke-width="5"
              stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </span>
        <span class="face f2 num">US</span>
        <span class="face f3 num">HK</span>
        <span class="face f4 num">$</span>
        <span class="face f5" />
        <span class="face f6" />
      </div>
      <div class="splash-shadow" />
    </div>

    <h1 class="glitch mt-10 text-center text-2xl font-black uppercase tracking-[0.35em] sm:text-4xl"
      :data-text="t('app.name')">
      <span class="text-gradient">{{ t('app.name') }}</span>
    </h1>
    <p class="splash-fade mt-2 text-sm text-ink-dim">{{ t('app.tagline') }}</p>

    <!-- Self-drawing candlestick chart -->
    <svg viewBox="0 0 100 84" class="splash-chart mt-6 h-20 w-56 sm:h-24 sm:w-72" aria-hidden="true">
      <defs>
        <linearGradient id="splash-trend" x1="0" x2="1">
          <stop offset="0" stop-color="#00f0ff" />
          <stop offset="0.6" stop-color="#bd00ff" />
          <stop offset="1" stop-color="#f3e600" />
        </linearGradient>
      </defs>
      <g v-for="([x, o, c, h, l], i) in candles" :key="i" class="splash-candle"
        :style="{ animationDelay: `${300 + i * 90}ms` }">
        <line :x1="x" :x2="x" :y1="h" :y2="l" stroke="#a3abc2" stroke-width="0.8" />
        <rect :x="x - 3" :width="6" :y="Math.min(o, c)" :height="Math.max(2, Math.abs(o - c))" rx="0.8"
          :fill="c < o ? '#22f59a' : '#ff3b6b'" />
      </g>
      <path :d="trend" class="splash-trend" fill="none" stroke="url(#splash-trend)" stroke-width="1.6"
        stroke-linecap="round" stroke-linejoin="round" pathLength="1" />
    </svg>

    <div class="mt-6 h-1 w-56 overflow-hidden rounded-full bg-line sm:w-72" aria-hidden="true">
      <div class="splash-progress h-full rounded-full" :class="{ done: ready }" />
    </div>
    <p class="splash-fade mt-3 text-[11px] text-ink-dim">
      <span class="sr-only">{{ t('common.loading') }}</span>{{ t('app.tapToSkip') }}
    </p>
  </div>
</template>

<style scoped>
.splash {
  background: var(--color-void);
  transition: opacity 550ms ease, transform 550ms ease, filter 550ms ease;
}
.splash-leave {
  opacity: 0;
  transform: scale(1.08);
  filter: blur(6px);
  pointer-events: none;
}
.splash-bg {
  position: absolute;
  inset: -20%;
  background:
    radial-gradient(circle at 30% 30%, rgb(0 240 255 / 0.22), transparent 45%),
    radial-gradient(circle at 70% 65%, rgb(189 0 255 / 0.28), transparent 50%),
    radial-gradient(circle at 50% 110%, rgb(243 230 0 / 0.08), transparent 45%);
  animation: bg-drift 6s ease-in-out infinite alternate;
}
@keyframes bg-drift {
  to { transform: rotate(8deg) scale(1.1); }
}

/* --- 3D cube --- */
.splash-scene {
  --s: 92px;
  position: relative;
  width: var(--s);
  height: var(--s);
  perspective: 600px;
  animation: pop-in 700ms cubic-bezier(0.2, 1.4, 0.4, 1) both;
}
.splash-cube {
  position: relative;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  animation: spin 3.2s cubic-bezier(0.6, 0, 0.4, 1) infinite;
}
.face {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 800;
  color: var(--color-neon-cyan);
  border: 1.5px solid rgb(0 240 255 / 0.85);
  background: linear-gradient(135deg, rgb(0 240 255 / 0.16), rgb(189 0 255 / 0.22));
  box-shadow: inset 0 0 22px rgb(189 0 255 / 0.45), 0 0 18px rgb(0 240 255 / 0.25);
  backface-visibility: hidden; /* no mirrored text on the far side */
}
.f1 { transform: translateZ(calc(var(--s) / 2)); }
.f2 { transform: rotateY(90deg) translateZ(calc(var(--s) / 2)); color: var(--color-neon-yellow); }
.f3 { transform: rotateY(180deg) translateZ(calc(var(--s) / 2)); color: var(--color-neon-purple); }
.f4 { transform: rotateY(-90deg) translateZ(calc(var(--s) / 2)); }
.f5 { transform: rotateX(90deg) translateZ(calc(var(--s) / 2)); }
.f6 { transform: rotateX(-90deg) translateZ(calc(var(--s) / 2)); }
.splash-shadow {
  position: absolute;
  left: 50%;
  bottom: -42px;
  width: 120px;
  height: 18px;
  margin-left: -60px;
  border-radius: 50%;
  background: radial-gradient(ellipse, rgb(0 240 255 / 0.45), transparent 70%);
  animation: shadow-pulse 3.2s cubic-bezier(0.6, 0, 0.4, 1) infinite;
}
@keyframes spin {
  0% { transform: rotateX(-24deg) rotateY(0deg); }
  50% { transform: rotateX(-24deg) rotateY(180deg) translateY(-10px); }
  100% { transform: rotateX(-24deg) rotateY(360deg); }
}
@keyframes shadow-pulse {
  50% { transform: scale(0.75); opacity: 0.6; }
}
@keyframes pop-in {
  from { transform: scale(0.2) rotate(-30deg); opacity: 0; }
}

/* --- Glitch title --- */
.glitch {
  position: relative;
  animation: rise 600ms 150ms ease-out both;
}
.glitch::before,
.glitch::after {
  content: attr(data-text);
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.glitch::before {
  color: var(--color-neon-cyan);
  transform: translate(-2px, 0);
  clip-path: inset(0 0 100% 0);
  animation: glitch-a 2.4s 700ms steps(1) infinite;
  mix-blend-mode: screen;
}
.glitch::after {
  color: var(--color-neon-purple);
  transform: translate(2px, 0);
  clip-path: inset(100% 0 0 0);
  animation: glitch-b 2.4s 760ms steps(1) infinite;
  mix-blend-mode: screen;
}
@keyframes glitch-a {
  0% { clip-path: inset(10% 0 70% 0); }
  6% { clip-path: inset(60% 0 15% 0); transform: translate(-4px, 1px); }
  12%, 100% { clip-path: inset(0 0 100% 0); }
}
@keyframes glitch-b {
  0% { clip-path: inset(70% 0 5% 0); }
  6% { clip-path: inset(25% 0 50% 0); transform: translate(4px, -1px); }
  12%, 100% { clip-path: inset(100% 0 0 0); }
}
@keyframes rise {
  from { transform: translateY(14px); opacity: 0; letter-spacing: 0.8em; }
}
.splash-fade {
  animation: fade 600ms 450ms ease-out both;
}
@keyframes fade {
  from { opacity: 0; }
}

/* --- Self-drawing chart --- */
.splash-candle {
  transform-box: fill-box;
  transform-origin: 50% 100%;
  animation: candle-grow 380ms cubic-bezier(0.2, 1.3, 0.5, 1) both;
}
@keyframes candle-grow {
  from { transform: scaleY(0); opacity: 0; }
}
.splash-trend {
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  filter: drop-shadow(0 0 2px rgb(189 0 255 / 0.9));
  animation: draw 1.1s 350ms ease-out forwards;
}
@keyframes draw {
  to { stroke-dashoffset: 0; }
}

/* --- Progress bar --- */
.splash-progress {
  width: 100%;
  background: linear-gradient(90deg, var(--color-neon-cyan), var(--color-neon-purple), var(--color-neon-yellow));
  box-shadow: 0 0 12px rgb(189 0 255 / 0.7);
  transform-origin: left;
  transform: scaleX(0.05);
  animation: load 3.5s cubic-bezier(0.1, 0.7, 0.3, 1) forwards;
}
.splash-progress.done {
  animation: none;
  transform: scaleX(1);
  transition: transform 300ms ease-out;
}
@keyframes load {
  to { transform: scaleX(0.88); }
}

/* --- Reduced motion: static logo, quick fade --- */
.splash-reduced *,
.splash-reduced *::before,
.splash-reduced *::after {
  animation: none !important;
}
.splash-reduced .splash-trend { stroke-dashoffset: 0; }
.splash-reduced.splash-leave { transform: none; filter: none; }
</style>
