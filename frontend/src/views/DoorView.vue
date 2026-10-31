<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiFailure, get, post } from '../api/client'
import type { AdventDoor, AdventOverview } from '../api/types'

/**
 * One advent door. Shut: the day's question, answered with one tap (a late door costs a nisse-key
 * after the first days). Open: right or wrong with the explanation, a tasting tip, a shelf beer few
 * have tried, a clue about the leader in the last week, and how the house and the teams are doing.
 */
const route = useRoute()
const router = useRouter()
const door = ref<AdventDoor | null>(null)
const overview = ref<AdventOverview | null>(null)
const error = ref('')
const busy = ref(false)
const justOpened = ref(false)

const ERRORS: Record<string, string> = {
  DOOR_LOCKED: 'Denne luka åpner seg på sin dag.',
  DOOR_NOT_FOUND: 'Det er ingen luke den dagen.',
  NO_KEY: 'Du har ingen nisse-nøkkel. Smak ved et bord sammen med noen, så får du en.',
  DOOR_OPENED: 'Du har allerede åpnet denne luka.',
  DOOR_ANSWER: 'Velg et av svarene.',
}

async function load() {
  error.value = ''
  door.value = null
  justOpened.value = false
  try {
    overview.value = await get<AdventOverview>('/api/advent')
    const param = String(route.params.day)
    const day = param === 'idag' ? overview.value.today : param
    door.value = await get<AdventDoor>(`/api/advent/doors/${day}`)
  } catch (e) {
    error.value = e instanceof ApiFailure ? ERRORS[e.code] ?? e.message : 'Kunne ikke hente luka.'
  }
}
watch(() => route.params.day, load, { immediate: true })

async function open(answer: number | null) {
  if (!door.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    door.value = await post<AdventDoor>(`/api/advent/doors/${door.value.day}/open`, { answer })
    justOpened.value = true
    overview.value = await get<AdventOverview>('/api/advent')
  } catch (e) {
    error.value = e instanceof ApiFailure ? ERRORS[e.code] ?? e.message : 'Noe gikk galt.'
  } finally {
    busy.value = false
  }
}

const dateText = computed(() =>
  door.value ? new Date(door.value.day + 'T12:00:00').toLocaleDateString('nb-NO', { weekday: 'long', day: 'numeric', month: 'long' }) : '',
)
const blocked = computed(() => door.value?.state === 'NEEDS_KEY' && door.value.keysAvailable < 1)
const isToday = computed(() => !!door.value && !!overview.value && door.value.day === overview.value.today)

function shift(day: string, by: number) {
  const d = new Date(day + 'T00:00:00Z')
  d.setUTCDate(d.getUTCDate() + by)
  return d.toISOString().slice(0, 10)
}
const prev = computed(() => (door.value && overview.value && shift(door.value.day, -1) >= overview.value.start ? shift(door.value.day, -1) : null))
const next = computed(() => (door.value && overview.value && shift(door.value.day, 1) <= overview.value.today ? shift(door.value.day, 1) : null))

function optionClass(i: number) {
  const a = door.value?.answer
  if (!a) return ''
  if (i === a.answer) return 'right'
  if (i === a.chosen) return 'wrong'
  return 'dim'
}
const housePct = computed(() => (overview.value ? Math.min(100, Math.round((overview.value.house.opened / overview.value.house.goal) * 100)) : 0))
</script>

<template>
  <router-link to="/" class="muted">‹ Kalenderen</router-link>
  <p v-if="error" class="error" role="alert">{{ error }}</p>

  <template v-if="door">
    <h1 style="margin-bottom: 2px">Luke {{ door.number }}</h1>
    <p class="muted" style="margin-top: 0">{{ dateText }}<template v-if="door.late"> · åpnet i ettertid<template v-if="door.keyUsed"> med en nisse-nøkkel</template></template></p>

    <div v-if="door.releaseDay" class="card banner">🍾 <strong>Slippdagen!</strong> Vinmonopolet fyller hyllene med årets juleøl i dag.</div>
    <div v-if="door.finaleDay" class="card banner">★ <strong>Finaledag.</strong> Stjernen tennes i kveld. <router-link class="lnk" to="/finale">Til finalen</router-link></div>

    <div class="card door-card" :class="{ swing: justOpened }">
      <template v-if="door.state !== 'OPENED'">
        <p v-if="door.state === 'FREE_LATE'" class="muted" style="margin-top: 0">Denne gikk du glipp av, men de første lukene kan åpnes i ettertid uten nøkkel.</p>
        <p v-else-if="door.state === 'NEEDS_KEY'" class="muted" style="margin-top: 0">
          Denne luka gikk du glipp av. En nisse-nøkkel åpner den, og du har {{ door.keysAvailable }}.
          <template v-if="blocked">Du får en nøkkel for hver dag du smaker ved et bord sammen med noen.</template>
        </p>
        <template v-if="door.question">
          <h2 class="display" style="margin: 4px 0 12px">{{ door.question.text }}</h2>
          <div class="options">
            <button v-for="(o, i) in door.question.options" :key="o" class="secondary" :disabled="busy || blocked" @click="open(i)">{{ o }}</button>
          </div>
          <p class="muted" style="margin: 10px 0 0">Ett trykk åpner luka. Svaret ditt ser bare du.</p>
        </template>
        <button v-else :disabled="busy || blocked" @click="open(null)">Åpne luka</button>
      </template>
      <template v-else>
        <template v-if="door.question && door.answer">
          <p class="result display">{{ door.answer.correct ? '✓ Riktig!' : '✗ Ikke helt.' }}</p>
          <h2 class="display" style="margin: 4px 0 12px">{{ door.question.text }}</h2>
          <ul class="options answered">
            <li v-for="(o, i) in door.question.options" :key="o" :class="optionClass(i)">{{ o }}</li>
          </ul>
          <p class="muted" style="margin: 10px 0 0">{{ door.answer.explanation }}</p>
        </template>
        <p v-else class="result display">Luka er åpnet.</p>
      </template>
    </div>

    <template v-if="door.state === 'OPENED'">
      <div v-if="door.clue" class="card">
        <strong class="display">🕵️ Spor om lederen</strong>
        <p style="margin: 6px 0 0">{{ door.clue }}</p>
        <p class="muted" style="margin: 6px 0 0">Resultatlista er mørkelagt til stjernen tennes. Sporet gjelder lederen akkurat nå.</p>
      </div>
      <div v-if="door.tip" class="card">
        <strong class="display">🍺 Smakstips</strong>
        <p style="margin: 6px 0 0">{{ door.tip }}</p>
      </div>
      <router-link v-if="door.spotlight" class="card row" :to="`/beers/${door.spotlight.id}`" style="text-decoration: none">
        <div>
          <strong class="display">🔍 Dagens ukjente</strong>
          <div class="muted">
            {{ door.spotlight.name }}<template v-if="door.spotlight.brewery"> · {{ door.spotlight.brewery }}</template> ·
            {{ door.spotlight.tastings === 0 ? 'ingen har smakt den ennå' : `${door.spotlight.tastings} har smakt den` }}
          </div>
        </div>
        <span class="muted">›</span>
      </router-link>
    </template>

    <div v-if="overview" class="card">
      <p style="margin: 0">
        🕯️ {{ overview.streak }} {{ overview.streak === 1 ? 'luke' : 'luker' }} på rad · 🗝️ {{ overview.keys.available }}
        {{ overview.keys.available === 1 ? 'nisse-nøkkel' : 'nisse-nøkler' }} · {{ overview.correct }} {{ overview.correct === 1 ? 'riktig svar' : 'riktige svar' }}
      </p>
      <div v-if="isToday && overview.teams.length" style="margin-top: 10px">
        <div v-for="t in overview.teams" :key="t.slug" class="race">
          <span>{{ t.name }}</span>
          <span class="bar"><span :style="{ width: `${Math.round((t.openedToday / t.members) * 100)}%` }" /></span>
          <span class="muted">{{ t.openedToday }} av {{ t.members }}</span>
        </div>
      </div>
      <p class="muted" style="margin: 10px 0 4px">⭐ Huset har åpnet {{ overview.house.opened }} av {{ overview.house.goal }} luker sammen<template v-if="overview.house.unlocked">. Stjernen er tent!</template></p>
      <span class="bar wide"><span :style="{ width: `${housePct}%` }" /></span>
    </div>

    <div class="row" style="margin-top: 8px">
      <router-link v-if="prev" class="btn secondary" :to="`/luke/${prev}`">‹ Forrige luke</router-link><span v-else />
      <router-link v-if="next" class="btn secondary" :to="`/luke/${next}`">Neste luke ›</router-link>
    </div>
  </template>
  <p v-else-if="!error" class="muted">Henter luka …</p>
  <button v-if="error" class="secondary" style="margin-top: 8px" @click="router.push('/')">Til kalenderen</button>
</template>

<style scoped>
.banner { border: 1px dashed var(--brass); }
.options { display: grid; gap: 8px; padding: 0; margin: 0; list-style: none; }
.options button { text-align: left; }
.options.answered li { padding: 10px 12px; border-radius: 10px; border: 1px solid var(--line); }
.options.answered li.right { border-color: var(--ok); color: var(--ok); font-weight: 600; }
.options.answered li.right::before { content: '✓ '; }
.options.answered li.wrong { border-color: var(--danger); color: var(--danger); text-decoration: line-through; }
.options.answered li.dim { opacity: 0.6; }
.result { font-size: 1.3rem; margin: 0 0 4px; }
.race { display: grid; grid-template-columns: 6.5rem 1fr auto; gap: 8px; align-items: center; margin: 4px 0; font-size: 0.9rem; }
.bar { display: block; height: 8px; border-radius: 4px; background: var(--door-shut); overflow: hidden; }
.bar > span { display: block; height: 100%; background: var(--candle); border-radius: 4px; }
.bar.wide { width: 100%; }
.door-card.swing { animation: swing 0.6s ease-out; transform-origin: left center; }
@keyframes swing { from { transform: perspective(600px) rotateY(-70deg); opacity: 0.3; } to { transform: none; opacity: 1; } }
@media (prefers-reduced-motion: reduce) { .door-card.swing { animation: none; } }
</style>
