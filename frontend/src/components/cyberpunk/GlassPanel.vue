<script setup lang="ts">
/** Dark glassmorphism container with an optional neon heading. */
withDefaults(defineProps<{ title?: string; glow?: 'cyan' | 'purple' | 'yellow' | 'none'; as?: string }>(), {
  title: undefined,
  glow: 'none',
  as: 'section',
})
</script>

<template>
  <component
    :is="as"
    class="glass glass-gradient rounded-xl p-3 sm:p-4"
    :class="{
      'shadow-glow-cyan': glow === 'cyan',
      'shadow-glow-purple': glow === 'purple',
      'shadow-glow-yellow': glow === 'yellow',
    }"
  >
    <header v-if="title || $slots.actions" class="mb-3 flex items-center justify-between gap-2">
      <h2 v-if="title" class="flex items-center gap-2 text-sm font-semibold uppercase tracking-widest">
        <span aria-hidden="true" class="h-3 w-1 rounded-full bg-gradient-to-b from-neon-cyan to-neon-purple" />
        <span class="text-gradient">{{ title }}</span>
      </h2>
      <slot name="actions" />
    </header>
    <slot />
  </component>
</template>
