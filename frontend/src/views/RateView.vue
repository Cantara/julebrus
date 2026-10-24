<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiFailure, get, post } from '../api/client'
import type { Beer, HistoricRating, NewTasting, StructuredNotes, Tasting } from '../api/types'
import ScoreDice from '../components/ScoreDice.vue'
import TastingNotes from '../components/TastingNotes.vue'
import { useMe } from '../stores/me'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()
const store = useMe()

const beer = ref<Beer | null>(null)
const history = ref<HistoricRating[]>([])
const score = ref<number | null>(null)
const prediction = ref<number | undefined>()
const notes = ref('')
const structured = ref<StructuredNotes>({})
const tags = ref<string[]>([])
const error = ref('')
const saving = ref(false)

const sessionId = typeof route.query.session === 'string' ? route.query.session : undefined

onMounted(async () => {
  const all = await get<Beer[]>('/api/beers')
  beer.value = all.find((b) => b.id === props.id) ?? null
  if (beer.value) {
    history.value = await get<HistoricRating[]>(`/api/history/beers?name=${encodeURIComponent(beer.value.name)}`).catch(() => [])
  }
})

const RULE_TEXT: Record<string, string> = {
  OUTSIDE_SEASON: 'Sesongen er ikke i gang. 1. november til 23. desember.',
  DAILY_LIMIT: 'Fem er nok for i dag. Palaten takker deg i morgen.',
  SOLO_DAYS_EXHAUSTED: 'Alle solo-dagene er brukt opp. Finn noen å smake med.',
  ALREADY_RATED: 'Du har allerede gitt dette ølet terningkast.',
}

async function save() {
  if (!score.value) return
  saving.value = true
  error.value = ''
  const body: NewTasting = {
    beerId: props.id,
    sessionId,
    score: score.value,
    prediction: sessionId ? prediction.value : undefined,
    notes: notes.value || undefined,
    ...structured.value,
    tags: tags.value,
  }
  try {
    await post<Tasting>('/api/tastings', body)
    await store.refresh()
    router.replace(sessionId ? `/session/${sessionId}` : '/')
  } catch (e) {
    error.value = e instanceof ApiFailure ? (RULE_TEXT[e.code] ?? e.message) : (e as Error).message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <h1>{{ beer?.name ?? '…' }}</h1>
  <p class="muted">{{ beer?.brewery }}</p>

  <div v-if="history.length" class="card">
    <strong>Tidligere år</strong>
    <table>
      <thead><tr><th>År</th><th>Hvem</th><th>Score</th><th>Vekt</th><th>På 1–6</th></tr></thead>
      <tbody>
        <tr v-for="h in history" :key="h.year + h.raterName">
          <td>{{ h.year }}</td><td>{{ h.raterName }}</td><td>{{ h.score }}</td><td>{{ h.weight }}</td><td>{{ h.normalised.toFixed(1) }}</td>
        </tr>
      </tbody>
    </table>
  </div>

  <form class="card" @submit.prevent="save">
    <label>Terningkast</label>
    <ScoreDice v-model="score" />

    <template v-if="sessionId">
      <label>Gjett gruppens snitt (valgfritt, gir Sommelier-merket)</label>
      <input v-model.number="prediction" type="number" min="1" max="6" step="0.1" placeholder="3.8" />
    </template>

    <label>Kommentar</label>
    <textarea v-model="notes" rows="2" placeholder="Kort og ærlig" />

    <div style="margin-top: 12px">
      <TastingNotes v-model:notes="structured" v-model:tags="tags" />
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <button :disabled="!score || saving" style="width: 100%; margin-top: 14px">Lagre</button>
  </form>
</template>
