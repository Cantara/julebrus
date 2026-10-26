<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiFailure, get, post } from '../api/client'
import type { AdminPerson } from '../api/types'

/**
 * Everyone who has signed in: who is mapped to earlier seasons, and what each person's vote counts this season.
 * The weight is decided by the rules (a rookie 0.5, a veteran 1.0) and can be set by hand when the rules are
 * wrong, for example when the last seasons' data is missing. A hand-set weight stays.
 */
const people = ref<AdminPerson[]>([])
const q = ref('')
const filter = ref<'all' | 'noHistory' | 'low' | 'noConsent' | 'manual'>('all')
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

const filtered = computed(() => {
  const needle = q.value.trim().toLowerCase()
  return people.value
    .filter((p) => !needle || p.displayName.toLowerCase().includes(needle) || p.email.toLowerCase().includes(needle) || p.linkedNames.some((n) => n.toLowerCase().includes(needle)))
    .filter((p) =>
      filter.value === 'all' ? true
        : filter.value === 'noHistory' ? noHistory(p)
        : filter.value === 'low' ? low(p)
        : filter.value === 'manual' ? p.overridden
        : !p.consented)
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
    {{ consented.filter(noHistory).length }} av dem uten tidligere år koblet, {{ consented.filter(low).length }} har vekt under 1.
    Den som ikke har samtykket vises bare med navn og e-post, uten vekt og uten tidligere år.
  </p>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <div class="row" style="margin-bottom: 8px">
    <input v-model="q" placeholder="Søk på navn, e-post eller gammelt navn" />
    <select v-model="filter" aria-label="Filter">
      <option value="all">Alle</option>
      <option value="noHistory">Uten tidligere år</option>
      <option value="low">Vekt under 1</option>
      <option value="manual">Satt for hånd</option>
      <option value="noConsent">Uten samtykke</option>
    </select>
  </div>
  <div v-for="p in filtered" :key="p.id" class="card">
    <div class="row">
      <div>
        <strong>{{ p.displayName }}</strong>
        <div class="muted">{{ p.email }}</div>
      </div>
      <span v-if="!p.consented" class="pill">ikke samtykket</span>
      <span v-else class="pill" :class="{ on: (p.weight ?? 0) >= 1 }">vekt {{ p.weight }}<template v-if="p.overridden"> · for hånd</template><template v-if="p.flagged"> · flagget</template></span>
    </div>
    <template v-if="p.consented">
      <div class="muted" style="margin-top: 6px">
        {{ years(p) }}<template v-if="p.linkedNames.length"> · koblet: {{ p.linkedNames.join(', ') }}</template> · {{ p.scores }} terningkast her
      </div>
      <div class="muted">{{ p.reason }}</div>
      <div v-if="editing === p.email" class="row" style="margin-top: 8px; flex-wrap: wrap">
        <label style="margin: 0">Vekt <input v-model.number="weightIn" type="number" min="0" max="1" step="0.25" style="width: 5rem" /></label>
        <input v-model="reasonIn" placeholder="Grunn" aria-label="Grunn" style="flex: 1; min-width: 12rem" />
        <button :disabled="busy || !reasonIn.trim() || weightIn < 0 || weightIn > 1" @click="save(p)">Lagre</button>
        <button class="secondary" :disabled="busy" @click="editing = null">Avbryt</button>
      </div>
      <div v-else class="row" style="margin-top: 8px; justify-content: flex-start">
        <button v-if="(p.weight ?? 0) < 1" @click="open(p, 1)">Full vekt</button>
        <button class="secondary" @click="open(p, p.weight ?? 1)">Sett vekt…</button>
      </div>
    </template>
  </div>
  <p v-if="loaded && !filtered.length" class="muted">Ingen treff.</p>
</template>
