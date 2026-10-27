<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiFailure, get, post } from '../api/client'
import type { AdminPerson } from '../api/types'

/**
 * Everyone who has signed in: who is mapped to earlier seasons, and what each person's vote counts this season.
 * The weight is decided by the rules (a rookie 0.5, a veteran 1.0) and can be set by hand when the rules are
 * wrong, for example when the last seasons' data is missing. A hand-set weight stays until "Tilbakestill"
 * hands it back to the rules; "Flagg" mutes a person this season (weight 0) without deleting anything.
 */
const people = ref<AdminPerson[]>([])
const q = ref('')
type Filter = 'all' | 'noHistory' | 'low' | 'noConsent' | 'staleConsent' | 'noTeam' | 'manual'
const FILTERS: Filter[] = ['all', 'noHistory', 'low', 'noConsent', 'staleConsent', 'noTeam', 'manual']
const route = useRoute()
const filter = ref<Filter>(FILTERS.includes(route.query.f as Filter) ? (route.query.f as Filter) : 'all')
const copied = ref(false)
const error = ref('')
const loaded = ref(false)
const editing = ref<string | null>(null)
const weightIn = ref(1)
const reasonIn = ref('')
const busy = ref(false)

async function load() {
  people.value = await get<AdminPerson[]>('/api/admin/people')
  loaded.value = true
}
onMounted(() => load().catch(() => (error.value = 'Kunne ikke hente lista.')))

const consented = computed(() => people.value.filter((p) => p.consented))
const noHistory = (p: AdminPerson) => p.consented && !p.historyYears.length
const low = (p: AdminPerson) => p.consented && (p.weight ?? 1) < 1
const noTeam = (p: AdminPerson) => p.consented && !p.teamsChosen
/** Never answered, or withdrew: nothing of theirs counts. */
const noConsent = (p: AdminPerson) => !p.consentVersion
/** Answered an older text: asked again at the next visit; until then treated as without consent. */
const staleConsent = (p: AdminPerson) => !p.consented && !!p.consentVersion

/** The nag: who to write to for the current filter, and a ready-made mail. */
const nag = computed(() => {
  if (filter.value === 'noConsent') {
    return {
      who: people.value.filter(noConsent),
      subject: 'Juleølsmakingen: samtykket mangler',
      body: 'Hei!\n\nDu har logget inn i juleølsmakingen, men ikke tatt stilling til samtykket ennå. Før du har sagt ja, teller ingenting du gjør, og du ser ikke bordene.\n\nÅpne appen og svar, så er du med: ' + location.origin + '\n\nSkål,\narrangørene',
    }
  }
  if (filter.value === 'staleConsent') {
    return {
      who: people.value.filter(staleConsent),
      subject: 'Juleølsmakingen: samtykketeksten er oppdatert',
      body: 'Hei!\n\nSamtykketeksten i juleølsmakingen er oppdatert siden du sa ja (lag, "I kveld", delte smaksnotater og planlagte smakinger). Før du har godtatt den nye teksten, er du ikke med i listene og bordene.\n\nLogg inn, så får du spørsmålet med en gang: ' + location.origin + '\n\nSkål,\narrangørene',
    }
  }
  if (filter.value === 'noTeam') {
    return {
      who: people.value.filter(noTeam),
      subject: 'Juleølsmakingen: hvilket lag spiller du for?',
      body: 'Hei!\n\nDu har ikke valgt lag ennå. Cantara, Rebel eller begge: laget får sin egen vinner i finalen, og årets lag kåres på terningkast per hode.\n\nVelg under Meg i appen: ' + location.origin + '/lag\n\nSkål,\narrangørene',
    }
  }
  return null
})
const mailto = computed(() => {
  if (!nag.value || !nag.value.who.length) return ''
  const bcc = nag.value.who.map((p) => p.email).join(',')
  return `mailto:?bcc=${encodeURIComponent(bcc)}&subject=${encodeURIComponent(nag.value.subject)}&body=${encodeURIComponent(nag.value.body)}`
})
async function copyEmails() {
  if (!nag.value) return
  try {
    await navigator.clipboard.writeText(nag.value.who.map((p) => p.email).join(', '))
    copied.value = true
    window.setTimeout(() => (copied.value = false), 2000)
  } catch {
    error.value = 'Kunne ikke kopiere. Marker adressene i lista i stedet.'
  }
}

const filtered = computed(() => {
  const needle = q.value.trim().toLowerCase()
  return people.value
    .filter((p) => !needle || p.displayName.toLowerCase().includes(needle) || p.email.toLowerCase().includes(needle) || p.linkedNames.some((n) => n.toLowerCase().includes(needle)))
    .filter((p) =>
      filter.value === 'all' ? true
        : filter.value === 'noHistory' ? noHistory(p)
        : filter.value === 'low' ? low(p)
        : filter.value === 'manual' ? p.overridden
        : filter.value === 'noTeam' ? noTeam(p)
        : filter.value === 'staleConsent' ? staleConsent(p)
        : noConsent(p))
})

function years(p: AdminPerson): string {
  if (!p.historyYears.length) return 'ingen tidligere år'
  const ys = p.historyYears
  return ys.length > 4 ? `${ys[0]}–${ys[ys.length - 1]} (${ys.length} år)` : ys.join(', ')
}

function open(p: AdminPerson, w: number) {
  editing.value = p.email
  weightIn.value = w
  reasonIn.value = p.overridden && p.reason ? p.reason : 'Mangler data fra tidligere år'
}

async function act(fn: () => Promise<unknown>) {
  error.value = ''
  busy.value = true
  try {
    await fn()
    editing.value = null
    await load()
  } catch (e) {
    error.value = e instanceof ApiFailure ? `${e.message} (${e.code})` : 'Noe gikk galt.'
  } finally {
    busy.value = false
  }
}
/** Hands a hand-set weight back to the rules. */
const reset = (p: AdminPerson) => act(() => post('/api/admin/raters/reset', { email: p.email }))
function flag(p: AdminPerson) {
  const note = p.flagged ? '' : window.prompt(`Flagg ${p.displayName} for juks i år. Notat:`, '')
  if (!p.flagged && note === null) return
  act(() => post('/api/admin/raters/flag', { email: p.email, flagged: !p.flagged, note: note ?? '' }))
}

async function save(p: AdminPerson) {
  error.value = ''
  busy.value = true
  try {
    await post('/api/admin/raters/override', { email: p.email, weight: weightIn.value, reason: reasonIn.value.trim() })
    editing.value = null
    await load()
  } catch (e) {
    error.value = e instanceof ApiFailure ? `${e.message} (${e.code})` : 'Noe gikk galt.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <h2>Admin: folk</h2>
  <p class="muted">
    {{ people.length }} har logget inn, {{ consented.length }} har samtykket,
    {{ consented.filter(noHistory).length }} av dem uten tidligere år koblet, {{ consented.filter(low).length }} har vekt under 1,
    {{ consented.filter(noTeam).length }} har ikke valgt lag.
    <template v-if="people.filter(staleConsent).length">{{ people.filter(staleConsent).length }} sa ja til en eldre tekst og blir spurt igjen ved neste innlogging.</template>
    Den som ikke har samtykket til gjeldende tekst vises bare med navn og e-post, uten vekt og uten tidligere år.
  </p>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <div class="row" style="margin-bottom: 8px">
    <input v-model="q" placeholder="Søk på navn, e-post eller gammelt navn" />
    <select v-model="filter" aria-label="Filter">
      <option value="all">Alle</option>
      <option value="noHistory">Uten tidligere år</option>
      <option value="low">Vekt under 1</option>
      <option value="manual">Satt for hånd</option>
      <option value="noConsent">Aldri samtykket</option>
      <option value="staleConsent">Eldre samtykke</option>
      <option value="noTeam">Uten lag</option>
    </select>
  </div>
  <div v-if="nag" class="card nag">
    <strong class="display">{{ filter === 'noConsent' ? 'Mas om samtykke' : filter === 'staleConsent' ? 'Mas om det nye samtykket' : 'Mas om lag' }}</strong>
    <p class="muted" style="margin: 4px 0 8px">
      {{ nag.who.length }} {{ nag.who.length === 1 ? 'person' : 'personer' }}. Appen maser selv når de logger inn; dette er for dem som ikke gjør det.
      E-posten åpnes i ditt eget program med alle som blindkopi og en ferdig tekst du kan endre.
    </p>
    <div class="row" style="justify-content: flex-start">
      <a v-if="mailto" class="btn" :href="mailto">Åpne e-post</a>
      <button class="secondary" :disabled="!nag.who.length" @click="copyEmails">{{ copied ? 'Kopiert' : 'Kopier adressene' }}</button>
    </div>
  </div>
  <div v-for="p in filtered" :key="p.id" class="card">
    <div class="row">
      <div>
        <strong>{{ p.displayName }}</strong>
        <div class="muted">{{ p.email }}</div>
      </div>
      <span v-if="staleConsent(p)" class="pill" :title="'Sa ja til ' + p.consentVersion + '; spørres igjen ved neste innlogging'">eldre samtykke ({{ p.consentVersion }})</span>
      <span v-else-if="!p.consented" class="pill">ikke samtykket</span>
      <span v-else class="pill" :class="{ on: (p.weight ?? 0) >= 1 }">vekt {{ p.weight }}<template v-if="p.overridden"> · for hånd</template><template v-if="p.flagged"> · flagget</template></span>
    </div>
    <template v-if="p.consented">
      <div class="muted" style="margin-top: 6px">
        {{ years(p) }}<template v-if="p.linkedNames.length"> · koblet: {{ p.linkedNames.join(', ') }}</template> · {{ p.scores }} terningkast her
        · {{ p.teams.length ? p.teams.join(' og ') : p.teamsChosen ? 'uten lag' : 'har ikke valgt lag' }}
      </div>
      <div class="muted">{{ p.reason }}</div>
      <div v-if="editing === p.email" class="row" style="margin-top: 8px; flex-wrap: wrap">
        <label style="margin: 0">Vekt <input v-model.number="weightIn" type="number" min="0" max="1" step="0.25" style="width: 5rem" /></label>
        <input v-model="reasonIn" placeholder="Grunn" aria-label="Grunn" style="flex: 1; min-width: 12rem" />
        <button :disabled="busy || !reasonIn.trim() || weightIn < 0 || weightIn > 1" @click="save(p)">Lagre</button>
        <button class="secondary" :disabled="busy" @click="editing = null">Avbryt</button>
      </div>
      <div v-else class="row" style="margin-top: 8px; justify-content: flex-start">
        <button v-if="(p.weight ?? 0) < 1" :disabled="busy" @click="open(p, 1)">Full vekt</button>
        <button class="secondary" :disabled="busy" @click="open(p, p.weight ?? 1)">Sett vekt…</button>
        <button v-if="p.overridden" class="secondary" :disabled="busy" title="La reglene avgjøre igjen" @click="reset(p)">Tilbakestill</button>
        <button class="secondary" :disabled="busy" @click="flag(p)">{{ p.flagged ? 'Fjern flagg' : 'Flagg' }}</button>
      </div>
    </template>
  </div>
  <p v-if="loaded && !filtered.length" class="muted">Ingen treff.</p>
</template>

<style scoped>
.nag { border: 1px dashed var(--brass); }
</style>
