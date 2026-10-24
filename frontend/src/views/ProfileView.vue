<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get } from '../api/client'
import type { BadgeView, Beer, HistoricRating, Tasting } from '../api/types'
import BadgeTree from '../components/BadgeTree.vue'
import { useMe } from '../stores/me'

const store = useMe()
const badges = ref<BadgeView[]>([])
const mine = ref<Tasting[]>([])
const history = ref<HistoricRating[]>([])
const beerNames = ref<Record<string, string>>({})

onMounted(async () => {
  const [b, t, h, beers] = await Promise.all([
    get<BadgeView[]>('/api/badges/me'),
    get<Tasting[]>('/api/tastings/mine'),
    get<HistoricRating[]>('/api/history/me').catch(() => []),
    get<Beer[]>('/api/beers'),
  ])
  badges.value = b
  mine.value = t
  history.value = h
  beerNames.value = Object.fromEntries(beers.map((x) => [x.id, x.name]))
})

const byYear = () => {
  const m = new Map<number, HistoricRating[]>()
  for (const h of history.value) m.set(h.year, [...(m.get(h.year) ?? []), h])
  return [...m.entries()].sort((a, b) => b[0] - a[0])
}
</script>

<template>
  <h1>{{ store.me?.displayName ?? 'Meg' }}</h1>
  <p class="muted">{{ store.me?.email }} · <a href="/api/logout">Logg ut</a></p>

  <div v-if="store.me" class="card">
    <div class="row">
      <strong>Din vekt i år</strong>
      <span class="pill on">{{ store.me.weight.weight }}</span>
    </div>
    <p class="muted" style="margin: 6px 0 0">{{ store.me.weight.reason }}</p>
    <p class="muted" style="margin: 6px 0 0">Nye starter på 0,5. En vanlig sesong med minst sju terningkast gir 1,0 neste år. Juks gir 0, men du kan fortsatt smake og stemme.</p>
  </div>

  <h2>Merker</h2>
  <div class="card">
    <BadgeTree :badges="badges" />
    <details>
      <summary class="muted" style="cursor: pointer">Alle merker som liste</summary>
      <div v-for="b in badges" :key="b.id" class="row" style="padding: 6px 0" :style="{ opacity: b.earned ? 1 : 0.5 }">
        <div><strong>{{ b.earned ? '★' : '☆' }} {{ b.title }}</strong><div class="muted">{{ b.description }}</div></div>
      </div>
    </details>
  </div>

  <h2>Mine smakinger i år</h2>
  <div class="card">
    <table>
      <tbody>
        <tr v-for="t in mine" :key="t.id">
          <td>{{ beerNames[t.beerId] ?? '?' }}<div v-if="t.tags.length" class="muted">{{ t.tags.join(', ') }}</div></td>
          <td style="text-align: right"><strong>{{ t.score }}</strong></td>
        </tr>
      </tbody>
    </table>
    <p v-if="!mine.length" class="muted">Ingenting ennå.</p>
  </div>

  <h2>Mine historiske scorer</h2>
  <p class="muted">Fra tidligere års smakinger, med den vektingen som gjaldt da.</p>
  <div v-for="[year, rows] in byYear()" :key="year" class="card">
    <strong>{{ year }}</strong>
    <table>
      <thead><tr><th>Øl</th><th>Score</th><th>Vekt</th><th>På 1–6</th></tr></thead>
      <tbody>
        <tr v-for="r in rows" :key="r.beerName">
          <td>{{ r.beerName }}<div v-if="r.notes" class="muted">{{ r.notes }}</div></td>
          <td>{{ r.score }}</td><td>{{ r.weight }}</td><td>{{ r.normalised.toFixed(1) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-if="!history.length" class="muted">Ingen historikk koblet til {{ store.me?.email }} ennå.</p>
</template>
