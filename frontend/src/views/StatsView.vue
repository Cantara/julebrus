<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { get } from '../api/client'
import type { BeerRank, DuelRank, HistoricSeason, Team } from '../api/types'

const beers = ref<BeerRank[]>([])
const duels = ref<DuelRank[]>([])
const seasons = ref<HistoricSeason[]>([])
const teams = ref<Team[]>([])
/** Empty means everyone; otherwise one team's raters only. */
const team = ref('')

async function loadBeers() {
  beers.value = await get<BeerRank[]>('/api/stats/beers' + (team.value ? `?team=${encodeURIComponent(team.value)}` : ''))
}
watch(team, () => loadBeers().catch(() => {}))

onMounted(async () => {
  ;[, duels.value, seasons.value, teams.value] = await Promise.all([
    loadBeers(),
    get<DuelRank[]>('/api/stats/duels').catch(() => []),
    get<HistoricSeason[]>('/api/history/seasons').catch(() => []),
    get<Team[]>('/api/teams').catch(() => []),
  ])
})
</script>

<template>
  <h1>Resultater</h1>
  <p><router-link to="/finale">★ Til finalen</router-link></p>
  <p class="muted">Offisiell liste: terningkast 1–6 vektet etter hver smakers vekt, og trukket mot sesongsnittet til et øl har fått nok stemmer.</p>

  <div v-if="teams.length" class="filter" role="group" aria-label="Lag">
    <button class="pill" :class="{ on: team === '' }" @click="team = ''">Alle</button>
    <button v-for="t in teams" :key="t.id" class="pill" :class="{ on: team === t.slug }" @click="team = t.slug">{{ t.name }}</button>
  </div>

  <ol class="ranked">
    <li v-for="r in beers" :key="r.beerId" class="card" :class="`r${Math.min(r.rank, 4)}`">
      <span class="rank display">{{ r.rank }}</span>
      <div class="body">
        <div class="row" style="align-items: baseline">
          <strong class="name">{{ r.name }}</strong>
          <strong class="display score">{{ r.bayesian.toFixed(2) }}</strong>
        </div>
        <div class="bar" role="img" :aria-label="`Justert ${r.bayesian.toFixed(2)} av 6`"><span :style="{ width: `${((r.bayesian - 1) / 5) * 100}%` }" /></div>
        <div class="muted small">{{ r.brewery }}<template v-if="r.brewery"> · </template>snitt {{ r.mean.toFixed(2) }} · {{ r.ratings }} {{ r.ratings === 1 ? 'smaker' : 'smakere' }}</div>
      </div>
    </li>
  </ol>
  <p v-if="!beers.length" class="muted card">{{ team ? 'Laget har ikke smakt ennå.' : 'Ingen smakinger ennå.' }}</p>

  <h2>Duellene (sideranking)</h2>
  <p class="muted">Parvise valg i gruppesmakinger. Bryr seg ikke om hvem som er snill eller streng med terningen.</p>
  <div class="card">
    <table>
      <thead><tr><th>#</th><th>Øl</th><th>Styrke</th></tr></thead>
      <tbody>
        <tr v-for="r in duels" :key="r.beerId">
          <td>{{ r.rank }}</td><td>{{ r.name }}</td><td>{{ r.strength.toFixed(2) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!duels.length" class="muted">Ingen dueller ennå.</p>
  </div>

  <h2>Tidligere år</h2>
  <router-link class="card row" to="/historikk" style="text-decoration: none">
    <div>
      <strong class="display">{{ seasons.length }} sesonger i arkivet</strong>
      <div class="muted">Resultatlister, vinnere og grafer fra {{ seasons.length ? Math.min(...seasons.map((s) => s.year)) : '' }} og utover.</div>
    </div>
    <span class="muted">›</span>
  </router-link>
</template>

<style scoped>
.ranked { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.ranked li { display: flex; gap: 12px; align-items: center; margin: 0; }
.ranked .rank { flex: 0 0 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: var(--line); color: var(--ink); font-size: 1rem; }
.ranked .r1 .rank { background: var(--candle); color: #2b1a14; box-shadow: 0 0 10px var(--glow); }
.ranked .r2 .rank { background: #d9d4cc; color: #2b1a14; }
.ranked .r3 .rank { background: #c99a6b; color: #2b1a14; }
.ranked .body { flex: 1; min-width: 0; display: grid; gap: 4px; }
.ranked .name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ranked .score { color: var(--brand); }
.ranked .bar { height: 6px; background: var(--line); border-radius: 999px; overflow: hidden; }
.ranked .bar span { display: block; height: 100%; background: var(--brass); border-radius: 999px; }
.ranked .r1 .bar span { background: var(--candle); }
.ranked .small { font-size: 0.85rem; }
.filter { margin: 0 0 10px; }
.filter .pill { border: 1px solid var(--line); cursor: pointer; font: inherit; font-size: 0.85rem; }
</style>
