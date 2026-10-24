<script setup lang="ts">
import { computed } from 'vue'

/**
 * Diverging bars around zero: how far above (brass) or below (burgundy) your own
 * mean you score beers with each tag. Two hues, neutral zero line, values on hover.
 */
const props = defineProps<{ rows: { tag: string; lift: number; count: number }[] }>()

const W = 600
const ROW = 24
const PAD = { l: 110, r: 40, t: 6, b: 6 }
const VAL = 44 // room for a value label at either end
const H = computed(() => PAD.t + PAD.b + props.rows.length * ROW)
const max = computed(() => Math.max(1, ...props.rows.map((r) => Math.abs(r.lift))))
const half = (W - PAD.l - PAD.r - 2 * VAL) / 2
const zero = PAD.l + VAL + half
const scale = computed(() => half / max.value)
</script>

<template>
  <figure class="chart">
    <svg :viewBox="`0 0 ${W} ${H}`" role="img" aria-label="Stikkord over og under ditt eget snitt">
      <line :x1="zero" :x2="zero" :y1="PAD.t" :y2="H - PAD.b" class="axis" />
      <g v-for="(r, i) in rows" :key="r.tag" :transform="`translate(0 ${PAD.t + i * ROW})`">
        <text :x="PAD.l - 8" :y="ROW / 2 + 4" text-anchor="end" class="tick">{{ r.tag }}</text>
        <rect
          :x="r.lift >= 0 ? zero : zero + r.lift * scale"
          :y="5"
          :width="Math.max(2, Math.abs(r.lift) * scale)"
          :height="ROW - 10"
          rx="3"
          :class="r.lift >= 0 ? 'bar up' : 'bar down'"
        >
          <title>{{ r.tag }}: {{ r.lift >= 0 ? '+' : '' }}{{ r.lift.toFixed(2) }} ({{ r.count }} øl)</title>
        </rect>
        <text :x="r.lift >= 0 ? zero + r.lift * scale + 6 : zero + r.lift * scale - 6" :y="ROW / 2 + 4"
              :text-anchor="r.lift >= 0 ? 'start' : 'end'" class="value">{{ r.lift >= 0 ? '+' : '' }}{{ r.lift.toFixed(1) }}</text>
      </g>
    </svg>
  </figure>
</template>
