/**
 * v-tilt: 3D tilt + moving light reflection that follows the pointer.
 *
 *   <div v-tilt>…</div>            default max angle 6°
 *   <div v-tilt="{ max: 10 }">…</div>
 *
 * Only active for fine pointers (mouse / trackpad). Touch devices, prefers-reduced-motion
 * and the outdoor high-contrast mode keep elements flat, so scrolling and reading on
 * phones are unaffected. Updates are batched with requestAnimationFrame.
 */
import type { Directive } from 'vue'

interface TiltOptions {
  max?: number
  /** Extra lift (px) while hovered */
  lift?: number
}

interface State {
  move: (e: PointerEvent) => void
  leave: () => void
  frame: number
}

const states = new WeakMap<HTMLElement, State>()

function enabled(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  if (!window.matchMedia('(hover: hover) and (pointer: fine)').matches) return false
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return false
  return !document.documentElement.classList.contains('high-contrast')
}

export const vTilt: Directive<HTMLElement, TiltOptions | undefined> = {
  mounted(el, binding) {
    el.dataset.tilt = ''
    if (getComputedStyle(el).position === 'static') el.style.position = 'relative'
    const max = binding.value?.max ?? 6
    const lift = binding.value?.lift ?? 6

    const state: State = {
      frame: 0,
      move(e: PointerEvent) {
        if (!enabled()) return
        cancelAnimationFrame(state.frame)
        state.frame = requestAnimationFrame(() => {
          const r = el.getBoundingClientRect()
          const px = (e.clientX - r.left) / r.width // 0..1
          const py = (e.clientY - r.top) / r.height
          const rx = (0.5 - py) * max * 2
          const ry = (px - 0.5) * max * 2
          el.classList.add('tilting')
          el.style.setProperty('--gx', `${(px * 100).toFixed(1)}%`)
          el.style.setProperty('--gy', `${(py * 100).toFixed(1)}%`)
          el.style.transform =
            `perspective(900px) rotateX(${rx.toFixed(2)}deg) rotateY(${ry.toFixed(2)}deg) translate3d(0, -${lift}px, 0)`
        })
      },
      leave() {
        cancelAnimationFrame(state.frame)
        el.classList.remove('tilting')
        el.style.transform = ''
      },
    }
    el.addEventListener('pointermove', state.move)
    el.addEventListener('pointerleave', state.leave)
    states.set(el, state)
  },
  unmounted(el) {
    const s = states.get(el)
    if (!s) return
    cancelAnimationFrame(s.frame)
    el.removeEventListener('pointermove', s.move)
    el.removeEventListener('pointerleave', s.leave)
    states.delete(el)
  },
}
