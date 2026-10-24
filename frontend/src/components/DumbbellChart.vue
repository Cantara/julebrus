<script setup lang="ts">
import { computed } from 'vue'

/**
 * Two values per row (you and the crowd) on one 1–6 scale, one hue in two shades,
 * joined by a bar. Legend above, values on hover, table underneath.
 */
const props = defineProps<{ rows: { label: string; a: number; b: number }[]; aName: string; bName: string }>()

const W = 600
const ROW = 26
const PAD = { l: 48, r: 16, t: 8, b: 20 }
const MIN = 1
const MAX = 6

const H = computed(() => PAD.t + PAD.b + props.rows.length * ROW)
const x = (v: number) => PAD.l + ((W - PAD.l - PAD.r) * (v - MIN)) / (MAX - MIN)
</script>

<template>
  <figure class="chart">
    <div class="legend">
      <span><i class="sw a" /> {{ aName }}</span>
      <span><i class="sw b" /> {{ bName }}</span>
    </div>
    <svg :viewBox="`0 0 ${W} ${H}`" role="img" :aria-label="`${aName} mot ${bName} per år`">
      <g v-for="v in [1, 2, 3, 4, 5, 6]" :key="v">
        <line :x1="x(v)" :x2="x(v)" :y1="PAD.t" :y2="H - PAD.b" class="grid" />
        <text :x="x(v)" :y="H - 6" text-anchor="middle" class="tick">{{ v }}</text>
      </g>
      <g v-for="(r, i) in rows" :key="r.label" :transform="`translate(0 ${PAD.t + i * ROW + ROW / 2})`">
        <text :x="PAD.l - 8" y="4" text-anchor="end" class="tick">{{ r.label }}</text>
        <line :x1="x(Math.min(r.a, r.b))" :x2="x(Math.max(r.a, r.b))" class="join" />
        <circle :cx="x(r.b)" r="6" class="dot b"><title>{{ r.label }} · {{ bName }}: {{ r.b.toFixed(2) }}</title></circle>
        <circle :cx="x(r.a)" r="6" class="dot a"><title>{{ r.label }} · {{ aName }}: {{ r.a.toFixed(2) }}</title></circle>
      </g>
    </svg>
    <details>
      <summary class="muted">Som tabell</summary>
      <table>
        <thead><tr><th>År</th><th>{{ aName }}</th><th>{{ bName }}</th></tr></thead>
        <tbody>
          <tr v-for="r in rows" :key="r.label"><td>{{ r.label }}</td><td>{{ r.a.toFixed(2) }}</td><td>{{ r.b.toFixed(2) }}</td></tr>
        </tbody>
      </table>
    </details>
  </figure>
</template>
