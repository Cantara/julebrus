<script setup lang="ts">
/**
 * Tipp pallen: gold, silver, bronze among the beers that can win. One tip per person,
 * editable until the last day begins, scored against the official podium at the reveal.
 */
import { computed, onMounted, ref } from 'vue'
import { ApiFailure, del, get, put } from '../api/client'
import type { Beer, MyTip } from '../api/types'

const beers = ref<Beer[]>([])
const my = ref<MyTip | null>(null)
const picks = ref<[string, string, string]>(['', '', ''])
const error = ref('')
const saved = ref(false)
const busy = ref(false)

const candidates = computed(() => beers.value.filter((b) => b.counts).sort((a, b) => a.name.localeCompare(b.name, 'nb')))
const deadline = computed(() => (my.value ? new Date(my.value.deadline) : null))
const deadlineText = computed(() => {
  if (!deadline.value) return ''
  // The deadline is midnight at the start of the last day: say the day before, which is what people mean.
  const lastMoment = new Date(deadline.value.getTime() - 60000)
  return lastMoment.toLocaleDateString('nb-NO', { day: 'numeric', month: 'long' })
})
const complete = computed(() => picks.value.every(Boolean) && new Set(picks.value).size === 3)
const MEDALS = ['🥇', '🥈', '🥉']

function available(slot: number) {
  return candidates.value.filter((b) => !picks.value.some((p, i) => i !== slot && p === b.id))
}

async function load() {
  my.value = await get<MyTip>('/api/tips/me')
  if (my.value.tip) picks.value = my.value.tip.podium.map((p) => p.beerId) as [string, string, string]
}

onMounted(async () => {
  try {
    ;[beers.value] = await Promise.all([get<Beer[]>('/api/beers'), load()])
  } catch {
    error.value = 'Kunne ikke hente tippingen.'
  }
})

async function run(fn: () => Promise<unknown>) {
  busy.value = true
  error.value = ''
  saved.value = false
  try {
    await fn()
    await load()
    saved.value = true
  } catch (e) {
    error.value = e instanceof ApiFailure ? (e.code === 'TIP_CLOSED' ? 'Tippingen er stengt.' : `Kunne ikke lagre (${e.code}).`) : 'Kunne ikke lagre.'
  } finally {
    busy.value = false
  }
}
const save = () => run(() => put('/api/tips', { firstBeerId: picks.value[0], secondBeerId: picks.value[1], thirdBeerId: picks.value[2] }))
const withdraw = () => run(async () => {
  await del('/api/tips')
  picks.value = ['', '', '']
})
</script>

<template>
  <h1>Tipp pallen</h1>
  <p class="muted">Hvem vinner Cantaras juleøl i år? Tre riktige på rett plass gir ni poeng; et øl på pallen men feil plass gir ett. Tippefristen er {{ deadlineText }} ved midnatt. Alles tips vises i finalen, og den beste tipperen får en pris.</p>
  <p v-if="error" class="error" role="alert">{{ error }}</p>

  <div v-if="my?.closed" class="card">
    <strong>Tippingen er stengt.</strong>
    <p v-if="my.tip" class="muted" style="margin: 6px 0 0">Du tippet <span v-for="(p, i) in my.tip.podium" :key="p.beerId">{{ MEDALS[i] }} {{ p.name }}<template v-if="i < 2">, </template></span>.</p>
    <p v-else class="muted" style="margin: 6px 0 0">Du rakk ikke å tippe i år.</p>
    <p style="margin: 10px 0 0"><router-link to="/finale">★ Til finalen</router-link></p>
  </div>

  <template v-else-if="my">
    <div class="card">
      <template v-for="(label, slot) in ['Gull', 'Sølv', 'Bronse']" :key="label">
        <label>{{ MEDALS[slot] }} {{ label }}</label>
        <select v-model="picks[slot]">
          <option value="">Velg øl</option>
          <option v-for="b in available(slot)" :key="b.id" :value="b.id">{{ b.name }}<template v-if="b.brewery"> · {{ b.brewery }}</template></option>
        </select>
      </template>
      <p v-if="!candidates.length" class="muted" style="margin-top: 10px">Ingen øl å tippe på ennå. Katalogen fylles når Polet slipper listen.</p>
      <div class="row" style="margin-top: 12px">
        <button v-if="my.tip" class="secondary" :disabled="busy" @click="withdraw">Trekk tipset</button>
        <span v-else />
        <button :disabled="busy || !complete" @click="save">{{ my.tip ? 'Endre tipset' : 'Lever tipset' }}</button>
      </div>
      <p v-if="saved" class="ok" style="margin: 8px 0 0">Lagret. Du kan endre det fram til fristen.</p>
    </div>
    <p class="muted" style="text-align: center">{{ my.tipped }} har tippet så langt.</p>
  </template>
</template>
