<script setup lang="ts">
/**
 * A beer from the archive, by name: its place on the all-time list, every season it was
 * on the table with the votes as the old sheets recorded them, and the way to this year's
 * page when the same name is on the shelf again. Public history, nothing from this season.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { get } from '../api/client'
import type { AllTimeRow, Beer, HistoricRating } from '../api/types'

const props = defineProps<{ name: string }>()
const rows = ref<HistoricRating[]>([])
const allTime = ref<AllTimeRow[]>([])
const current = ref<Beer | null>(null)
const loaded = ref(false)

async function load() {
  loaded.value = false
  const name = props.name
  const [r, a, beers] = await Promise.all([
    get<HistoricRating[]>(`/api/history/beers?name=${encodeURIComponent(name)}`).catch(() => []),
    get<AllTimeRow[]>('/api/history/alltime?min=1').catch(() => []),
    get<Beer[]>(`/api/beers?q=${encodeURIComponent(name)}`).catch(() => []),
  ])
  rows.value = r
  allTime.value = a
  current.value = beers.find((b) => b.name.localeCompare(name, undefined, { sensitivity: 'accent' }) === 0 && b.status !== 'REJECTED') ?? null
  loaded.value = true
}
onMounted(load)
watch(() => props.name, load)

const brewery = computed(() => rows.value.find((r) => r.brewery)?.brewery ?? null)
const mine = computed(() => allTime.value.find((r) => r.beerName.localeCompare(props.name, undefined, { sensitivity: 'accent' }) === 0) ?? null)
const ranked = computed(() => allTime.value.filter((r) => r.ratings >= 5))
const rankAmongRanked = computed(() => {
  const i = ranked.value.findIndex((r) => r.beerName === mine.value?.beerName)
  return i >= 0 ? i + 1 : null
})
const byYear = computed(() => {
  const m = new Map<number, HistoricRating[]>()
  for (const r of rows.value) m.set(r.year, [...(m.get(r.year) ?? []), r])
  return [...m.entries()].sort((a, b) => b[0] - a[0]).map(([year, rs]) => {
    const w = rs.reduce((s, r) => s + r.weight, 0)
    const mean = w ? rs.reduce((s, r) => s + r.normalised * r.weight, 0) / w : 0
    return { year, rows: rs, mean, n: rs.length }
  })
})
</script>

<template>
  <p class="muted"><router-link to="/historikk">‹ Tidligere år</router-link></p>
  <h1 style="margin-bottom: 2px">{{ name }}</h1>
  <p v-if="brewery" class="muted" style="margin-top: 0">{{ brewery }}</p>

  <router-link v-if="current" class="card row" :to="`/beers/${current.id}`" style="text-decoration: none">
    <div>
      <strong class="display">På hylla i år</strong>
      <div class="muted">{{ current.ratingCount }} terningkast så langt. Se årets side med fordeling, stikkord og notater.</div>
    </div>
    <span class="muted">›</span>
  </router-link>

  <div v-if="mine" class="card">
    <div class="row" style="align-items: baseline">
      <div>
        <strong class="display">{{ rankAmongRanked ? `Nr. ${rankAmongRanked} av ${ranked.length} gjennom alle år` : 'Gjennom alle år' }}</strong>
        <div class="muted">
          {{ mine.ratings }} terningkast over {{ mine.years.length }} {{ mine.years.length === 1 ? 'sesong' : 'sesonger' }} · snitt {{ mine.mean.toFixed(2) }}
          <template v-if="mine.years.length > 1"> · best i {{ mine.bestYear }} ({{ mine.bestMean.toFixed(2) }})</template>
        </div>
        <div v-if="!rankAmongRanked" class="muted">Alle-tiders-listen krever fem stemmer. {{ 5 - mine.ratings }} til.</div>
      </div>
      <strong class="display" style="font-size: 1.6rem">{{ mine.bayesian.toFixed(2) }}</strong>
    </div>
  </div>

  <template v-if="byYear.length">
    <h2>Sesong for sesong</h2>
    <p class="muted">Slik regnearkene førte det, med hver smakers vekt det året. Alt vist på 1–6.</p>
    <div v-for="y in byYear" :key="y.year" class="card">
      <div class="row">
        <strong class="display"><router-link :to="`/historikk?year=${y.year}`" style="text-decoration: none">{{ y.year }}</router-link></strong>
        <span class="muted">snitt {{ y.mean.toFixed(2) }} · {{ y.n }} {{ y.n === 1 ? 'stemme' : 'stemmer' }}</span>
      </div>
      <table>
        <thead><tr><th>Hvem</th><th style="text-align: right">Score</th><th style="text-align: right">Vekt</th><th style="text-align: right">På 1–6</th></tr></thead>
        <tbody>
          <tr v-for="(r, i) in y.rows" :key="i">
            <td>{{ r.raterKey || r.raterName }}<div v-if="r.notes" class="muted">{{ r.notes }}</div></td>
            <td style="text-align: right">{{ r.score }}</td><td style="text-align: right">{{ r.weight }}</td><td style="text-align: right">{{ r.normalised.toFixed(1) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>
  <p v-else-if="loaded" class="muted card">Ingen stemmer funnet på dette navnet i arkivet.</p>
</template>
