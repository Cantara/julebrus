<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ApiFailure, get } from '../api/client'
import type { AdventDoor, AdventOverview, BeerRank, DuelRank, HistoricSeason, Team } from '../api/types'
import CandleRoom from '../components/CandleRoom.vue'
import { fx } from '../effects'
import { useMe } from '../stores/me'

const beers = ref<BeerRank[]>([])
const duels = ref<DuelRank[]>([])
const seasons = ref<HistoricSeason[]>([])
const teams = ref<Team[]>([])
/** Empty means everyone; otherwise one team's raters only. Arrives preset from a team page. */
const route = useRoute()
const team = ref(typeof route.query.team === 'string' ? route.query.team : '')

const store = useMe()
/** The last week before the finale the ranking is hidden ("mørkelagt"); an admin may still look. */
const dark = ref(false)
const preview = ref(false)
const advent = ref<AdventOverview | null>(null)
/** The clues the doors have given since the ranking went dark, one per day; null for a door not opened yet. */
const clues = ref<{ day: string; number: number; clue: string | null }[]>([])

async function loadClues() {
  const a = advent.value
  if (!a?.blackoutFrom) return
  const days: string[] = []
  for (let d = new Date(a.blackoutFrom + 'T00:00:00Z'); ; d.setUTCDate(d.getUTCDate() + 1)) {
    const day = d.toISOString().slice(0, 10)
    if (day > a.today || day >= a.end) break
    days.push(day)
  }
  const doors = await Promise.all(days.map((d) => get<AdventDoor>(`/api/advent/doors/${d}`).catch(() => null)))
  clues.value = doors.filter((d): d is AdventDoor => !!d).map((d) => ({ day: d.day, number: d.number, clue: d.clue }))
}

function query(extra: Record<string, string>) {
  const q = new URLSearchParams(extra)
  if (preview.value) q.set('preview', 'true')
  const s = q.toString()
  return s ? `?${s}` : ''
}
async function loadBeers() {
  try {
    beers.value = await get<BeerRank[]>('/api/stats/beers' + query(team.value ? { team: team.value } : {}))
    dark.value = false
  } catch (e) {
    if (e instanceof ApiFailure && e.code === 'BLACKOUT') {
      dark.value = true
      beers.value = []
      if (!advent.value) {
        advent.value = await get<AdventOverview>('/api/advent').catch(() => null)
        loadClues().catch(() => {})
      }
    } else throw e
  }
}
async function loadDuels() {
  duels.value = await get<DuelRank[]>('/api/stats/duels' + query({})).catch(() => [])
}
watch(team, () => loadBeers().catch(() => {}))
async function peek() {
  preview.value = true
  await Promise.all([loadBeers(), loadDuels()])
}

onMounted(async () => {
  ;[, , seasons.value, teams.value] = await Promise.all([
    loadBeers(),
    loadDuels(),
    get<HistoricSeason[]>('/api/history/seasons').catch(() => []),
    get<Team[]>('/api/teams').catch(() => []),
  ])
})
</script>

<template>
  <h1>Resultater</h1>
  <p><router-link to="/finale">★ Til finalen</router-link></p>
  <p class="muted">Offisiell liste: terningkast 1–6 vektet etter hver smakers vekt, og trukket mot sesongsnittet til et øl har fått nok stemmer.</p>

  <div v-if="dark" class="card dark">
    <strong class="display">🌒 Mørkelagt til finalen</strong>
    <p style="margin: 6px 0 0">Den siste uka er resultatlista skjult for alle. Stjernen tennes {{ advent ? new Date(advent.revealAt).toLocaleString('nb-NO', { timeZone: 'Europe/Oslo', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }) : 'i finalen' }}.</p>
    <CandleRoom style="margin-top: 10px">
      <p class="muted" style="margin: 0 0 8px">{{ fx ? 'Dra flammen over arket og les sporene om hvem som leder.' : 'Sporene om hvem som leder, ett per luke.' }}</p>
      <ol class="clues">
        <li v-for="c in clues" :key="c.day">
          <strong>Luke {{ c.number }}</strong>
          <template v-if="c.clue"> · {{ c.clue }}</template>
          <template v-else> · <router-link :to="`/luke/${c.day}`">åpne luka for sporet</router-link></template>
        </li>
      </ol>
      <p v-if="!clues.length" class="muted" style="margin: 0">Første spor ligger bak dagens luke.</p>
      <p style="margin: 10px 0 0"><router-link to="/luke/idag">Til dagens luke</router-link></p>
    </CandleRoom>
    <template v-if="advent?.house.unlocked && advent.topTen.length">
      <p class="muted" style="margin: 10px 0 4px">Huset har tent stjernen. Topp ti akkurat nå, i alfabetisk rekkefølge:</p>
      <ul style="columns: 2; margin: 0; padding-left: 18px">
        <li v-for="b in advent.topTen" :key="b.id"><router-link class="lnk" :to="`/beers/${b.id}`">{{ b.name }}</router-link></li>
      </ul>
    </template>
    <button v-if="store.me?.admin" class="secondary" style="margin-top: 10px" @click="peek">Vis lista likevel (admin)</button>
  </div>

  <div v-if="teams.length && !dark" class="filter" role="group" aria-label="Lag">
    <button class="pill" :class="{ on: team === '' }" @click="team = ''">Alle</button>
    <button v-for="t in teams" :key="t.id" class="pill" :class="{ on: team === t.slug }" @click="team = t.slug">{{ t.name }}</button>
  </div>

  <ol class="ranked">
    <li v-for="r in beers" :key="r.beerId" class="card" :class="`r${Math.min(r.rank, 4)}`">
      <span class="rank display">{{ r.rank }}</span>
      <div class="body">
        <div class="row" style="align-items: baseline">
          <router-link :to="`/beers/${r.beerId}`" class="name"><strong>{{ r.name }}</strong></router-link>
          <strong class="display score">{{ r.bayesian.toFixed(2) }}</strong>
        </div>
        <div class="bar" role="img" :aria-label="`Justert ${r.bayesian.toFixed(2)} av 6`"><span :style="{ width: `${((r.bayesian - 1) / 5) * 100}%` }" /></div>
        <div class="muted small">{{ r.brewery }}<template v-if="r.brewery"> · </template>snitt {{ r.mean.toFixed(2) }} · {{ r.ratings }} {{ r.ratings === 1 ? 'smaker' : 'smakere' }}</div>
      </div>
    </li>
  </ol>
  <p v-if="!beers.length && !dark" class="muted card">{{ team ? 'Laget har ikke smakt ennå.' : 'Ingen smakinger ennå.' }}</p>

  <template v-if="!dark">
  <h2>Duellene (sideranking)</h2>
  <p class="muted">Parvise valg i gruppesmakinger. Bryr seg ikke om hvem som er snill eller streng med terningen.</p>
  <div class="card">
    <table>
      <thead><tr><th>#</th><th>Øl</th><th>Styrke</th></tr></thead>
      <tbody>
        <tr v-for="r in duels" :key="r.beerId">
          <td>{{ r.rank }}</td><td><router-link :to="`/beers/${r.beerId}`">{{ r.name }}</router-link></td><td>{{ r.strength.toFixed(2) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!duels.length" class="muted">Ingen dueller ennå.</p>
  </div>
  </template>

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
.ranked .name { display: block; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--brand); text-decoration: underline dotted; text-underline-offset: 3px; text-decoration-thickness: 1px; }
.ranked .score { color: var(--brand); }
.ranked .bar { height: 6px; background: var(--line); border-radius: 999px; overflow: hidden; }
.ranked .bar span { display: block; height: 100%; background: var(--brass); border-radius: 999px; }
.ranked .r1 .bar span { background: var(--candle); }
.ranked .small { font-size: 0.85rem; }
.filter { margin: 0 0 10px; }
.dark { border: 1px dashed var(--brass); }
.clues { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.filter .pill { border: 1px solid var(--line); cursor: pointer; font: inherit; font-size: 0.85rem; }
</style>
