<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get } from '../api/client'
import type { AdventOverview, BadgeView, Beer, Feed, MyTip, PlannedEvent, Session, Tasting } from '../api/types'
import { useRoute } from 'vue-router'
import AdventCalendar, { type DayEntry } from '../components/AdventCalendar.vue'
import AdventPicture from '../components/AdventPicture.vue'
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
const nextEvent = ref<PlannedEvent | null | undefined>(undefined)
const advent = ref<AdventOverview | null>(null)
/** A clock for the countdowns, ticking once a minute. */
const now = ref(Date.now())
const ticker = window.setInterval(() => (now.value = Date.now()), 60_000)
onUnmounted(() => window.clearInterval(ticker))
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
  const adventLoaded = get<AdventOverview>('/api/advent').then((a) => (advent.value = a)).catch(() => null)
  get<PlannedEvent[]>('/api/events').then((l) => (nextEvent.value = l.find((e) => e.joined) ?? l[0] ?? null)).catch(() => (nextEvent.value = null))
  const [least, tastings, beers] = await Promise.all([
    get<Beer[]>('/api/beers/least-tasted?limit=5').catch(() => []),
    get<Tasting[]>('/api/tastings/mine').catch(() => []),
    get<Beer[]>('/api/beers').catch(() => []),
  ])
  leastTasted.value = least
  mine.value = tastings
  beerName.value = Object.fromEntries(beers.map((b) => [b.id, b.name]))
  // Back from a roll: a toast, and a pointer to today's door while it is still shut.
  if (typeof route.query.opened === 'string') {
    await adventLoaded
    if (advent.value && advent.value.inSeason && !advent.value.todayOpened) showToast('Terningen er kastet. Dagens luke venter på svaret ditt.', '/luke/idag')
    else showToast('Terningen er kastet. Skål!')
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


const season = computed(() => store.me?.season)

/** Opened advent doors by day, for the calendar; the value says whether the answer was right. */
const openedDoors = computed<Record<string, boolean | null>>(() =>
  Object.fromEntries((advent.value?.opened ?? []).map((o) => [o.day, o.correct])),
)
const doorNumber = computed(() => dayOfSeason.value.n)
/** "3 d 4 t" until a moment, or null once it has passed. */
function until(at: string | null) {
  if (!at) return null
  const ms = new Date(at).getTime() - now.value
  if (ms <= 0) return null
  const d = Math.floor(ms / 86_400_000), h = Math.floor((ms % 86_400_000) / 3_600_000), m = Math.floor((ms % 3_600_000) / 60_000)
  return d > 0 ? `${d} d ${h} t` : h > 0 ? `${h} t ${m} min` : `${m} min`
}
const releaseIn = computed(() => until(advent.value?.releaseAt ?? null))
const releaseToday = computed(() => !!advent.value?.releaseAt && !releaseIn.value && advent.value.releaseAt.slice(0, 10) <= today.value
  && new Date(advent.value.releaseAt).toLocaleDateString('sv-SE', { timeZone: 'Europe/Oslo' }) === today.value)
const housePct = computed(() => (advent.value ? Math.min(100, Math.round((advent.value.house.opened / advent.value.house.goal) * 100)) : 0))

/** What waits for an organiser, worded, and the admin section with the most of it. */
const choresText = computed(() => {
  const c = store.me?.chores
  if (!c) return ''
  const parts: string[] = []
  if (c.claims) parts.push(`${c.claims} ${c.claims === 1 ? 'historikk-kobling' : 'historikk-koblinger'} å avgjøre`)
  if (c.beers) parts.push(`${c.beers} ${c.beers === 1 ? 'øl' : 'øl'} å godkjenne`)
  if (c.photos) parts.push(`${c.photos} ${c.photos === 1 ? 'etikett' : 'etiketter'} å se på`)
  if (c.noConsent) parts.push(`${c.noConsent} uten samtykke`)
  if (c.staleConsent) parts.push(`${c.staleConsent} med eldre samtykke`)
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
  if (c.staleConsent) return '/admin?s=people&f=staleConsent'
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
    <template v-else>Luke {{ dayOfSeason.n }} av {{ dayOfSeason.total }}<template v-if="advent && advent.streak >= 2"> · 🕯️ {{ advent.streak }} luker på rad</template><template v-if="advent && advent.keys.available"> · 🗝️ {{ advent.keys.available }}</template>.</template>
  </p>

  <div v-if="toast" class="toast card" role="status">
    <span>{{ toast.text }}</span>
    <router-link v-if="toast.to" :to="toast.to">Se</router-link>
  </div>

  <router-link v-if="advent?.inSeason" class="card row door-today" :class="{ done: advent.todayOpened }" to="/luke/idag" style="text-decoration: none">
    <div>
      <strong class="display">{{ advent.todayOpened ? `✦ Luke ${doorNumber} er åpnet` : `🚪 Luke ${doorNumber} venter` }}</strong>
      <div v-if="!advent.todayOpened" class="muted">{{ advent.todayQuestion ?? 'Ett trykk, og du ser hva som står bak.' }}</div>
      <div v-else class="muted">Neste luke i morgen. {{ advent.house.today }} har åpnet dagens luke.</div>
    </div>
    <span class="muted">›</span>
  </router-link>

  <router-link v-if="releaseIn || releaseToday" class="card row release" to="/beers" style="text-decoration: none">
    <div>
      <strong class="display">🍾 {{ releaseIn ? `Slippdagen om ${releaseIn}` : 'Slippdagen er i dag!' }}</strong>
      <div class="muted">{{ releaseIn ? 'Vinmonopolet slipper årets juleøl. Da fylles hylla her også.' : 'Vinmonopolet har fylt hyllene. Hva smaker du først?' }}</div>
    </div>
    <span class="muted">›</span>
  </router-link>

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

  <router-link v-if="nextEvent !== undefined" class="card row" to="/smakinger" style="text-decoration: none">
    <div>
      <strong class="display">📅 {{ nextEvent ? (nextEvent.joined ? 'Din neste smaking' : 'Neste smaking') : 'Planlegg en smaking' }}</strong>
      <div v-if="nextEvent" class="muted">
        {{ nextEvent.title }} · {{ new Date(nextEvent.startsAt).toLocaleString('nb-NO', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) }}
        <template v-if="nextEvent.online"> · digitalt</template> · {{ nextEvent.attendees.length }} påmeldt
      </div>
      <div v-else class="muted">Ingen planlagte smakinger ennå. Samle folk, fysisk eller digitalt.</div>
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
    <AdventCalendar :start="season.start" :end="season.end" :today="today" :days="days" :compact="!wholeCalendar" :opened="advent ? openedDoors : undefined"
      @finale="router.push('/finale')" @door="(d) => router.push(`/luke/${d}`)" />
    <p class="muted" style="margin: 10px 0 0">{{ wholeCalendar ? 'Én luke per dag, med et spørsmål bak. ✦ er en åpnet luke, fargen viser hvor mye du smakte. Stjernen er finalen.' : 'Denne uka. ✦ er en åpnet luke, fargen viser hvor mye du smakte.' }}</p>
  </div>

  <div v-if="season && advent" class="card">
    <AdventPicture :start="season.start" :end="season.end" :opened="advent.opened.map((o) => o.day)" />
    <p class="muted" style="margin: 8px 0 0">
      <template v-if="advent.opened.length >= dayOfSeason.total">Kalenderen er full. Hele bildet er ditt.</template>
      <template v-else>Hver luke du åpner, tørker en rute fri for rim. {{ advent.opened.length }} av {{ dayOfSeason.total }}.
        <template v-if="advent.keys.available"> Du har {{ advent.keys.available }} {{ advent.keys.available === 1 ? 'nisse-nøkkel' : 'nisse-nøkler' }} til luker du gikk glipp av.</template>
        <template v-else> Gikk du glipp av en luke? Smak ved et bord sammen med noen, så får du en nisse-nøkkel.</template>
      </template>
    </p>
  </div>

  <div v-if="advent" class="card">
    <strong class="display">⭐ Stjernen</strong>
    <p class="muted" style="margin: 6px 0 6px">
      <template v-if="!advent.house.unlocked">Når huset har åpnet {{ advent.house.goal }} luker sammen, tennes stjernen og alle får se topp ti, uten rekkefølge. {{ advent.house.opened }} så langt.</template>
      <template v-else>Huset har åpnet {{ advent.house.opened }} luker. Stjernen er tent: dette er topp ti akkurat nå, i alfabetisk rekkefølge.</template>
    </p>
    <span class="bar"><span :style="{ width: `${housePct}%` }" /></span>
    <ul v-if="advent.house.unlocked && advent.topTen.length" class="topten">
      <li v-for="b in advent.topTen" :key="b.id"><router-link class="lnk" :to="`/beers/${b.id}`">{{ b.name }}</router-link></li>
    </ul>
    <p v-if="advent.blackout" class="muted" style="margin: 8px 0 0">Resultatlista er mørkelagt til stjernen tennes i finalen. Lukene gir et spor om lederen hver dag.</p>
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
.door-today { border: 2px solid var(--brand); }
.door-today.done { border: 1px solid var(--line); }
.release { border: 1px dashed var(--brass); }
.bar { display: block; height: 8px; border-radius: 4px; background: var(--door-shut); overflow: hidden; }
.bar > span { display: block; height: 100%; background: var(--candle); border-radius: 4px; }
.topten { columns: 2; margin: 10px 0 0; padding-left: 18px; }
.topten li { margin: 2px 0; }
.table-card { border: 1px solid var(--candle); box-shadow: 0 0 0 1px var(--candle) inset, 0 2px 10px rgba(0,0,0,0.05); }
</style>
