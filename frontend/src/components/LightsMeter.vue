<script setup lang="ts">
/** A string of lights: one bulb per allowed unit, lit for each one used. Long strings (the 24 solo days, the 12 at a big table) get small bulbs that wrap. */
defineProps<{ lit: number; total: number; tone?: 'warm' | 'solo'; label: string }>()
const DENSE_FROM = 9
</script>

<template>
  <div class="lights" :class="{ dense: total >= DENSE_FROM }" role="img" :aria-label="`${label}: ${lit} av ${total}`">
    <template v-for="i in total" :key="i">
      <span class="wire" v-if="i > 1 && total < DENSE_FROM" />
      <span class="bulb" :class="[tone ?? 'warm', { lit: i <= lit }]" />
    </template>
  </div>
</template>
