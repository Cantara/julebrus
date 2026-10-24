<script setup lang="ts">
import { computed, ref } from 'vue'
import type { BadgeView } from '../api/types'

/**
 * Badges as julekuler on a tree. Earned ones hang lit, the rest are frosted glass.
 * Tap a bauble to read its rule. Colour is decoration; identity is the label.
 */
const props = defineProps<{ badges: BadgeView[] }>()

// Nine slots down the tree, left to right, top to bottom.
const SLOTS: [number, number][] = [
  [100, 62],
  [78, 98], [124, 104],
  [62, 142], [100, 150], [140, 146],
  [50, 190], [98, 196], [150, 192],
]
// Decorative only; the label carries identity. No pine: it would vanish against the tree.
const TONES = ['var(--brand)', 'var(--brass)', '#9fc5d8', 'var(--candle)']

const selected = ref<string | null>(null)
const items = computed(() => props.badges.slice(0, SLOTS.length).map((b, i) => ({ ...b, x: SLOTS[i][0], y: SLOTS[i][1], tone: TONES[i % TONES.length] })))
const earned = computed(() => props.badges.filter((b) => b.earned).length)
const current = computed(() => props.badges.find((b) => b.id === selected.value) ?? null)
</script>

<template>
  <svg class="tree" viewBox="0 0 200 240" role="img" :aria-label="`Merker: ${earned} av ${badges.length} opptjent`">
    <defs>
      <radialGradient id="bauble-shine" cx="35%" cy="30%" r="70%">
        <stop offset="0" stop-color="#fff" stop-opacity="0.55" />
        <stop offset="0.5" stop-color="#fff" stop-opacity="0" />
      </radialGradient>
    </defs>
    <!-- Trunk and three tiers of pine -->
    <rect x="92" y="208" width="16" height="22" rx="3" fill="#5a3a24" />
    <polygon points="100,118 32,212 168,212" fill="var(--pine)" />
    <polygon points="100,70 44,160 156,160" fill="var(--pine)" />
    <polygon points="100,28 56,108 144,108" fill="var(--pine)" />
    <!-- Star -->
    <polygon
      points="100,8 104.5,20 117,20 107,27.5 110.5,40 100,32.5 89.5,40 93,27.5 83,20 95.5,20"
      :fill="earned === badges.length && badges.length > 0 ? 'var(--candle)' : 'var(--brass)'"
      :style="earned === badges.length && badges.length > 0 ? 'filter: drop-shadow(0 0 6px var(--glow))' : ''"
    />
    <!-- Baubles -->
    <g v-for="b in items" :key="b.id" class="bauble" :class="{ earned: b.earned, selected: selected === b.id }" role="button" tabindex="0"
       :aria-label="`${b.title}: ${b.earned ? 'opptjent' : 'ikke ennå'}`"
       @click="selected = selected === b.id ? null : b.id" @keydown.enter.prevent="selected = selected === b.id ? null : b.id">
      <line :x1="b.x" :y1="b.y - 16" :x2="b.x" :y2="b.y - 9" stroke="var(--brass)" stroke-width="1.5" />
      <rect :x="b.x - 3" :y="b.y - 11" width="6" height="4" rx="1" fill="var(--brass)" />
      <circle :cx="b.x" :cy="b.y" r="10" :fill="b.earned ? b.tone : 'var(--card)'" :stroke="b.earned ? 'none' : 'var(--muted)'" stroke-width="1" :stroke-dasharray="b.earned ? '' : '2 2'" :opacity="b.earned ? 1 : 0.75" />
      <circle v-if="b.earned" :cx="b.x" :cy="b.y" r="10" fill="url(#bauble-shine)" />
      <circle :cx="b.x" :cy="b.y" r="14" fill="transparent" />
    </g>
  </svg>
  <p class="muted" style="text-align: center; margin: 4px 0 8px">{{ earned }} av {{ badges.length }} kuler på treet. Trykk på en kule.</p>
  <div v-if="current" class="card" style="margin-top: 0">
    <strong class="display">{{ current.earned ? '★' : '☆' }} {{ current.title }}</strong>
    <div class="muted">{{ current.description }}</div>
  </div>
</template>
