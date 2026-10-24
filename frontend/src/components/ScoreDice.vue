<script setup lang="ts">
import { ref } from 'vue'

defineProps<{ modelValue: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: number] }>()

/** Pip positions on a 3x3 grid, per face. */
const FACES: Record<number, [number, number][]> = {
  1: [[1, 1]],
  2: [[0, 0], [2, 2]],
  3: [[0, 0], [1, 1], [2, 2]],
  4: [[0, 0], [0, 2], [2, 0], [2, 2]],
  5: [[0, 0], [0, 2], [1, 1], [2, 0], [2, 2]],
  6: [[0, 0], [0, 1], [0, 2], [2, 0], [2, 1], [2, 2]],
}

const rolling = ref<number | null>(null)

function pick(n: number) {
  rolling.value = n
  window.setTimeout(() => (rolling.value = null), 450)
  emit('update:modelValue', n)
}
</script>

<template>
  <div class="dice" role="radiogroup" aria-label="Terningkast">
    <button
      v-for="n in 6"
      :key="n"
      type="button"
      class="die"
      role="radio"
      :aria-checked="modelValue === n"
      :aria-label="`${n}`"
      :class="{ on: modelValue === n, roll: rolling === n }"
      @click="pick(n)"
    >
      <svg viewBox="0 0 30 30" aria-hidden="true">
        <circle v-for="([r, c], i) in FACES[n]" :key="i" class="pip" :cx="5 + c * 10" :cy="5 + r * 10" r="3.2" />
      </svg>
    </button>
  </div>
</template>
