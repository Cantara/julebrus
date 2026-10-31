<script setup lang="ts">
import { computed } from 'vue'

/**
 * The calendar's picture: a winter evening under frosted glass, one pane per door. Each door you open
 * clears its pane, and the whole season clears the whole picture.
 */
const props = defineProps<{ start: string; end: string; opened: string[] }>()

const COLS = 9
const W = 360
const tile = W / COLS

const panes = computed(() => {
  const out: { day: string; dom: number; x: number; y: number; open: boolean }[] = []
  const open = new Set(props.opened)
  const d = new Date(props.start + 'T00:00:00Z')
  const end = new Date(props.end + 'T00:00:00Z')
  for (let i = 0; d <= end; i++, d.setUTCDate(d.getUTCDate() + 1)) {
    const day = d.toISOString().slice(0, 10)
    out.push({ day, dom: d.getUTCDate(), x: (i % COLS) * tile, y: Math.floor(i / COLS) * tile, open: open.has(day) })
  }
  return out
})
const H = computed(() => Math.ceil(panes.value.length / COLS) * tile)
const cleared = computed(() => panes.value.filter((p) => p.open).length)
</script>

<template>
  <svg :viewBox="`0 0 ${W} ${H}`" class="picture" role="img" :aria-label="`Julebildet: ${cleared} av ${panes.length} ruter er klare`">
    <defs>
      <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stop-color="#16213b" />
        <stop offset="0.75" stop-color="#3a4f78" />
      </linearGradient>
    </defs>
    <rect :width="W" :height="H" fill="url(#sky)" />
    <circle cx="54" cy="46" r="20" fill="#f6e7c1" />
    <circle cx="62" cy="40" r="17" fill="#1b2846" />
    <g fill="#fdf6e3">
      <circle cx="120" cy="30" r="1.6" /><circle cx="210" cy="22" r="1.2" /><circle cx="300" cy="40" r="1.8" />
      <circle cx="160" cy="70" r="1.1" /><circle cx="330" cy="90" r="1.3" /><circle cx="250" cy="60" r="1" /><circle cx="25" cy="110" r="1.2" />
    </g>
    <path :d="`M0 ${H - 46} Q 90 ${H - 70} 180 ${H - 50} T ${W} ${H - 58} V ${H} H 0 Z`" fill="#f4f1ea" />
    <g transform="translate(250 0)">
      <rect x="0" :y="H - 110" width="78" height="62" fill="#7a1f2b" />
      <path :d="`M-8 ${H - 108} L39 ${H - 146} L86 ${H - 108} Z`" fill="#4a1219" />
      <path :d="`M-8 ${H - 108} L39 ${H - 146} L86 ${H - 108} L80 ${H - 104} L39 ${H - 137} L-2 ${H - 104} Z`" fill="#f4f1ea" />
      <rect x="10" :y="H - 98" width="18" height="16" fill="#f0b45a" /><rect x="50" :y="H - 98" width="18" height="16" fill="#f0b45a" />
      <rect x="31" :y="H - 76" width="16" height="28" fill="#3a1a12" />
    </g>
    <g transform="translate(150 0)">
      <rect x="-7" :y="H - 62" width="14" height="18" fill="#5a3a22" />
      <path :d="`M-62 ${H - 60} L0 ${H - 128} L62 ${H - 60} Z`" fill="#234b36" />
      <path :d="`M-50 ${H - 100} L0 ${H - 160} L50 ${H - 100} Z`" fill="#2d6047" />
      <path :d="`M-36 ${H - 140} L0 ${H - 190} L36 ${H - 140} Z`" fill="#357052" />
      <path :d="`M0 ${H - 206} l5 11 12 1 -9 8 3 12 -11 -6 -11 6 3 -12 -9 -8 12 -1 Z`" fill="#f0b45a" />
      <g>
        <circle cx="-30" :cy="H - 72" r="4" fill="#c0392b" /><circle cx="22" :cy="H - 80" r="4" fill="#f0b45a" />
        <circle cx="-14" :cy="H - 112" r="3.5" fill="#f0b45a" /><circle cx="18" :cy="H - 118" r="3.5" fill="#c0392b" />
        <circle cx="-6" :cy="H - 152" r="3" fill="#c0392b" /><circle cx="44" :cy="H - 66" r="3.5" fill="#e5c98f" />
      </g>
    </g>
    <g fill="#ffffff" opacity="0.85">
      <circle cx="90" cy="130" r="2" /><circle cx="200" cy="100" r="1.6" /><circle cx="320" cy="150" r="2" /><circle cx="40" cy="170" r="1.5" />
    </g>
    <g v-for="p in panes" :key="p.day">
      <rect v-if="!p.open" :x="p.x" :y="p.y" :width="tile" :height="tile" class="frost" />
      <text v-if="!p.open" :x="p.x + tile / 2" :y="p.y + tile / 2 + 4" text-anchor="middle" class="dom">{{ p.dom }}</text>
    </g>
  </svg>
</template>

<style scoped>
.picture { width: 100%; height: auto; display: block; border-radius: 12px; }
.frost { fill: var(--door-shut); stroke: var(--line); stroke-width: 0.6; }
.dom { font-size: 11px; fill: var(--muted); font-variant-numeric: tabular-nums; }
</style>
