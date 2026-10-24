<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get } from '../api/client'
import type { Beer, MyTip, Session, Tasting } from '../api/types'
import AdventCalendar, { type DayEntry } from '../components/AdventCalendar.vue'
import LightsMeter from '../components/LightsMeter.vue'
import Snow from '../components/Snow.vue'
import { useMe } from '../stores/me'

const store = useMe()
const router = useRouter()
const leastTasted = ref<Beer[]>([])
const mine = ref<Tasting[]>([])
const tip = ref<MyTip | null>(null)
const beerName = ref<Record<string, string>>({})
const soloSessions = ref<Set<string>>(new Set())

const snow = ref(true)
try {
  snow.value = localStorage.getItem('snow') !== 'off'
} catch {
  /* private mode */
}
function toggleSnow() {
  snow.value = !snow.value
  try {
    localStorage.setItem('snow', snow.value ? 'on' : 'off')
  } catch {
    /* ignore */
  }
}

onMounted(async () => {
  get<MyTip>('/api/tips/me').then((t) => (tip.value = t)).catch(() => {})
  const [least, tastings, beers] = await Promise.all([
    get<Beer[]>('/api/beers/least-tasted?limit=5').catch(() => []),
    get<Tasting[]>('/api/tastings/mine').catch(() => []),
    get<Beer[]>('/api/beers').catch(() => []),
  ])
  leastTasted.value = least
  mine.value = tastings
  beerName.value = Object.fromEntries(beers.map((b) => [b.id, b.name]))
  // Solo means no session or a one-member session; ask about the sessions we were in.
  const sessionIds = [...new Set(tastings.map((t) => t.sessionId).filter((s): s is string => !!s))]
  const sessions = await Promise.all(sessionIds.map((id) => get<Session>(`/api/sessions/${id}`).catch(() => null)))
  soloSessions.value = new Set(sessions.filter((s): s is Session => !!s && s.solo).map((s) => s.id))
})

const days = computed<Record<string, DayEntry>>(() => {
  const out: Record<string, DayEntry> = {}
  for (const t of mine.value) {
    const entry = (out[t.tastedDay] ??= { beers: [], solo: false })
    entry.beers.push({ name: beerName.value[t.beerId] ?? '?', score: t.score })
    if (!t.sessionId || soloSessions.value.has(t.sessionId)) entry.solo = true
  }
  return out
})

const season = computed(() => store.me?.season)
const today = computed(() => store.me?.status.today ?? new Date().toISOString().slice(0, 10))

const daysUntilStart = computed(() => {
  if (!season.value) return 0
  const ms = new Date(season.value.start + 'T00:00:00Z').getTime() - new Date(today.value + 'T00:00:00Z').getTime()
  return Math.ceil(ms / 86400000)
})
const dayOfSeason = computed(() => {
  if (!season.value) return { n: 0, total: 0 }
  const day = (a: string) => new Date(a + 'T00:00:00Z').getTime() / 86400000
  return { n: Math.floor(day(today.value) - day(season.value.start)) + 1, total: Math.floor(day(season.value.end) - day(season.value.start)) + 1 }
})
</script>

<template>
  <Snow :enabled="snow" />

  <div class="row" style="align-items: baseline">
    <h1 style="margin-bottom: 4px">Juleølsmakingen {{ season?.year ?? '' }}</h1>
    <button class="secondary" style="padding: 6px 10px; font-size: 0.8rem" @click="toggleSnow">{{ snow ? 'Snø av' : 'Snø på' }}</button>
  </div>
  <p class="muted" style="margin-top: 0">
    <template v-if="daysUntilStart > 0">{{ daysUntilStart }} {{ daysUntilStart === 1 ? 'dag' : 'dager' }} til første luke.</template>
    <template v-else-if="store.me && !store.me.status.inSeason">Sesongen er over. Takk for i år.</template>
    <template v-else>Dag {{ dayOfSeason.n }} av {{ dayOfSeason.total }}.</template>
  </p>

  <router-link v-if="store.me && store.me.status.tastingsToday === 0 && !mine.length" class="card row" to="/historien" style="text-decoration: none">
    <div>
      <strong class="display">Ny her?</strong>
      <div class="muted">Les historien om juleølsmakingen, og hvorfor den varer hele adventstiden.</div>
    </div>
    <span class="muted">›</span>
  </router-link>

  <div v-if="store.me" class="card">
    <strong class="display">Hei, {{ store.me.displayName }}</strong>
    <p class="muted" style="margin: 8px 0 0">I dag</p>
    <LightsMeter :lit="store.me.status.tastingsToday" :total="store.me.status.maxPerDay" tone="warm" label="Smakinger i dag" />
    <p class="muted" style="margin: 8px 0 0">Solo-dager</p>
    <LightsMeter :lit="store.me.status.soloDaysUsed" :total="store.me.status.maxSoloDays" tone="solo" label="Solo-dager brukt" />
  </div>
  <div v-else class="card"><p class="muted">Logger inn …</p></div>

  <div class="row" style="margin: 14px 0">
    <router-link class="btn" to="/session" style="flex: 1; text-align: center">Start smaking</router-link>
    <router-link class="btn secondary" to="/join" style="flex: 1; text-align: center">Bli med</router-link>
  </div>

  <h2>Kalenderen</h2>
  <div v-if="season" class="card">
    <AdventCalendar :start="season.start" :end="season.end" :today="today" :days="days" @finale="router.push('/finale')" />
    <p class="muted" style="margin: 10px 0 0">Én luke per dag. Trykk på en åpnet luke for å se hva som sto på bordet. Stjernen er finalen.</p>
  </div>

  <router-link v-if="tip && !tip.closed" class="card row" to="/tipp" style="text-decoration: none">
    <div>
      <strong class="display">🥇 Tipp pallen</strong>
      <div v-if="tip.tip" class="muted">Du tipper {{ tip.tip.podium.map((p) => p.name).join(', ') }}</div>
      <div v-else class="muted">Hvem vinner i år? {{ tip.tipped }} har tippet.</div>
    </div>
    <span class="muted">›</span>
  </router-link>

  <router-link class="card row" to="/finale" style="text-decoration: none">
    <div>
      <strong class="display">★ Finalen</strong>
      <div class="muted">Resultatet avsløres {{ season ? new Date(season.end + 'T00:00:00').toLocaleDateString('nb-NO', { day: 'numeric', month: 'long' }) : '' }}</div>
    </div>
    <span class="muted">›</span>
  </router-link>

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
