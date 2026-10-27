<script setup lang="ts">
import { hapticTap } from '@/platform/native'

const props = withDefaults(
  defineProps<{ variant?: 'primary' | 'ghost' | 'danger'; type?: 'button' | 'submit'; disabled?: boolean }>(),
  { variant: 'primary', type: 'button', disabled: false },
)
const emit = defineEmits<{ click: [MouseEvent] }>()

function onClick(e: MouseEvent) {
  if (props.disabled) return
  void hapticTap()
  emit('click', e)
}
</script>

<template>
  <!-- min-h-11 = 44px touch target -->
  <button
    :type="type"
    :disabled="disabled"
    class="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold transition
      disabled:cursor-not-allowed disabled:opacity-50"
    :class="{
      'btn-gradient text-ink': variant === 'primary',
      'border border-line text-ink hover:border-neon-cyan/60': variant === 'ghost',
      'border border-down/70 text-down hover:bg-down/10': variant === 'danger',
    }"
    @click="onClick"
  >
    <slot />
  </button>
</template>
