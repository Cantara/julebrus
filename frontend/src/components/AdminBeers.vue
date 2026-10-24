<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiFailure, get, post } from '../api/client'
import type { PendingBeer } from '../api/types'

const pending = ref<PendingBeer[]>([])
const error = ref('')

async function load() {
  pending.value = await get<PendingBeer[]>('/api/admin/beers?status=PENDING')
}
onMounted(() => load().catch(() => (error.value = 'Kunne ikke hente listen.')))

async function decide(b: PendingBeer, decision: 'APPROVE' | 'REJECT') {
  error.value = ''
  try {
    await post(`/api/admin/beers/${b.id}/decision`, { decision })
  } catch (e) {
    error.value = e instanceof ApiFailure ? `Kunne ikke lagre (${e.code}).` : 'Kunne ikke lagre.'
  }
  await load().catch(() => {})
}
</script>

<template>
  <h2>Admin: nye øl</h2>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="!pending.length" class="muted">Ingen øl venter på godkjenning.</p>
  <div v-for="b in pending" :key="b.id" class="card">
    <strong>{{ b.name }}</strong>
    <div class="muted">{{ [b.brewery, b.style, b.abv ? b.abv + ' %' : null, b.vinmonopoletId ? 'Polet ' + b.vinmonopoletId : null].filter(Boolean).join(' · ') }}</div>
    <div class="muted">Lagt til av {{ b.addedBy ?? '?' }}</div>
    <div class="row" style="margin-top: 8px; justify-content: flex-start">
      <button @click="decide(b, 'APPROVE')">Godkjenn</button>
      <button class="secondary" @click="decide(b, 'REJECT')">Avvis</button>
    </div>
  </div>
</template>
