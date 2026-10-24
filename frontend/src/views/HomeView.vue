<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get } from '../api/client'
import type { Beer } from '../api/types'
import { useMe } from '../stores/me'

const store = useMe()
const leastTasted = ref<Beer[]>([])

onMounted(async () => {
  leastTasted.value = await get<Beer[]>('/api/beers/least-tasted?limit=5').catch(() => [])
})
</script>

<template>
  <h1>Cantaras juleølsmaking {{ store.me?.season.year ?? '' }}</h1>

  <div v-if="store.me" class="card">
    <div class="row">
      <strong>Hei, {{ store.me.displayName }}</strong>
      <span v-if="!store.me.status.inSeason" class="pill">Utenfor sesong</span>
    </div>
    <p class="muted" style="margin: 6px 0 2px">I dag: {{ store.me.status.tastingsToday }} av {{ store.me.status.maxPerDay }} smakinger</p>
    <div class="meter"><span :style="{ width: (100 * store.me.status.tastingsToday) / store.me.status.maxPerDay + '%' }" /></div>
    <p class="muted" style="margin: 10px 0 2px">Solo-dager brukt: {{ store.me.status.soloDaysUsed }} av {{ store.me.status.maxSoloDays }}</p>
    <div class="meter"><span :style="{ width: (100 * store.me.status.soloDaysUsed) / store.me.status.maxSoloDays + '%' }" /></div>
    <p class="muted" style="margin-top: 10px">Sesongen varer {{ store.me.season.start }} til {{ store.me.season.end }}.</p>
  </div>
  <div v-else class="card"><p class="muted">Logger inn …</p></div>

  <div class="row" style="margin: 16px 0">
    <router-link class="btn" to="/session" style="flex: 1; text-align: center">Start smaking</router-link>
    <router-link class="btn secondary" to="/join" style="flex: 1; text-align: center">Bli med</router-link>
  </div>

  <h2>Minst smakt</h2>
  <p class="muted">Disse trenger kjærlighet. Explorer-merket venter.</p>
  <div v-for="b in leastTasted" :key="b.id" class="card row">
    <div>
      <strong>{{ b.name }}</strong>
      <div class="muted">{{ b.brewery }} · {{ b.ratingCount }} smakinger</div>
    </div>
    <router-link class="btn secondary" :to="`/beers/${b.id}/rate`">Smak</router-link>
  </div>
</template>
