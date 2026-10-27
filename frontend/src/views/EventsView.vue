<script setup lang="ts">
/**
 * Planned tastings: anyone hosts one, physical or digital, the others sign up, and on the
 * night the host opens the table from here so the attendees get the code without a QR.
 * The finale is listed at the bottom as the season's fixed digital event.
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiFailure, del, get, post } from '../api/client'
import type { PlannedEvent, Session } from '../api/types'
import { useMe } from '../stores/me'

const store = useMe()
const router = useRouter()
const events = ref<PlannedEvent[]>([])
const error = ref('')
const busy = ref('')
const planning = ref(false)
const draft = ref({ title: '', date: '', time: '19:00', online: false, place: '', note: '', capacity: '' as string | number })

const CODES: Record<string, string> = {
  EVENT_PAST: 'Det tidspunktet har passert.',
  EVENT_FULL: 'Fullt. Spør verten om det er plass til én til.',
  EVENT_CANCELLED: 'Smakingen er avlyst.',
  EVENT_HOST_STAYS: 'Verten kan ikke melde seg av. Avlys heller.',
  EVENT_NOT_HOST: 'Bare verten kan gjøre det.',
  VALIDATION: 'Sjekk feltene: tittel, tidspunkt, og plasser mellom 2 og 60.',
}

async function load() {
  events.value = await get<PlannedEvent[]>('/api/events')
}
async function run(key: string, fn: () => Promise<unknown>) {
  error.value = ''
  busy.value = key
  try {
    await fn()
    await load()
  } catch (e) {
    error.value = e instanceof ApiFailure ? (CODES[e.code] ?? e.message) : (e as Error).message
  } finally {
    busy.value = ''
  }
}
onMounted(() => load().catch(() => (error.value = 'Kunne ikke hente smakingene.')))

const plan = () =>
  run('plan', async () => {
    const startsAt = new Date(`${draft.value.date}T${draft.value.time || '19:00'}:00`)
    if (Number.isNaN(startsAt.getTime())) throw new ApiFailure(422, 'VALIDATION', 'Velg dato og klokkeslett.')
    await post('/api/events', {
      title: draft.value.title.trim(),
      startsAt: startsAt.toISOString(),
      online: draft.value.online,
      place: draft.value.online ? '' : draft.value.place.trim(),
      note: draft.value.note.trim(),
      capacity: draft.value.capacity === '' ? null : Number(draft.value.capacity),
    })
    planning.value = false
    draft.value = { title: '', date: '', time: '19:00', online: false, place: '', note: '', capacity: '' }
  })
const join = (e: PlannedEvent) => run(e.id, () => post(`/api/events/${e.id}/join`))
const leave = (e: PlannedEvent) => run(e.id, () => del(`/api/events/${e.id}/join`))
function cancel(e: PlannedEvent) {
  if (!window.confirm(`Avlyse «${e.title}»? De påmeldte ser at den er borte.`)) return
  run(e.id, () => post(`/api/events/${e.id}/cancel`))
}
const openTable = (e: PlannedEvent) =>
  run(e.id, async () => {
    const after = await post<PlannedEvent>(`/api/events/${e.id}/table`)
    if (after.sessionId) router.push(`/session/${after.sessionId}`)
  })
const goToTable = (e: PlannedEvent) =>
  run(e.id, async () => {
    if (!e.joinCode) return
    const s = await post<Session>('/api/sessions/join', { code: e.joinCode })
    router.push(`/session/${s.id}`)
  })

const when = (iso: string) => new Date(iso).toLocaleString('nb-NO', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
const season = computed(() => store.me?.season)
const finaleAt = computed(() => (season.value ? new Date(season.value.end + 'T18:00:00') : null))
const todayIso = new Date().toISOString().slice(0, 10)
</script>

<template>
  <h1>Smakinger</h1>
  <p class="muted">Planlegg en kveld, fysisk eller digital, og la de andre melde seg på. Verten åpner bordet herfra når det er tid, og alle påmeldte får koden.</p>
  <p v-if="error" class="error" role="alert">{{ error }}</p>

  <button v-if="!planning" class="btn" style="width: 100%" @click="planning = true">Planlegg en smaking</button>
  <form v-else class="card" @submit.prevent="plan">
    <label>Hva</label>
    <input v-model="draft.title" required maxlength="120" placeholder="Juleøl hos Kari" />
    <div class="row">
      <div style="flex: 1"><label>Dato</label><input v-model="draft.date" type="date" required :min="todayIso" /></div>
      <div style="flex: 1"><label>Klokka</label><input v-model="draft.time" type="time" required /></div>
    </div>
    <label style="display: flex; gap: 8px; align-items: center; margin-top: 12px">
      <input v-model="draft.online" type="checkbox" style="width: auto" /> Digitalt: alle sitter hjemme med samme øl, bordet er felles
    </label>
    <template v-if="!draft.online">
      <label>Hvor</label>
      <input v-model="draft.place" maxlength="200" placeholder="Kjøkkenet i 3. etasje" />
    </template>
    <label>Plasser (valgfritt)</label>
    <input v-model="draft.capacity" type="number" min="2" max="60" placeholder="Åpent" />
    <label>Beskjed</label>
    <textarea v-model="draft.note" rows="2" maxlength="1000" placeholder="Ta med ett øl hver, jeg har glass." />
    <div class="row" style="margin-top: 12px">
      <button type="button" class="secondary" @click="planning = false">Avbryt</button>
      <button :disabled="busy === 'plan' || !draft.title.trim() || !draft.date">Lagre</button>
    </div>
  </form>

  <h2>Kommende</h2>
  <div v-for="e in events" :key="e.id" class="card" :class="{ mine: e.joined }">
    <div class="row" style="align-items: flex-start">
      <div style="min-width: 0">
        <strong class="display">{{ e.title }}</strong>
        <span v-if="e.online" class="pill" style="margin-left: 6px">digitalt</span>
        <span v-if="e.tableOpen" class="pill on" style="margin-left: 6px">bordet er åpent</span>
        <div class="muted">{{ when(e.startsAt) }}<template v-if="!e.online && e.place"> · {{ e.place }}</template> · vert {{ e.mine ? 'deg' : e.hostName }}</div>
        <p v-if="e.note" style="margin: 6px 0 0">{{ e.note }}</p>
      </div>
      <span class="muted" style="white-space: nowrap">{{ e.attendees.length }}<template v-if="e.capacity">/{{ e.capacity }}</template></span>
    </div>
    <div style="margin-top: 6px">
      <span v-for="a in e.attendees" :key="a.userId" class="pill" :class="{ on: a.userId === store.me?.id }">{{ a.displayName }}</span>
    </div>
    <div class="actions">
      <button v-if="!e.joined" :disabled="busy === e.id" @click="join(e)">Bli med</button>
      <button v-else-if="!e.mine" class="secondary" :disabled="busy === e.id" @click="leave(e)">Meld av</button>
      <button v-if="e.mine && !e.tableOpen" class="secondary" :disabled="busy === e.id" @click="openTable(e)">Åpne bordet</button>
      <button v-if="e.joined && e.tableOpen && e.joinCode" :disabled="busy === e.id" @click="goToTable(e)">Til bordet</button>
      <button v-if="e.mine" class="secondary" :disabled="busy === e.id" @click="cancel(e)">Avlys</button>
    </div>
  </div>
  <p v-if="!events.length" class="muted card">Ingen planlagte smakinger ennå. Vær den første, så har de andre noe å melde seg på.</p>

  <h2>Finalen</h2>
  <router-link class="card row" to="/finale" style="text-decoration: none">
    <div>
      <strong class="display">★ Finalen {{ season?.year ?? '' }}</strong>
      <span class="pill" style="margin-left: 6px">digitalt</span>
      <div class="muted">{{ finaleAt ? when(finaleAt.toISOString()) : '' }} · alle på hver sin telefon. Stjerna tennes og resultatet kommer av seg selv.</div>
    </div>
    <span class="muted">›</span>
  </router-link>
</template>

<style scoped>
.mine { border-color: var(--candle); }
.actions { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px; }
.actions button { padding: 8px 12px; }
</style>
