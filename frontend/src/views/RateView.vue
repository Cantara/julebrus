<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiFailure, get, post } from '../api/client'
import type { Beer, HistoricRating, NewTasting, StructuredNotes, Tasting } from '../api/types'
import BeerPhotoBox from '../components/BeerPhotoBox.vue'
import DiceFace from '../components/DiceFace.vue'
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
/** One roll per beer per person, ever: once there is one, the page shows it instead of a form that can only fail. */
const mine = ref<Tasting | null>(null)
const loaded = ref(false)

async function loadMine() {
  const tastings = await get<Tasting[]>('/api/tastings/mine').catch(() => [] as Tasting[])
  mine.value = tastings.find((t) => t.beerId === props.id) ?? null
}
const NOTE_LABELS: [keyof StructuredNotes, string][] = [['appearance', 'Utseende'], ['aroma', 'Lukt'], ['taste', 'Smak'], ['finish', 'Ettersmak']]
const myNotes = computed(() => NOTE_LABELS.filter(([k]) => mine.value?.structuredNotes?.[k]).map(([k, label]) => ({ label, text: mine.value!.structuredNotes![k]! })))

const sessionId = typeof route.query.session === 'string' ? route.query.session : undefined

async function loadBeer() {
  const all = await get<Beer[]>('/api/beers')
  beer.value = all.find((b) => b.id === props.id) ?? null
}

onMounted(async () => {
  await Promise.all([loadBeer(), loadMine()])
  loaded.value = true
  if (beer.value) {
    history.value = await get<HistoricRating[]>(`/api/history/beers?name=${encodeURIComponent(beer.value.name)}`).catch(() => [])
  }
})

const RULE_TEXT: Record<string, string> = {
  OUTSIDE_SEASON: 'Sesongen er ikke i gang. 1. november til 23. desember.',
  DAILY_LIMIT: 'Nok for i dag. Palaten takker deg i morgen. Fem er grensen, tolv ved et bord med åtte eller flere.',
  SOLO_DAYS_EXHAUSTED: 'Alle solo-dagene er brukt opp. Finn noen å smake med.',
  ALREADY_RATED: 'Du har allerede gitt dette ølet terningkast.',
  HOMEBREW_NEEDS_GROUP: 'Hjemmebrygg smakes bare rundt bord med mer enn fem. Få flere til bordet først.',
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
    router.replace(sessionId ? `/session/${sessionId}?just=${props.id}` : `/?opened=${store.me?.status.today ?? ''}`)
  } catch (e) {
    // Rolled already (another tab, another phone): show that roll instead of an error.
    if (e instanceof ApiFailure && e.code === 'ALREADY_RATED') {
      await loadMine()
      if (mine.value) return
    }
    error.value = e instanceof ApiFailure ? (RULE_TEXT[e.code] ?? e.message) : (e as Error).message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <h1>{{ beer?.name ?? '…' }}</h1>
  <p class="muted">{{ beer?.brewery }}</p>
  <BeerPhotoBox v-if="beer" :beer="beer" @changed="loadBeer" />

  <div v-if="mine" class="card mine">
    <div class="row">
      <div>
        <strong class="display">Ditt terningkast</strong>
        <div class="muted">
          {{ new Date(mine.tastedDay + 'T00:00:00').toLocaleDateString('nb-NO', { weekday: 'long', day: 'numeric', month: 'long' }) }}<template v-if="mine.sessionId"> · ved et bord</template>
        </div>
      </div>
      <DiceFace :value="mine.score" :size="48" />
    </div>
    <p v-if="mine.notes" style="margin: 10px 0 0">«{{ mine.notes }}»</p>
    <p v-for="n in myNotes" :key="n.label" class="muted" style="margin: 4px 0 0"><strong>{{ n.label }}:</strong> {{ n.text }}</p>
    <div v-if="mine.tags.length" style="margin-top: 6px"><span v-for="t in mine.tags" :key="t" class="pill on">{{ t }}</span></div>
    <p class="muted" style="margin: 10px 0 0">Ett terningkast per øl, for alltid. Se hva de andre syntes på ølets side.</p>
    <div class="row" style="margin-top: 10px; justify-content: flex-start; gap: 8px">
      <router-link class="btn" :to="`/beers/${props.id}`">Til ølets side</router-link>
      <router-link v-if="sessionId" class="btn secondary" :to="`/session/${sessionId}`">Tilbake til bordet</router-link>
    </div>
  </div>

  <form v-else-if="loaded" class="card" @submit.prevent="save">
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

  <details v-if="history.length" class="card">
    <summary><strong>Tidligere år</strong> <span class="muted">· {{ history.length }} terningkast siden {{ Math.min(...history.map((h) => h.year)) }}</span> · <router-link v-if="beer" class="lnk" :to="`/historikk/ol/${encodeURIComponent(beer.name)}`">arkivsiden</router-link></summary>
    <table>
      <thead><tr><th>År</th><th>Hvem</th><th>Score</th><th>Vekt</th><th>På 1–6</th></tr></thead>
      <tbody>
        <tr v-for="h in history" :key="h.year + h.raterName">
          <td>{{ h.year }}</td><td>{{ h.raterKey }}</td><td>{{ h.score }}</td><td>{{ h.weight }}</td><td>{{ h.normalised.toFixed(1) }}</td>
        </tr>
      </tbody>
    </table>
  </details>
</template>

<style scoped>
.mine { border-color: var(--candle); }
</style>
