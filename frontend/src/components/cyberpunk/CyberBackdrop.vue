<script setup lang="ts">
/**
 * Decorative full-screen backdrop: drifting aurora, a 3D perspective neon grid floor and
 * slowly spinning wireframe cubes. Purely visual (aria-hidden, no pointer events); disabled
 * by prefers-reduced-motion and hidden in high-contrast mode (see main.css).
 */
const cubes = [
  { cls: 'left-[2%] top-[55%] hidden sm:block', size: 54, delay: '0s', dur: '18s' },
  { cls: 'right-[3%] top-[62%] hidden sm:block', size: 82, delay: '-6s', dur: '24s' },
  { cls: 'left-[42%] top-[8%] hidden md:block', size: 36, delay: '-11s', dur: '14s' },
  { cls: 'right-[30%] bottom-[38%] hidden lg:block', size: 46, delay: '-3s', dur: '20s' },
]
</script>

<template>
  <div class="backdrop" aria-hidden="true">
    <div class="aurora aurora-1" />
    <div class="aurora aurora-2" />
    <div class="aurora aurora-3" />
    <div class="grid-horizon" />
    <div class="grid-floor"><div class="grid-floor-plane" /></div>
    <div
      v-for="(c, i) in cubes"
      :key="i"
      class="cube-wrap opacity-35"
      :class="c.cls"
      :style="{ animationDelay: c.delay }"
    >
      <div class="cube3d" :style="{ '--s': `${c.size}px`, animationDuration: c.dur, animationDelay: c.delay }">
        <i v-for="f in 6" :key="f" />
      </div>
    </div>
  </div>
</template>
