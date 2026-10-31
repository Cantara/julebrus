<script setup lang="ts">
/**
 * One beer's page: facts and label, this season's numbers, its duel record, what the room
 * wrote about it, your own roll, and what earlier years said. Anything from other people's
 * rolls shows only from three rolls, and the notes only after you have rolled it yourself.
 */
import { computed, onMounted, ref, watch } from 'vue'
import { get } from '../api/client'
import type { BeerPage } from '../api/types'
import BeerPhotoBox from '../components/BeerPhotoBox.vue'
import DiceFace from '../components/DiceFace.vue'
import { useMe } from '../stores/me'

const props = defineProps<{ id: string }>()
const store = useMe()
const page = ref<BeerPage | null>(null)
const error = ref('')

async function load() {
  error.value = ''
  try {
    page.value = await get<BeerPage>(`/api/beers/${props.id}`)
  } catch {
    error.value = 'Fant ikke ølet.'
  }
}
onMounted(load)
watch(() => props.id, load)

const facts = computed(() => {
  const b = page.value?.beer
  return b ? [b.brewery, b.style, b.abv ? `${b.abv} %` : null].filter(Boolean).join(' · ') : ''
})
const histMax = computed(() => Math.max(1, ...(page.value?.season.histogram ?? [0])))
const canRate = computed(() => page.value && !page.value.mine && page.value.beer.status !== 'REJECTED' && store.me?.status.inSeason)
const NOTE_FIELDS: [keyof BeerPage['notes'][number], string][] = [
  ['appearance', 'Utseende'],
  ['aroma', 'Lukt'],
  ['taste', 'Smak'],
  ['finish', 'Ettersmak'],
]
</script>

<template>
  <p class="muted"><router-link to="/beers">‹ Ølkatalogen</router-link></p>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <template v-if="page">
    <h1 style="margin-bottom: 2px">{{ page.beer.name }}</h1>
    <p class="muted" style="margin-top: 0">
      {{ facts }}
      <span v-if="page.beer.homebrew" class="pill">hjemmebrygg</span>
      <span v-if="page.beer.status === 'PENDING'" class="pill">venter på godkjenning</span>
      <span v-else-if="page.beer.status === 'REJECTED'" class="pill">avvist</span>
      <span v-else-if="!page.beer.counts" class="pill">teller ikke</span>
    </p>
    <BeerPhotoBox :beer="page.beer" @changed="load" />

    <div v-if="page.mine" class="card row mine">
      <div>
        <strong class="display">Du ga</strong>
        <div class="muted">
          {{ new Date(page.mine.tastedDay + 'T00:00:00').toLocaleDateString('nb-NO', { day: 'numeric', month: 'long' }) }}
          <template v-if="page.season.mean !== null && page.season.mean !== undefined && page.season.tastings >= page.minShared">
            · flokken {{ page.season.mean.toFixed(2) }}
          </template>
        </div>
        <div v-if="page.mine.tags.length" style="margin-top: 4px"><span v-for="t in page.mine.tags" :key="t" class="pill on">{{ t }}</span></div>
        <p v-if="page.mine.notes" class="muted" style="margin: 4px 0 0">«{{ page.mine.notes }}»</p>
      </div>
      <DiceFace :value="page.mine.score" :size="44" />
    </div>
    <router-link v-else-if="canRate" class="btn" :to="`/beers/${page.beer.id}/rate`" style="display: block; text-align: center; margin-bottom: 12px">Gi terningkast</router-link>

    <h2>I år</h2>
    <div class="card">
      <div class="row" style="align-items: baseline">
        <div>
          <strong v-if="page.season.rank" class="display">Nr. {{ page.season.rank }} av {{ page.season.rankedOf }}</strong>
          <strong v-else-if="page.season.blackout" class="display">🌒 Mørkelagt til finalen</strong>
          <strong v-else class="display">{{ page.season.tastings ? 'Ikke på listen' : 'Ingen har smakt ennå' }}</strong>
          <div class="muted">
            {{ page.season.tastings }} terningkast fra {{ page.season.people }} {{ page.season.people === 1 ? 'smaker' : 'smakere' }}
            <template v-if="page.season.rank"> · snitt {{ page.season.mean?.toFixed(2) }}</template>
          </div>
        </div>
        <strong v-if="page.season.bayesian" class="display big">{{ page.season.bayesian.toFixed(2) }}</strong>
      </div>
      <div v-if="page.season.histogram" class="hist" role="img" :aria-label="`Fordeling av terningkast: ${page.season.histogram.join(', ')}`">
        <div v-for="(n, i) in page.season.histogram" :key="i" class="col">
          <span class="n">{{ n || '' }}</span>
          <span class="bar" :style="{ height: `${(n / histMax) * 56}px` }" />
          <DiceFace :value="i + 1" :size="20" />
        </div>
      </div>
      <p v-else-if="page.season.blackout" class="muted" style="margin: 8px 0 0">Plassering, snitt og terninger er skjult den siste uka før finalen. Lukene i kalenderen gir et spor om lederen hver dag.</p>
      <p v-else-if="page.season.tastings" class="muted" style="margin: 8px 0 0">Fordelingen vises når {{ page.minShared }} har kastet. {{ page.minShared - page.season.tastings }} til.</p>
      <p v-if="page.duels.wins + page.duels.losses" class="muted" style="margin: 8px 0 0">
        Dueller: {{ page.duels.wins }} {{ page.duels.wins === 1 ? 'seier' : 'seire' }}, {{ page.duels.losses }} tap<template v-if="page.duels.rank"> · nr. {{ page.duels.rank }} i duellrangeringen</template>
      </p>
    </div>

    <h2>Hva rommet sier</h2>
    <div class="card">
      <template v-if="page.tags">
        <p class="muted" style="margin-top: 0">Stikkordene folk satte på ølet.</p>
        <div v-if="page.tags.length"><span v-for="t in page.tags" :key="t.tag" class="pill" :style="{ fontSize: `${0.8 + Math.min(0.5, (t.count - 1) * 0.12)}rem` }">{{ t.tag }} <span class="muted">{{ t.count }}</span></span></div>
        <p v-else class="muted">Ingen stikkord ennå.</p>
      </template>
      <p v-else class="muted" style="margin-top: 0">Stikkordene og notatene åpner når {{ page.minShared }} har smakt ølet.</p>

      <template v-if="page.notesOpen">
        <p class="muted" style="margin: 12px 0 6px">Smaksnotatene, uten navn og i tilfeldig rekkefølge. Les, sammenlign med din egen, og bli litt skarpere til neste glass.</p>
        <ul v-if="page.notes.length" class="notes">
          <li v-for="(n, i) in page.notes" :key="i">
            <template v-for="[field, label] in NOTE_FIELDS" :key="field">
              <div v-if="n[field]"><span class="muted">{{ label }}:</span> {{ n[field] }}</div>
            </template>
            <div v-if="n.tags.length" style="margin-top: 2px"><span v-for="t in n.tags" :key="t" class="pill">{{ t }}</span></div>
          </li>
        </ul>
        <p v-else class="muted">Ingen har skrevet notater ennå. Vær den første, så har de andre noe å lære av.</p>
      </template>
      <p v-else-if="page.notesLocked === 'NEED_ROLL' && page.tags" class="muted" style="margin: 12px 0 0">
        🔒 Smak først, les etterpå. Notatene åpner når du selv har gitt ølet terningkast.
      </p>
    </div>

    <template v-if="page.history.length">
      <h2>Tidligere år</h2>
      <div class="card">
        <table>
          <thead><tr><th>År</th><th style="text-align: right">Snitt 1–6</th><th style="text-align: right">Terningkast</th></tr></thead>
          <tbody>
            <tr v-for="h in page.history" :key="h.year"><td>{{ h.year }}</td><td style="text-align: right">{{ h.mean.toFixed(2) }}</td><td style="text-align: right">{{ h.ratings }}</td></tr>
          </tbody>
        </table>
        <p class="muted" style="margin: 8px 0 0"><router-link class="lnk" :to="`/historikk/ol/${encodeURIComponent(page.beer.name)}`">Stemmene år for år</router-link> · <router-link class="lnk" to="/historikk">hele historikken</router-link></p>
      </div>
    </template>
  </template>
</template>

<style scoped>
.mine { border-color: var(--candle); }
.big { font-size: 1.6rem; }
.hist { display: grid; grid-template-columns: repeat(6, 1fr); gap: 6px; align-items: end; margin-top: 12px; }
.hist .col { display: grid; justify-items: center; gap: 4px; }
.hist .n { font-size: 0.75rem; color: var(--muted); min-height: 1em; font-variant-numeric: tabular-nums; }
.hist .bar { display: block; width: 70%; min-height: 2px; background: var(--candle); border-radius: 4px 4px 0 0; }
.notes { list-style: none; margin: 0; padding: 0; }
.notes li { padding: 8px 0; border-top: 1px dashed var(--line); }
.notes li:first-child { border-top: none; }
</style>
