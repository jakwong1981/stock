<script setup lang="ts">
/** Compact neon card for KPIs: gradient edge, 3D tilt on hover, value lifted towards the viewer. */
defineProps<{ label: string; accent?: 'cyan' | 'purple' | 'yellow' }>()
</script>

<template>
  <div
    v-tilt="{ max: 8, lift: 4 }"
    class="glass-gradient overflow-hidden rounded-lg p-3"
    :class="{
      'card-cyan': !accent || accent === 'cyan',
      'card-purple': accent === 'purple',
      'card-yellow': accent === 'yellow',
    }"
  >
    <span aria-hidden="true" class="card-bar absolute inset-x-0 top-0 h-0.5" />
    <p class="depth-1 text-xs text-ink-dim">{{ label }}</p>
    <div class="depth-2 mt-1 text-lg font-semibold"><slot /></div>
  </div>
</template>

<style scoped>
.card-cyan .card-bar { background: linear-gradient(90deg, var(--color-neon-cyan), transparent); }
.card-purple .card-bar { background: linear-gradient(90deg, var(--color-neon-purple), transparent); }
.card-yellow .card-bar { background: linear-gradient(90deg, var(--color-neon-yellow), transparent); }
</style>
