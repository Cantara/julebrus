<script setup lang="ts">
/**
 * "I kveld": the day's wall for the whole house. Who rolled what, the tables going right
 * now, approved labels and blind-round results, newest first and live, with a cheer and a
 * few emojis to give each line. Never anyone's dice: the day's average per beer shows only
 * from three rolls.
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get, put } from '../api/client'
import { subscribeFeed, type LiveStatus } from '../api/live'
import type { Feed, FeedEntry, FeedReaction } from '../api/types'

const route = useRoute()
const router = useRouter()
const feed = ref<Feed | null>(null)
const error = ref('')
const live = ref<LiveStatus>('connecting')
const busy = ref<Set<string>>(new Set())
/** Lines that arrived after the first load get a little landing animation. */
const seen = ref<Set<string>>(new Set())
const fresh = ref<Set<string>>(new Set())

const day = computed(() => (typeof route.query.day === 'string' ? route.query.day : null))
const isToday = computed(() => !feed.value || feed.value.day === feed.value.today)

async function refresh() {
  const next = await get<Feed>('/api/feed' + (day.value ? `?day=${encodeURIComponent(day.value)}` : ''))
  const incoming = new Set<string>()
  if (feed.value && feed.value.day === next.day) {
    for (const e of next.entries) if (!seen.value.has(e.id)) incoming.add(e.id)
  }
  feed.value = next
  seen.value = new Set(next.entries.map((e) => e.id))
  if (incoming.size) {
    fresh.value = incoming
    window.setTimeout(() => (fresh.value = new Set()), 1200)
  }
}

let refreshQueued: number | undefined
function refreshSoon() {
  clearTimeout(refreshQueued)
  refreshQueued = window.setTimeout(() => refresh().catch(() => {}), 150)
}

let unsubscribe: (() => void) | undefined
let timer: number | undefined
function onVisible() {
  if (document.visibilityState === 'visible') refreshSoon()
}

onMounted(async () => {
  try {
    await refresh()
  } catch (e) {
    error.value = (e as Error).message
  }
  unsubscribe = subscribeFeed(refreshSoon, (s) => (live.value = s))
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
watch(day, () => {
  seen.value = new Set()
  refresh().catch(() => {})
})

function shift(days: number) {
  const base = new Date((feed.value?.day ?? new Date().toISOString().slice(0, 10)) + 'T00:00:00Z')
  base.setUTCDate(base.getUTCDate() + days)
  const iso = base.toISOString().slice(0, 10)
  router.replace({ query: iso === feed.value?.today ? {} : { day: iso } })
}

async function react(entry: FeedEntry, emoji: string) {
  const key = entry.id + emoji
  if (busy.value.has(key)) return
  busy.value.add(key)
  // Optimistic: flip it locally, then take the server's tally.
  const before = entry.reactions
  const mine = before.find((r) => r.emoji === emoji)
  entry.reactions = mine
    ? before.map((r) => (r.emoji === emoji ? { ...r, mine: !r.mine, count: r.count + (r.mine ? -1 : 1) } : r)).filter((r) => r.count > 0)
    : [...before, { emoji, count: 1, mine: true }]
  try {
    entry.reactions = await put<FeedReaction[]>('/api/feed/reactions', { kind: entry.kind, targetId: entry.id, emoji })
  } catch {
    entry.reactions = before
  } finally {
    busy.value.delete(key)
  }
}

const emojiTitle: Record<string, string> = { '🍻': 'Skål!', '🎅': 'Julestemning', '🔥': 'Fyr i peisen', '😂': 'Hehe', '👏': 'Bra jobba' }

function clock(iso: string) {
  return new Date(iso).toLocaleTimeString('nb-NO', { hour: '2-digit', minute: '2-digit' })
}
function dayLabel(iso: string) {
  return new Date(iso + 'T00:00:00').toLocaleDateString('nb-NO', { weekday: 'long', day: 'numeric', month: 'long' })
}
function line(e: FeedEntry): string {
  switch (e.kind) {
    case 'TASTING':
      return e.tableId ? `smakte ${e.beerName} ved bordet hos ${e.tableHost}` : `smakte ${e.beerName}`
    case 'TABLE':
      return e.open
        ? `åpnet et bord · ${e.tableSize} rundt bordet · ${e.beers} øl`
        : `hadde ${e.tableSize} rundt bordet og ${e.beers} øl. Bordet er avsluttet`
    case 'BLIND':
      return `kjørte blindsmaking: ${e.correct} av ${e.guesses} traff ${e.beerName}`
    case 'PHOTO':
      return `Ny etikett på ${e.beerName}`
  }
}
</script>

<template>
  <div class="row" style="align-items: baseline">
    <h1 style="margin-bottom: 4px">{{ isToday ? 'I kveld' : 'Den kvelden' }}</h1>
    <span class="live" :class="live" :title="live === 'live' ? 'Direkte: veggen oppdateres når noen kaster' : live === 'connecting' ? 'Kobler til' : 'Frakoblet, oppdaterer hvert 5. sekund'">
      <i aria-hidden="true" />{{ live === 'live' ? 'direkte' : live === 'connecting' ? 'kobler til' : 'frakoblet' }}
    </span>
  </div>
  <p v-if="error" class="error" role="alert">{{ error }}</p>

  <template v-if="feed">
    <p class="muted" style="margin-top: 0">
      <button class="linkish" aria-label="Dagen før" @click="shift(-1)">‹</button>
      {{ dayLabel(feed.day) }}
      <button class="linkish" :disabled="isToday" aria-label="Dagen etter" @click="shift(1)">›</button>
      <br />{{ feed.tastings }} terningkast · {{ feed.people }} {{ feed.people === 1 ? 'smaker' : 'smakere' }}
    </p>

    <div v-if="feed.openTables.length" class="card tables">
      <strong class="display">{{ feed.openTables.length === 1 ? 'Ett bord i gang' : `${feed.openTables.length} bord i gang` }}</strong>
      <div v-for="t in feed.openTables" :key="t.id" class="row" style="margin-top: 6px">
        <span>Hos <strong>{{ t.hostName }}</strong> <span class="muted">· {{ t.members }} rundt bordet · {{ t.beers }} øl</span></span>
        <router-link v-if="t.mine" class="btn secondary" style="padding: 6px 10px; font-size: 0.8rem" :to="`/session/${t.id}`">Til bordet</router-link>
      </div>
    </div>

    <div v-if="!feed.entries.length" class="card">
      <strong class="display">Stille på veggen</strong>
      <p class="muted" style="margin: 6px 0 0">
        {{ isToday ? 'Ingen har kastet ennå i dag. Bli den første, så har de andre noe å skåle for.' : 'Ingen kastet denne dagen.' }}
      </p>
      <router-link v-if="isToday" class="btn" to="/beers" style="margin-top: 10px">Finn et øl</router-link>
    </div>

    <ol class="wall">
      <li v-for="e in feed.entries" :key="e.id" class="card" :class="[e.kind.toLowerCase(), { fresh: fresh.has(e.id), me: e.me }]">
        <div class="row" style="align-items: flex-start">
          <div>
            <span class="who" aria-hidden="true">{{ e.kind === 'TASTING' ? '🎲' : e.kind === 'TABLE' ? '🍻' : e.kind === 'BLIND' ? '🙈' : '📷' }}</span>
            <strong v-if="e.displayName && e.kind !== 'PHOTO'">{{ e.me ? 'Du' : e.displayName }}</strong>
            <template v-if="e.kind === 'TASTING'"> smakte <router-link :to="`/beers/${e.beerId}`">{{ e.beerName }}</router-link><template v-if="e.tableId"> ved bordet hos {{ e.tableHost }}</template></template>
            <template v-else-if="e.kind === 'BLIND'"> kjørte blindsmaking: {{ e.correct }} av {{ e.guesses }} traff <router-link :to="`/beers/${e.beerId}`">{{ e.beerName }}</router-link></template>
            <template v-else-if="e.kind === 'PHOTO'">Ny etikett på <router-link :to="`/beers/${e.beerId}`">{{ e.beerName }}</router-link></template>
            <template v-else> {{ line(e) }}</template>
            <span v-if="e.brewery && e.kind !== 'TABLE'" class="muted"> · {{ e.brewery }}</span>
          </div>
          <span class="muted when">{{ clock(e.at) }}</span>
        </div>
        <div class="cheers">
          <button
            v-for="emoji in feed.emojis"
            :key="emoji"
            class="cheer"
            :class="{ on: e.reactions.find((r) => r.emoji === emoji)?.mine, zero: !e.reactions.find((r) => r.emoji === emoji) }"
            :title="emojiTitle[emoji] ?? emoji"
            :aria-pressed="!!e.reactions.find((r) => r.emoji === emoji)?.mine"
            @click="react(e, emoji)"
          >
            {{ emoji }}<span v-if="e.reactions.find((r) => r.emoji === emoji)" class="n">{{ e.reactions.find((r) => r.emoji === emoji)?.count }}</span>
          </button>
        </div>
      </li>
    </ol>

    <template v-if="feed.beers.length">
      <h2>{{ isToday ? 'Dagens øl' : 'Ølet den dagen' }}</h2>
      <p class="muted">Snittet vises først når tre har kastet. Ingen enkeltkast, de hører bordet til.</p>
      <div v-for="b in feed.beers" :key="b.beerId" class="card row">
        <div>
          <router-link :to="`/beers/${b.beerId}`" style="text-decoration: none"><strong>{{ b.name }}</strong></router-link>
          <div class="muted">{{ b.brewery ? b.brewery + ' · ' : '' }}{{ b.count }} terningkast</div>
        </div>
        <span v-if="b.average !== null" class="avg display">{{ b.average.toFixed(1) }}</span>
        <span v-else class="muted" style="font-size: 0.8rem">{{ 3 - b.count }} til for snitt</span>
      </div>
    </template>
  </template>
</template>

<style scoped>
.live { font-size: 0.75rem; color: var(--muted); display: inline-flex; align-items: center; gap: 5px; }
.live i { width: 8px; height: 8px; border-radius: 50%; background: var(--muted); opacity: 0.5; }
.live.live i { background: #3c8a4f; opacity: 1; box-shadow: 0 0 6px #3c8a4f88; }
.live.connecting i { animation: blink 1.2s ease-in-out infinite; }
@keyframes blink { 50% { opacity: 0.15; } }
.linkish { background: none; border: 0; color: var(--brand); padding: 0 8px; font-size: 1.2rem; line-height: 1; cursor: pointer; }
.linkish:disabled { opacity: 0.3; }
.tables { border: 1px solid var(--candle); box-shadow: 0 0 0 1px var(--candle) inset, 0 2px 10px rgba(0,0,0,0.05); }
.wall { list-style: none; margin: 0; padding: 0; }
.wall li { padding: 12px 14px; }
.wall li.me { border-color: var(--candle); }
.wall li.fresh { animation: land 600ms cubic-bezier(0.2, 1.2, 0.3, 1) both; }
.who { margin-right: 6px; }
.when { font-size: 0.75rem; white-space: nowrap; margin-left: 8px; font-variant-numeric: tabular-nums; }
.cheers { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
.cheer {
  background: var(--line); color: var(--ink); border: 1px solid transparent; border-radius: 999px;
  padding: 4px 10px; font-size: 0.95rem; line-height: 1.2; min-width: 40px; display: inline-flex; align-items: center; gap: 4px;
  transition: transform 120ms ease;
}
.cheer:active { transform: scale(1.15); }
.cheer.zero { background: transparent; border-color: var(--line); opacity: 0.7; }
.cheer.on { background: var(--candle); color: #2b1a14; border-color: var(--candle); opacity: 1; }
.cheer .n { font-size: 0.8rem; font-weight: 600; font-variant-numeric: tabular-nums; }
.avg { font-size: 1.4rem; font-weight: 700; }
</style>
