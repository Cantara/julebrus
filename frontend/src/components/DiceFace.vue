<script setup lang="ts">
/** One die face, for scores in lists and at the reveal. Same pips as the picker. */
defineProps<{ value: number; size?: number }>()
const FACES: Record<number, [number, number][]> = {
  1: [[1, 1]],
  2: [[0, 0], [2, 2]],
  3: [[0, 0], [1, 1], [2, 2]],
  4: [[0, 0], [0, 2], [2, 0], [2, 2]],
  5: [[0, 0], [0, 2], [1, 1], [2, 0], [2, 2]],
  6: [[0, 0], [0, 1], [0, 2], [2, 0], [2, 1], [2, 2]],
}
</script>

<template>
  <svg class="face" :width="size ?? 28" :height="size ?? 28" viewBox="0 0 30 30" role="img" :aria-label="`${value}`">
    <rect x="1" y="1" width="28" height="28" rx="6" />
    <circle v-for="([r, c], i) in FACES[value] ?? []" :key="i" :cx="5 + c * 10" :cy="5 + r * 10" r="3.2" />
  </svg>
</template>

<style scoped>
.face { vertical-align: middle; flex: 0 0 auto; }
.face rect { fill: var(--card); stroke: var(--brass); stroke-width: 1.5; }
.face circle { fill: var(--ink); }
</style>
