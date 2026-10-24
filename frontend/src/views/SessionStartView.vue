<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get, post } from '../api/client'
import type { MySession, Session } from '../api/types'

const router = useRouter()
const error = ref('')
const tables = ref<MySession[]>([])
onMounted(() => get<MySession[]>('/api/sessions/mine').then((t) => (tables.value = t)).catch(() => {}))
const when = (iso: string) => new Date(iso).toLocaleDateString('nb-NO', { day: 'numeric', month: 'short' })

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

  <template v-if="tables.length">
    <h2 style="margin-top: 0">Dine bord</h2>
    <router-link v-for="t in tables" :key="t.id" class="card row" :to="`/session/${t.id}`" style="text-decoration: none">
      <div>
        <strong>{{ t.host ? 'Ditt bord' : `Hos ${t.hostName}` }}</strong>
        <span v-if="!t.closedAt" class="pill on" style="margin-left: 8px">åpent</span>
        <div class="muted">
          {{ when(t.createdAt) }} · {{ t.members }} rundt bordet · {{ t.beers }} øl
          <template v-if="!t.closedAt && t.pendingForMe > 0"> · <strong style="color: var(--brand)">{{ t.pendingForMe }} venter på deg</strong></template>
        </div>
      </div>
      <span class="muted">›</span>
    </router-link>
  </template>

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
