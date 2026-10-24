<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { get, post } from '../api/client'
import type { Beer } from '../api/types'

const q = ref('')
const beers = ref<Beer[]>([])
const adding = ref(false)
const draft = ref({ name: '', brewery: '', style: '', abv: undefined as number | undefined })
const error = ref('')

async function load() {
  beers.value = await get<Beer[]>(`/api/beers?q=${encodeURIComponent(q.value)}`)
}

async function add() {
  error.value = ''
  try {
    await post('/api/beers', draft.value)
    draft.value = { name: '', brewery: '', style: '', abv: undefined }
    adding.value = false
    await load()
  } catch (e) {
    error.value = (e as Error).message
  }
}

let timer: number | undefined
watch(q, () => {
  clearTimeout(timer)
  timer = window.setTimeout(load, 200)
})
onMounted(load)
</script>

<template>
  <h1>Ølkatalogen</h1>
  <input v-model="q" placeholder="Søk på navn eller bryggeri" />

  <div v-for="b in beers" :key="b.id" class="card row">
    <div>
      <strong>{{ b.name }}</strong>
      <span v-if="b.status === 'PENDING'" class="pill">venter på godkjenning</span>
      <span v-else-if="b.status === 'REJECTED'" class="pill">avvist</span>
      <div class="muted">{{ [b.brewery, b.style, b.abv ? b.abv + ' %' : null].filter(Boolean).join(' · ') }}</div>
      <div class="muted">{{ b.ratingCount }} smakinger</div>
    </div>
    <router-link class="btn secondary" :to="`/beers/${b.id}/rate`">Smak</router-link>
  </div>
  <p v-if="!beers.length" class="muted">Ingen treff.</p>

  <button class="secondary" style="width: 100%" @click="adding = !adding">Legg til et øl som mangler</button>
  <form v-if="adding" class="card" @submit.prevent="add">
    <p class="muted" style="margin-top: 0">Bare juleøl fra Polet teller, altså over butikkstyrke (4,7 %). Ølet kan smakes med en gang, men teller ikke i resultatlisten før en administrator har godkjent det.</p>
    <label>Navn</label>
    <input v-model="draft.name" required />
    <label>Bryggeri</label>
    <input v-model="draft.brewery" />
    <label>Stil</label>
    <input v-model="draft.style" placeholder="Juleøl, bokk, porter …" />
    <label>Alkohol %</label>
    <input v-model.number="draft.abv" type="number" step="0.1" min="0" max="20" />
    <p v-if="error" class="error">{{ error }}</p>
    <button style="margin-top: 12px">Lagre</button>
  </form>
</template>
