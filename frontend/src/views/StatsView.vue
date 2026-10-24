<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get } from '../api/client'
import type { BeerRank, DuelRank, HistoricSeason } from '../api/types'

const beers = ref<BeerRank[]>([])
const duels = ref<DuelRank[]>([])
const seasons = ref<HistoricSeason[]>([])

onMounted(async () => {
  ;[beers.value, duels.value, seasons.value] = await Promise.all([
    get<BeerRank[]>('/api/stats/beers'),
    get<DuelRank[]>('/api/stats/duels').catch(() => []),
    get<HistoricSeason[]>('/api/history/seasons').catch(() => []),
  ])
})
</script>

<template>
  <h1>Resultater</h1>
  <p><router-link to="/finale">★ Til finalen</router-link></p>
  <p class="muted">Offisiell liste: terningkast 1–6 vektet etter hver smakers vekt, og trukket mot sesongsnittet til et øl har fått nok stemmer.</p>

  <div class="card">
    <table>
      <thead><tr><th>#</th><th>Øl</th><th>Snitt</th><th>Justert</th><th>Smakere</th></tr></thead>
      <tbody>
        <tr v-for="r in beers" :key="r.beerId">
          <td>{{ r.rank }}</td>
          <td>{{ r.name }}<div class="muted">{{ r.brewery }}</div></td>
          <td>{{ r.mean.toFixed(2) }}</td>
          <td><strong>{{ r.bayesian.toFixed(2) }}</strong></td>
          <td>{{ r.ratings }}<div class="muted">{{ r.effectiveVotes.toFixed(1) }} vekt</div></td>
        </tr>
      </tbody>
    </table>
    <p v-if="!beers.length" class="muted">Ingen smakinger ennå.</p>
  </div>

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
