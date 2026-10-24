<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '../api/client'
import type { Session } from '../api/types'

const router = useRouter()
const error = ref('')

async function start() {
  try {
    const s = await post<Session>('/api/sessions')
    router.push(`/session/${s.id}`)
  } catch (e) {
    error.value = (e as Error).message
  }
}
</script>

<template>
  <h1>Smaking</h1>
  <div class="card">
    <p>Åpne en smaking og vis QR-koden til de andre rundt bordet. Alle gir terningkast hver for seg, og scorene vises først når alle har svart.</p>
    <button style="width: 100%" @click="start">Åpne ny smaking</button>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
  <div class="card">
    <p>Er du gjest? Skann vertens QR-kode med kameraet, eller skriv inn koden.</p>
    <router-link class="btn secondary" style="width: 100%; text-align: center" to="/join">Bli med i en smaking</router-link>
  </div>
  <p class="muted">Smaker du alene, går du rett på et øl i katalogen. Det bruker en av fem solo-dager.</p>
</template>
