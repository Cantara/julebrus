<script setup lang="ts">
import { computed } from 'vue'

/**
 * One series of bars, one hue. Hover any bar for its value; the largest and the
 * latest bar are labelled directly. A small table sits under it for screen readers.
 */
const props = defineProps<{ points: { label: string; value: number }[]; title: string; unit?: string }>()

const W = 600
const H = 200
const PAD = { l: 8, r: 8, t: 22, b: 24 }

const layout = computed(() => {
  const n = props.points.length
  const max = Math.max(1, ...props.points.map((p) => p.value))
  const innerW = W - PAD.l - PAD.r
  const gap = 4
  const bw = n ? (innerW - gap * (n - 1)) / n : innerW
  const maxIdx = props.points.findIndex((p) => p.value === max)
  return props.points.map((p, i) => {
    const h = ((H - PAD.t - PAD.b) * p.value) / max
    return {
      ...p,
      x: PAD.l + i * (bw + gap),
      y: H - PAD.b - h,
      w: bw,
      h,
      labelled: i === maxIdx || i === n - 1,
    }
  })
})
</script>

<template>
  <figure class="chart">
    <figcaption class="muted">{{ title }}</figcaption>
    <svg :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="title">
      <line :x1="PAD.l" :x2="W - PAD.r" :y1="H - PAD.b" :y2="H - PAD.b" class="axis" />
      <g v-for="b in layout" :key="b.label">
        <rect :x="b.x" :y="b.y" :width="b.w" :height="b.h" rx="3" class="bar">
          <title>{{ b.label }}: {{ b.value }}{{ unit ? ' ' + unit : '' }}</title>
        </rect>
        <text v-if="b.labelled" :x="b.x + b.w / 2" :y="b.y - 6" text-anchor="middle" class="value">{{ b.value }}</text>
        <text :x="b.x + b.w / 2" :y="H - 8" text-anchor="middle" class="tick">{{ b.label }}</text>
      </g>
    </svg>
    <details>
      <summary class="muted">Som tabell</summary>
      <table>
        <tbody>
          <tr v-for="p in points" :key="p.label"><td>{{ p.label }}</td><td style="text-align: right">{{ p.value }}</td></tr>
        </tbody>
      </table>
    </details>
  </figure>
</template>
