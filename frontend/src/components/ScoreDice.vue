<script setup lang="ts">
import { computed, ref } from 'vue'
import { buzz, fx } from '../effects'
import { clack, prime } from '../sound'

const props = defineProps<{ modelValue: number | null }>()
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

/** How to turn the cube so a face looks at you: 1 front, 6 back, 3 right, 4 left, 2 top, 5 bottom. */
const SHOW: Record<number, [number, number]> = { 1: [0, 0], 6: [0, 180], 3: [0, -90], 4: [0, 90], 2: [-90, 0], 5: [90, 0] }

const rolling = ref<number | null>(null)
const turns = ref(0)
const stage = ref<HTMLElement | null>(null)

/** Each pick adds whole turns, so the die always tumbles forward before it lands on the face. */
const cube = computed(() => {
  if (!props.modelValue) return undefined
  const [x, y] = SHOW[props.modelValue]
  return { transform: `rotateX(${x + turns.value * 720}deg) rotateY(${y + turns.value * 360}deg)` }
})

function pick(n: number) {
  prime()
  rolling.value = n
  window.setTimeout(() => (rolling.value = null), 450)
  turns.value += 1
  emit('update:modelValue', n)
  if (!fx.value) return
  // The throw: up, down, one small bounce; the knock and the tick in the hand come as it lands.
  stage.value?.animate(
    [
      { transform: 'translateY(0) scale(1)' },
      { transform: 'translateY(-26px) scale(1.12)', offset: 0.3 },
      { transform: 'translateY(0) scale(1)', offset: 0.68 },
      { transform: 'translateY(-6px) scale(1.02)', offset: 0.82 },
      { transform: 'translateY(0) scale(1)' },
    ],
    { duration: 900, easing: 'ease-out' },
  )
  window.setTimeout(() => {
    clack()
    buzz(16)
  }, 600)
}
</script>

<template>
  <div v-if="fx" class="stage" aria-hidden="true">
    <div ref="stage" class="thrown">
      <div class="tilt">
        <div class="cube" :class="{ idle: !modelValue }" :style="cube">
          <div v-for="n in 6" :key="n" class="side" :class="`s${n}`">
            <svg viewBox="0 0 30 30"><circle v-for="([r, c], i) in FACES[n]" :key="i" :cx="5 + c * 10" :cy="5 + r * 10" r="3.2" /></svg>
          </div>
        </div>
      </div>
    </div>
  </div>
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

<style scoped>
/* A real cube: six faces in 3D, tilted a little so the top and one side show when it lands. */
.stage { height: 84px; display: grid; place-items: center; perspective: 520px; margin: 2px 0 6px; }
.tilt { transform-style: preserve-3d; transform: rotateX(-18deg) rotateY(-22deg); }
.cube { --s: 52px; width: var(--s); height: var(--s); position: relative; transform-style: preserve-3d; transition: transform 900ms cubic-bezier(0.2, 0.85, 0.3, 1.04); }
.cube.idle { animation: idle 9s linear infinite; }
@keyframes idle { from { transform: rotateY(0deg); } to { transform: rotateY(360deg); } }
.side {
  position: absolute; inset: 0; display: grid; place-items: center; border-radius: 18%;
  background: var(--card); border: 1.5px solid var(--brass); backface-visibility: hidden;
}
.side svg { width: 72%; height: 72%; }
.side circle { fill: var(--ink); }
.s1 { transform: translateZ(calc(var(--s) / 2)); }
.s6 { transform: rotateY(180deg) translateZ(calc(var(--s) / 2)); }
.s3 { transform: rotateY(90deg) translateZ(calc(var(--s) / 2)); }
.s4 { transform: rotateY(-90deg) translateZ(calc(var(--s) / 2)); }
.s2 { transform: rotateX(90deg) translateZ(calc(var(--s) / 2)); }
.s5 { transform: rotateX(-90deg) translateZ(calc(var(--s) / 2)); }
</style>
