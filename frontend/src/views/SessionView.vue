<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get, post } from '../api/client'
import { subscribeSession, type LiveStatus } from '../api/live'
import type { Beer, BlindRound, DuelPair, Session } from '../api/types'
import BlindPour from '../components/BlindPour.vue'
import DiceFace from '../components/DiceFace.vue'
import QrCode from '../components/QrCode.vue'
import { useMe } from '../stores/me'

const props = defineProps<{ id: string }>()
const store = useMe()
const route = useRoute()

const session = ref<Session | null>(null)
const catalogue = ref<Beer[]>([])
const q = ref('')
const pending = ref<DuelPair[]>([])
const rounds = ref<BlindRound[]>([])
const error = ref('')
const live = ref<LiveStatus>('connecting')
/** The gift tag with the QR stays up while the host is alone; once there is company it folds to a line. */
const showQr = ref<boolean | null>(null)
const qrOpen = computed(() => showQr.value ?? (session.value ? session.value.members.length < 2 : true))

const isHost = computed(() => session.value && store.me && session.value.hostId === store.me.id)
const joinUrl = computed(() => `${location.origin}/join/${session.value?.joinCode ?? ''}`)
const myId = computed(() => store.me?.id)

const beerName = (id: string) => session.value?.beers.find((b) => b.beerId === id)?.name ?? '?'
const memberName = (id: string) => session.value?.members.find((m) => m.userId === id)?.displayName ?? '?'

const filtered = computed(() => {
  const inSession = new Set(session.value?.beers.map((b) => b.beerId))
  const needle = q.value.toLowerCase()
  return catalogue.value
    .filter((b) => !inSession.has(b.id))
    .filter((b) => !needle || b.name.toLowerCase().includes(needle) || (b.brewery ?? '').toLowerCase().includes(needle))
    .slice(0, 8)
})

/** Beers whose scores were revealed since we last looked: these get unwrapped. */
const unwrapping = ref<Set<string>>(new Set())
let seenRevealed = new Set<string>()
let firstLoad = true

function unwrap(beerId: string) {
  unwrapping.value = new Set([...unwrapping.value, beerId])
  window.setTimeout(() => {
    const next = new Set(unwrapping.value)
    next.delete(beerId)
    unwrapping.value = next
  }, 2600)
}

async function refresh() {
  session.value = await get<Session>(`/api/sessions/${props.id}`)
  pending.value = await get<DuelPair[]>(`/api/duels/pending/${props.id}`).catch(() => [])
  rounds.value = await get<BlindRound[]>(`/api/sessions/${props.id}/blind`).catch(() => [])
  const revealedNow = new Set(session.value.beers.filter((b) => b.revealed && b.scores).map((b) => b.beerId))
  if (firstLoad) {
    // Coming back from the rating page as the last to rate: unwrap that one beer.
    const just = typeof route.query.just === 'string' ? route.query.just : null
    if (just && revealedNow.has(just)) unwrap(just)
    firstLoad = false
  } else {
    for (const id of revealedNow) if (!seenRevealed.has(id)) unwrap(id)
  }
  seenRevealed = revealedNow
}

async function addBeer(beerId: string) {
  await post(`/api/sessions/${props.id}/beers`, { beerId })
  q.value = ''
  await refresh()
}

async function answerDuel(winner: string, loser: string) {
  await post('/api/duels', { sessionId: props.id, winnerBeerId: winner, loserBeerId: loser })
  await refresh()
}

async function close() {
  await post(`/api/sessions/${props.id}/close`)
  await refresh()
}

let timer: number | undefined
let unsubscribe: (() => void) | undefined
let refreshQueued: number | undefined

/** Several events can land within a few ms (the last score reveals and closes a round); one refetch is enough. */
function refreshSoon() {
  if (refreshQueued) return
  refreshQueued = window.setTimeout(() => {
    refreshQueued = undefined
    refresh().catch(() => {})
  }, 120)
}

function onVisible() {
  if (document.visibilityState === 'visible') refreshSoon()
}

onMounted(async () => {
  try {
    await refresh()
    catalogue.value = await get<Beer[]>('/api/beers')
  } catch (e) {
    error.value = (e as Error).message
  }
  // Live: every write at the table pushes an event, and every phone refetches at once.
  unsubscribe = subscribeSession(props.id, refreshSoon, (s) => (live.value = s))
  // Fallback for a stream that silently died (a proxy, a sleeping phone): every 5 s while
  // not connected, every 30 s while live, and a refetch when the tab comes back.
  let tick = 0
  timer = window.setInterval(() => {
    tick += 1
    if (live.value !== 'live' || tick % 6 === 0) refresh().catch(() => {})
  }, 5000)
  document.addEventListener('visibilitychange', onVisible)
})
onUnmounted(() => {
  clearInterval(timer)
  clearTimeout(refreshQueued)
  unsubscribe?.()
  document.removeEventListener('visibilitychange', onVisible)
})
</script>

<template>
  <p v-if="error" class="error">{{ error }}</p>
  <template v-if="session">
    <h1>Smaking {{ session.closedAt ? '(avsluttet)' : '' }}</h1>

    <div v-if="!session.closedAt && qrOpen" class="card gift-tag" style="text-align: center">
      <span class="string" aria-hidden="true" />
      <span class="hole" aria-hidden="true" />
      <p class="muted" style="margin: 0 0 8px">Til: alle rundt bordet</p>
      <QrCode :value="joinUrl" :size="220" />
      <p class="code" style="margin: 10px 0 0">{{ session.joinCode }}</p>
      <p class="muted">Skann eller skriv inn koden for å bli med</p>
      <button v-if="session.members.length >= 2" class="secondary" style="margin-top: 4px" @click="showQr = false">Skjul</button>
    </div>
    <div v-else-if="!session.closedAt" class="card row">
      <span class="muted">Flere til bordet? Kode <strong class="display" style="letter-spacing: 0.2em">{{ session.joinCode }}</strong></span>
      <button class="secondary" @click="showQr = true">Vis QR</button>
    </div>

    <div class="card">
      <div class="row">
        <strong>Rundt bordet</strong>
        <span class="live" :class="live" :title="live === 'live' ? 'Direkte: alle ser avsløringen samtidig' : live === 'connecting' ? 'Kobler til' : 'Frakoblet, oppdaterer hvert 5. sekund'">
          <i aria-hidden="true" />{{ live === 'live' ? 'direkte' : live === 'connecting' ? 'kobler til' : 'frakoblet' }}
        </span>
      </div>
      <div><span v-for="m in session.members" :key="m.userId" class="pill" :class="{ on: m.userId === myId }">{{ m.displayName }}<span v-if="m.teams.length" class="muted"> · {{ m.teams.join(' · ') }}</span></span></div>
      <p v-if="session.solo" class="muted">Du er alene. Dette teller som en solo-dag til noen blir med.</p>
    </div>

    <h2>Øl på bordet</h2>
    <div v-for="b in session.beers" :key="b.beerId" class="card">
      <div class="row">
        <div>
          <strong>{{ b.name }}</strong>
          <div class="muted">{{ b.brewery }}</div>
        </div>
        <router-link v-if="myId && !b.ratedBy.includes(myId) && !session.closedAt" class="btn" :to="`/beers/${b.beerId}/rate?session=${session.id}`">Gi terningkast</router-link>
      </div>
      <div v-if="b.revealed && b.scores" class="reveal" :class="{ unwrap: unwrapping.has(b.beerId) }">
        <span class="paper" aria-hidden="true" />
        <span class="burst" aria-hidden="true"><i v-for="n in 10" :key="n" :style="{ '--a': `${n * 36}deg` }" /></span>
        <div class="scores dice-row">
          <span v-for="(s, uid) in b.scores" :key="uid" class="score roll">
            <DiceFace :value="s" :size="34" />
            <span class="who">{{ memberName(String(uid)) }}</span>
          </span>
        </div>
        <p class="ok average display" style="margin: 8px 0 0">Snitt {{ b.average?.toFixed(2) }}</p>
        <ul v-if="b.notes && Object.keys(b.notes).length" class="notes">
          <li v-for="(n, uid) in b.notes" :key="uid">
            <strong>{{ memberName(String(uid)) }}:</strong>{{ ' ' }}
            <template v-if="n.notes">{{ n.notes }}{{ ' ' }}</template>
            <span v-for="tag in n.tags" :key="tag" class="pill">{{ tag }}</span>
          </li>
        </ul>
      </div>
      <p v-else class="muted" style="margin: 8px 0 0">
        {{ b.ratedBy.length }} av {{ session.members.length }} har svart. Scorene vises når alle er ferdige.
      </p>
    </div>

    <div v-if="!session.closedAt" class="card">
      <label>Legg et øl på bordet</label>
      <input v-model="q" placeholder="Søk i katalogen" />
      <div v-for="b in filtered" :key="b.id" class="row" style="padding: 8px 0; border-bottom: 1px solid var(--line)">
        <span>{{ b.name }} <span class="muted">{{ b.brewery }}</span><span v-if="b.homebrew" class="pill" style="margin-left: 6px">hjemmebrygg</span></span>
        <button class="secondary" @click="addBeer(b.id)">Legg til</button>
      </div>
      <p class="muted" style="margin-top: 8px">Mangler ølet? <router-link to="/beers">Legg det til i katalogen.</router-link></p>
    </div>

    <BlindPour v-if="myId" :session="session" :rounds="rounds" :my-id="myId" :is-host="!!isHost" @changed="refresh" />

    <template v-if="pending.length">
      <h2>Duell</h2>
      <p class="muted">Hvilket var best? Dette lager sideranking uavhengig av hvor snill du er med terningen.</p>
      <div v-for="p in pending" :key="p.beerA + p.beerB" class="card duel">
        <button class="secondary" @click="answerDuel(p.beerA, p.beerB)">{{ beerName(p.beerA) }}</button>
        <span class="vs" aria-hidden="true">vs</span>
        <button class="secondary" @click="answerDuel(p.beerB, p.beerA)">{{ beerName(p.beerB) }}</button>
      </div>
    </template>

    <button v-if="isHost && !session.closedAt" class="secondary" style="width: 100%; margin-top: 12px" @click="close">Avslutt smakingen</button>
  </template>
</template>

<style scoped>
.dice-row { display: flex; flex-wrap: wrap; gap: 10px 14px; margin-top: 10px; }
.dice-row .score { display: inline-flex; flex-direction: column; align-items: center; gap: 2px; }
.dice-row .who { font-size: 0.75rem; color: var(--muted); max-width: 72px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notes { list-style: none; margin: 8px 0 0; padding: 0; display: grid; gap: 4px; font-size: 0.9rem; }
.notes .pill { margin-left: 4px; }
.duel { display: grid; gap: 6px; }
.duel button { width: 100%; text-align: left; }
.duel .vs { justify-self: center; font-size: 0.75rem; letter-spacing: 0.08em; text-transform: uppercase; color: var(--muted); }
.live { font-size: 0.75rem; color: var(--muted); display: inline-flex; align-items: center; gap: 5px; }
.live i { width: 8px; height: 8px; border-radius: 50%; background: var(--muted); opacity: 0.5; }
.live.live i { background: #3c8a4f; opacity: 1; box-shadow: 0 0 6px #3c8a4f88; }
.live.connecting i { animation: blink 1.2s ease-in-out infinite; }
@keyframes blink { 50% { opacity: 0.15; } }
</style>
