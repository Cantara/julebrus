<script setup lang="ts">
/** A string of lights: one bulb per allowed unit, lit for each one used. */
defineProps<{ lit: number; total: number; tone?: 'warm' | 'solo'; label: string }>()
</script>

<template>
  <div class="lights" role="img" :aria-label="`${label}: ${lit} av ${total}`">
    <template v-for="i in total" :key="i">
      <span class="wire" v-if="i > 1" />
      <span class="bulb" :class="[tone ?? 'warm', { lit: i <= lit }]" />
    </template>
  </div>
</template>
