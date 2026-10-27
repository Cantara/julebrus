<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get } from '../api/client'
import type { BadgeView, Beer, Feed, MyTip, Session, Tasting } from '../api/types'
import { useRoute } from 'vue-router'
import AdventCalendar, { type DayEntry } from '../components/AdventCalendar.vue'
import LightsMeter from '../components/LightsMeter.vue'
import Snow from '../components/Snow.vue'
import { useMe } from '../stores/me'

const store = useMe()
const router = useRouter()
const route = useRoute()
const toast = ref<{ text: string; to?: string } | null>(null)
let toastTimer: number | undefined
function showToast(text: string, to?: string) {
  toast.value = { text, to }
  clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => (toast.value = null), 6000)
}

/** When loading the person takes too long, offer a clean restart: a stale app shell or cookie is the usual cause. */
const slow = ref(false)
window.setTimeout(() => (slow.value = !store.me), 6000)
async function restart() {
  try {
    const regs = await navigator.serviceWorker?.getRegistrations()
    await Promise.all((regs ?? []).map((r) => r.unregister()))
    const keys = await caches?.keys()
    await Promise.all((keys ?? []).map((k) => caches.delete(k)))
  } catch {
    /* best effort */
  }
  window.location.replace('/?r=' + Date.now())
}
const leastTasted = ref<Beer[]>([])
const mine = ref<Tasting[]>([])
const tip = ref<MyTip | null>(null)
const tonight = ref<Feed | null>(null)
const beerName = ref<Record<string, string>>({})
const soloSessions = ref<Set<string>>(new Set())

const wholeCalendar = ref(false)
try {
  wholeCalendar.value = localStorage.getItem('calendar') === 'all'
} catch {
  /* private mode */
}
function toggleCalendar() {
  wholeCalendar.value = !wholeCalendar.value
  try {
    localStorage.setItem('calendar', wholeCalendar.value ? 'all' : 'week')
  } catch {
    /* ignore */
  }
}

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
  get<Feed>('/api/feed').then((f) => (tonight.value = f)).catch(() => {})
  const [least, tastings, beers] = await Promise.all([
    get<Beer[]>('/api/beers/least-tasted?limit=5').catch(() => []),
    get<Tasting[]>('/api/tastings/mine').catch(() => []),
    get<Beer[]>('/api/beers').catch(() => []),
  ])
  leastTasted.value = least
  mine.value = tastings
  beerName.value = Object.fromEntries(beers.map((b) => [b.id, b.name]))
  // Back from a roll: the door opens with a little toast.
  if (typeof route.query.opened === 'string') {
    const d = new Date(route.query.opened + 'T00:00:00Z')
    showToast(`Luke ${d.getUTCDate()}. ${['jan', 'feb', 'mar', 'apr', 'mai', 'jun', 'jul', 'aug', 'sep', 'okt', 'nov', 'des'][d.getUTCMonth()]} åpnet!`)
    router.replace({ query: {} })
  }
  // A bauble that lit since last time gets a toast; the first visit only remembers.
  get<BadgeView[]>('/api/badges/me').then((badges) => {
    const earned = badges.filter((b) => b.earned).map((b) => b.id)
    let seen: string[] | null = null
    try {
      const raw = localStorage.getItem('badgesSeen')
      seen = raw ? (JSON.parse(raw) as string[]) : null
    } catch {
      seen = null
    }
    if (seen) {
      const fresh = badges.filter((b) => b.earned && !seen!.includes(b.id))
      if (fresh.length && !toast.value) showToast(`Ny kule på treet: ${fresh.map((b) => b.title).join(', ')}`, '/me')
    }
    try {
      localStorage.setItem('badgesSeen', JSON.stringify(earned))
    } catch {
      /* ignore */
    }
  }).catch(() => {})
  // Solo means no session or a one-member session; ask about the sessions we were in.
  const sessionIds = [...new Set(tastings.map((t) => t.sessionId).filter((s): s is string => !!s))]
  const sessions = await Promise.all(sessionIds.map((id) => get<Session>(`/api/sessions/${id}`).catch(() => null)))
  soloSessions.value = new Set(sessions.filter((s): s is Session => !!s && s.solo).map((s) => s.id))
})

const days = computed<Record<string, DayEntry>>(() => {
  const out: Record<string, DayEntry> = {}
  for (const t of mine.value) {
    const entry = (out[t.tastedDay] ??= { beers: [], solo: false })
    entry.beers.push({ id: t.beerId, name: beerName.value[t.beerId] ?? '?', score: t.score })
    if (!t.sessionId || soloSessions.value.has(t.sessionId)) entry.solo = true
  }
  return out
})

/** Days in a row with a roll, counting back from today or yesterday. */
const streak = computed(() => {
  const days = new Set(mine.value.map((t) => t.tastedDay))
  if (!days.size) return 0
  const t = new Date((store.me?.status.today ?? new Date().toISOString().slice(0, 10)) + 'T00:00:00Z')
  const iso = (d: Date) => d.toISOString().slice(0, 10)
  if (!days.has(iso(t))) t.setUTCDate(t.getUTCDate() - 1)
  let n = 0
  while (days.has(iso(t))) {
    n += 1
    t.setUTCDate(t.getUTCDate() - 1)
  }
  return n
})

const season = computed(() => store.me?.season)

/** What waits for an organiser, worded, and the admin section with the most of it. */
const choresText = computed(() => {
  const c = store.me?.chores
  if (!c) return ''
  const parts: string[] = []
  if (c.claims) parts.push(`${c.claims} ${c.claims === 1 ? 'historikk-kobling' : 'historikk-koblinger'} å avgjøre`)
  if (c.beers) parts.push(`${c.beers} ${c.beers === 1 ? 'øl' : 'øl'} å godkjenne`)
  if (c.photos) parts.push(`${c.photos} ${c.photos === 1 ? 'etikett' : 'etiketter'} å se på`)
  if (c.noConsent) parts.push(`${c.noConsent} uten samtykke`)
  if (c.noTeam) parts.push(`${c.noTeam} uten lag`)
  if (c.unmatched) parts.push(`${c.unmatched} ${c.unmatched === 1 ? 'innlogging' : 'innlogginger'} uten historisk navn`)
  return parts.join(' · ')
})
const choresLink = computed(() => {
  const c = store.me?.chores
  if (!c) return '/admin'
  if (c.claims) return '/admin?s=claims'
  if (c.beers) return '/admin?s=beers'
  if (c.photos) return '/admin?s=photos'
  if (c.noConsent) return '/admin?s=people&f=noConsent'
  if (c.noTeam) return '/admin?s=people&f=noTeam'
  return '/admin?s=raters'
})
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
    <template v-else>Luke {{ dayOfSeason.n }} av {{ dayOfSeason.total }}<template v-if="streak >= 2"> · 🕯️ {{ streak }} dager på rad</template>.</template>
  </p>

  <div v-if="toast" class="toast card" role="status">
    <span>{{ toast.text }}</span>
    <router-link v-if="toast.to" :to="toast.to">Se</router-link>
  </div>

  <router-link v-if="store.me && store.me.status.tastingsToday === 0 && !mine.length" class="card row" to="/historien" style="text-decoration: none">
    <div>
      <strong class="display">Ny her?</strong>
      <div class="muted">Les historien om juleølsmakingen, og hvorfor den varer hele adventstiden.</div>
    </div>
    <span class="muted">›</span>
  </router-link>

  <router-link v-if="store.me?.openTable" class="card row table-card" :to="`/session/${store.me.openTable.id}`" style="text-decoration: none">
    <div>
      <strong class="display">{{ store.me.openTable.host ? 'Du er vert ved et bord' : `Du sitter ved bordet hos ${store.me.openTable.hostName}` }}</strong>
      <div class="muted">
        {{ store.me.openTable.members }} rundt bordet · {{ store.me.openTable.beers }} øl
        <template v-if="store.me.openTable.pendingForMe > 0"> · <strong style="color: var(--brand)">{{ store.me.openTable.pendingForMe }} venter på terningen din</strong></template>
      </div>
    </div>
    <span class="muted">›</span>
  </router-link>

  <router-link v-if="store.me?.chores && store.me.chores.total > 0" class="card row chores" :to="choresLink" style="text-decoration: none">
    <div>
      <strong class="display">🧹 Arrangørsysler</strong>
      <div class="muted">{{ choresText }}</div>
    </div>
    <span class="muted">›</span>
  </router-link>

  <router-link class="card row tonight" to="/ikveld" style="text-decoration: none">
    <div>
      <strong class="display">🍻 I kveld</strong>
      <div v-if="tonight" class="muted">
        <template v-if="tonight.tastings === 0">Stille på veggen ennå. Bli den første i dag.</template>
        <template v-else>
          {{ tonight.tastings }} terningkast fra {{ tonight.people }} {{ tonight.people === 1 ? 'smaker' : 'smakere' }}<template v-if="tonight.openTables.length"> · {{ tonight.openTables.length }} {{ tonight.openTables.length === 1 ? 'bord i gang' : 'bord i gang' }}</template>. Skål for dem.
        </template>
      </div>
      <div v-else class="muted">Hvem smaker akkurat nå? Skål og emoji til de andre.</div>
    </div>
    <span class="muted">›</span>
  </router-link>

  <div v-if="store.me" class="card">
    <strong class="display">Hei, {{ store.me.displayName }}</strong>
    <p class="muted" style="margin: 8px 0 0">I dag<template v-if="store.me.status.bigTable"> · stort bord, {{ store.me.status.maxPerDay }} i kveld</template></p>
    <LightsMeter :lit="store.me.status.tastingsToday" :total="store.me.status.maxPerDay" tone="warm" label="Smakinger i dag" />
    <p class="muted" style="margin: 8px 0 0">Solo-dager</p>
    <LightsMeter :lit="store.me.status.soloDaysUsed" :total="store.me.status.maxSoloDays" tone="solo" label="Solo-dager brukt" />
  </div>
  <div v-else class="card">
    <p class="muted">Logger inn …</p>
    <p v-if="slow" class="muted" style="margin: 8px 0 0">Tar det lang tid? <a href="#" @click.prevent="restart">Start på nytt</a> rydder nettleserens kopi av appen og laster den inn igjen.</p>
  </div>

  <div class="row" style="margin: 14px 0">
    <router-link class="btn" to="/session" style="flex: 1; text-align: center">Start smaking</router-link>
    <router-link class="btn secondary" to="/join" style="flex: 1; text-align: center">Bli med</router-link>
  </div>

  <div class="row" style="align-items: baseline">
    <h2>Kalenderen</h2>
    <button class="secondary" style="padding: 6px 10px; font-size: 0.8rem" @click="toggleCalendar">{{ wholeCalendar ? 'Bare denne uka' : 'Hele kalenderen' }}</button>
  </div>
  <div v-if="season" class="card">
    <AdventCalendar :start="season.start" :end="season.end" :today="today" :days="days" :compact="!wholeCalendar" @finale="router.push('/finale')" />
    <p class="muted" style="margin: 10px 0 0">{{ wholeCalendar ? 'Én luke per dag. Trykk på en åpnet luke for å se hva som sto på bordet. Stjernen er finalen.' : 'Denne uka. Trykk på en åpnet luke for å se hva som sto på bordet.' }}</p>
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
      <div class="muted">{{ b.brewery }} · {{ b.ratingCount }} {{ b.ratingCount === 1 ? 'smaking' : 'smakinger' }}</div>
    </div>
    <router-link class="btn secondary" :to="`/beers/${b.id}/rate`">Smak</router-link>
  </div>
</template>

<style scoped>
.toast { position: fixed; left: 16px; right: 16px; bottom: calc(72px + env(safe-area-inset-bottom)); z-index: 3; display: flex; justify-content: space-between; gap: 12px; align-items: center; background: var(--pine); color: #fff; border: none; box-shadow: 0 8px 24px rgba(0,0,0,0.25); animation: land 500ms cubic-bezier(0.2, 1.2, 0.3, 1) both; }
.toast a { color: var(--candle); font-weight: 600; }
.chores { border: 1px dashed var(--brass); }
.table-card { border: 1px solid var(--candle); box-shadow: 0 0 0 1px var(--candle) inset, 0 2px 10px rgba(0,0,0,0.05); }
</style>
