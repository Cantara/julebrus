<script setup lang="ts">
import { computed } from 'vue'

/**
 * A few dozen CSS-only flakes behind the page. Hidden under prefers-reduced-motion
 * (in CSS) and when the person has switched it off.
 */
const props = defineProps<{ enabled: boolean; count?: number }>()

const flakes = computed(() => {
  const n = props.count ?? 36
  const out: { left: string; size: string; dur: string; delay: string; drift: string; opacity: number }[] = []
  for (let i = 0; i < n; i++) {
    const size = 2 + Math.random() * 4
    out.push({
      left: `${Math.random() * 100}%`,
      size: `${size.toFixed(1)}px`,
      dur: `${(9 + Math.random() * 12).toFixed(1)}s`,
      delay: `${(-Math.random() * 20).toFixed(1)}s`,
      drift: `${(Math.random() * 80 - 40).toFixed(0)}px`,
      opacity: 0.35 + Math.random() * 0.55,
    })
  }
  return out
})
</script>

<template>
  <div v-if="enabled" class="snow" aria-hidden="true">
    <i
      v-for="(f, i) in flakes"
      :key="i"
      :style="{ left: f.left, width: f.size, height: f.size, animationDuration: f.dur, animationDelay: f.delay, opacity: f.opacity, '--drift': f.drift }"
    />
  </div>
</template>
