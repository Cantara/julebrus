<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get } from '../api/client'
import type { AllTimeRow, BeerSeason, HistoricRating, SeasonOverview } from '../api/types'
import BarChart from '../components/BarChart.vue'

const route = useRoute()
const router = useRouter()
const overview = ref<SeasonOverview[]>([])
const year = ref<number | null>(null)
const results = ref<BeerSeason[]>([])
const allTime = ref<AllTimeRow[]>([])
const allTimeOpen = ref(false)
const beerHistory = ref<{ name: string; rows: HistoricRating[] } | null>(null)
const loading = ref(false)

onMounted(async () => {
  get<AllTimeRow[]>('/api/history/alltime').then((r) => (allTime.value = r)).catch(() => {})
  overview.value = await get<SeasonOverview[]>('/api/history/overview').catch(() => [])
  const q = Number(route.query.year)
  year.value = overview.value.some((s) => s.year === q) ? q : (overview.value[0]?.year ?? null)
})

watch(year, async (y) => {
  beerHistory.value = null
  if (!y) return
  loading.value = true
  router.replace({ query: { year: String(y) } })
  results.value = await get<BeerSeason[]>(`/api/history/seasons/${y}`).catch(() => [])
  loading.value = false
})

const season = computed(() => overview.value.find((s) => s.year === year.value) ?? null)
const chronological = computed(() => [...overview.value].sort((a, b) => a.year - b.year))
const beersPerYear = computed(() => chronological.value.map((s) => ({ label: String(s.year), value: s.beers })))
const ratersPerYear = computed(() => chronological.value.map((s) => ({ label: String(s.year), value: s.raters })))

async function showBeer(name: string) {
  if (beerHistory.value?.name === name) {
    beerHistory.value = null
    return
  }
  const rows = await get<HistoricRating[]>(`/api/history/beers?name=${encodeURIComponent(name)}`).catch(() => [])
  beerHistory.value = { name, rows }
}
const beerByYear = computed(() => {
  if (!beerHistory.value) return []
  const m = new Map<number, HistoricRating[]>()
  for (const r of beerHistory.value.rows) m.set(r.year, [...(m.get(r.year) ?? []), r])
  return [...m.entries()].sort((a, b) => b[0] - a[0]).map(([y, rs]) => ({
    year: y,
    n: rs.length,
    mean: rs.reduce((s, r) => s + r.normalised, 0) / rs.length,
  }))
})
</script>

<template>
  <h1>Tidligere år</h1>
  <p class="muted">Arkivet fra wikien og regnearkene, med den vektingen som gjaldt hvert år. Alt vist på 1–6.</p>

  <h2 style="margin-top: 8px">Gjennom alle år</h2>
  <p class="muted">Alle terningkast siden 1998 på 1–6, vektet slik hvert år vektet dem, og trukket mot alle års snitt til et øl har fått nok stemmer. Minst fem stemmer.</p>
  <div class="card">
    <table>
      <thead><tr><th>#</th><th>Øl</th><th style="text-align: right">Justert</th><th style="text-align: right">Stemmer</th></tr></thead>
      <tbody>
        <tr v-for="r in (allTimeOpen ? allTime : allTime.slice(0, 10))" :key="r.beerName" style="cursor: pointer" @click="showBeer(r.beerName)">
          <td>{{ r.rank }}</td>
          <td>{{ r.beerName }}<div class="muted">{{ [r.brewery, r.years.length === 1 ? String(r.years[0]) : `${r.years.length} sesonger, best ${r.bestYear} (${r.bestMean.toFixed(2)})`].filter(Boolean).join(' · ') }}</div></td>
          <td style="text-align: right"><strong>{{ r.bayesian.toFixed(2) }}</strong><div class="muted">snitt {{ r.mean.toFixed(2) }}</div></td>
          <td style="text-align: right">{{ r.ratings }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!allTime.length" class="muted">Ingen historikk lastet.</p>
    <button v-else-if="allTime.length > 10" class="secondary" style="width: 100%; margin-top: 8px" @click="allTimeOpen = !allTimeOpen">{{ allTimeOpen ? 'Vis topp 10' : `Vis alle ${allTime.length}` }}</button>
  </div>

  <h2>Sesong for sesong</h2>
  <div class="card" style="overflow-x: auto; white-space: nowrap; padding: 10px 12px">
    <button v-for="s in overview" :key="s.year" type="button" class="pill" :class="{ on: s.year === year }" style="cursor: pointer; border: 0" @click="year = s.year">{{ s.year }}</button>
  </div>

  <template v-if="season">
    <div class="card">
      <div class="row">
        <strong class="display">{{ season.title }}</strong>
        <a v-if="season.sourceUrl" :href="season.sourceUrl" class="muted" target="_blank" rel="noopener">Kilde</a>
      </div>
      <p class="muted" style="margin: 4px 0 0">{{ season.raters }} smakere · {{ season.beers }} øl · {{ season.votes }} terningkast</p>
      <p v-if="season.weightingDescription" class="muted" style="margin: 4px 0 0">{{ season.weightingDescription }}</p>
      <p v-if="season.winnerName" style="margin: 8px 0 0">
        <strong class="display">★ {{ season.winnerName }}</strong>
        <span class="muted"> · {{ season.winnerWeightedMean?.toFixed(2) }}</span>
      </p>
    </div>

    <div class="card">
      <table>
        <thead><tr><th>#</th><th>Øl</th><th>Snitt</th><th>Stemmer</th></tr></thead>
        <tbody>
          <tr v-for="(r, i) in results" :key="r.beerName" style="cursor: pointer" @click="showBeer(r.beerName)">
            <td>{{ i + 1 }}</td>
            <td>{{ r.beerName }}<div v-if="r.brewery" class="muted">{{ r.brewery }}</div></td>
            <td><strong>{{ r.weightedMean.toFixed(2) }}</strong></td>
            <td>{{ r.ratings }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="loading" class="muted">Henter …</p>
      <p class="muted" style="margin: 8px 0 0">Trykk på et øl for å se alle årene det har vært med.</p>
    </div>

    <div v-if="beerHistory" class="card">
      <strong class="display">{{ beerHistory.name }} gjennom årene</strong>
      <table>
        <thead><tr><th>År</th><th>Snitt</th><th>Stemmer</th></tr></thead>
        <tbody>
          <tr v-for="b in beerByYear" :key="b.year"><td>{{ b.year }}</td><td>{{ b.mean.toFixed(2) }}</td><td>{{ b.n }}</td></tr>
        </tbody>
      </table>
    </div>
  </template>

  <h2>Gjennom årene</h2>
  <div class="card"><BarChart :points="beersPerYear" title="Øl på bordet per sesong" /></div>
  <div class="card"><BarChart :points="ratersPerYear" title="Smakere per sesong" /></div>

  <h2>Vinnere</h2>
  <div class="card">
    <table>
      <tbody>
        <tr v-for="s in overview" :key="s.year" style="cursor: pointer" @click="year = s.year">
          <td>{{ s.year }}</td>
          <td><strong>{{ s.winnerName ?? '–' }}</strong></td>
          <td style="text-align: right">{{ s.winnerWeightedMean?.toFixed(2) ?? '' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-if="!overview.length" class="muted">Ingen historikk lastet.</p>
</template>
