<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { get, post } from '../api/client'
import type { Beer } from '../api/types'
import BeerPhotoBox from '../components/BeerPhotoBox.vue'

const q = ref('')
const beers = ref<Beer[]>([])
const adding = ref(false)
const draft = ref({ name: '', brewery: '', style: '', abv: undefined as number | undefined, homebrew: false })
const error = ref('')

async function load() {
  beers.value = await get<Beer[]>(`/api/beers?q=${encodeURIComponent(q.value)}`)
}

async function add() {
  error.value = ''
  try {
    await post('/api/beers', draft.value)
    draft.value = { name: '', brewery: '', style: '', abv: undefined, homebrew: false }
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
      <router-link :to="`/beers/${b.id}`" class="beer-link"><strong>{{ b.name }}</strong></router-link>
      <span v-if="b.homebrew" class="pill">hjemmebrygg</span>
      <span v-if="b.status === 'PENDING'" class="pill">venter på godkjenning</span>
      <span v-else-if="b.status === 'REJECTED'" class="pill">avvist</span>
      <div class="muted">{{ [b.brewery, b.style, b.abv ? b.abv + ' %' : null].filter(Boolean).join(' · ') }}</div>
      <div class="muted">{{ b.ratingCount }} {{ b.ratingCount === 1 ? 'smaking' : 'smakinger' }}</div>
      <BeerPhotoBox :beer="b" @changed="load" />
    </div>
    <router-link class="btn secondary" :to="`/beers/${b.id}/rate`">Smak</router-link>
  </div>
  <p v-if="!beers.length" class="muted">Ingen treff.</p>
  <p v-else class="muted" style="margin: -4px 0 12px">Trykk på et øl for siden med fordeling, stikkord og notater.</p>

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
    <label style="display: flex; gap: 8px; align-items: center; margin-top: 12px">
      <input v-model="draft.homebrew" type="checkbox" style="width: auto" /> Hjemmebrygg
    </label>
    <p v-if="draft.homebrew" class="muted" style="margin: 4px 0 0">Hjemmebrygg er velkomment når det er like sterkt som Polets. Det smakes bare rundt bord med mer enn fem, og kommer på resultatlisten først etter fire terningkast, så ingen kan jukse med sitt eget.</p>
    <p v-if="error" class="error">{{ error }}</p>
    <button style="margin-top: 12px">Lagre</button>
  </form>
</template>
