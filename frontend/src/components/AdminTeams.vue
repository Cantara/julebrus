<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiFailure, get, post } from '../api/client'
import type { AdminTeam } from '../api/types'

const teams = ref<AdminTeam[]>([])
const error = ref('')
const draft = ref({ name: '', sponsorName: '', sponsorUrl: '', domains: '' })

async function load() {
  teams.value = await get<AdminTeam[]>('/api/admin/teams')
}
onMounted(() => load().catch(() => (error.value = 'Kunne ikke hente lagene.')))

async function run(fn: () => Promise<unknown>) {
  error.value = ''
  try {
    await fn()
  } catch (e) {
    error.value = e instanceof ApiFailure ? `Kunne ikke lagre (${e.code}).` : 'Kunne ikke lagre.'
  }
  await load().catch(() => {})
}

const create = () => run(async () => {
  await post('/api/admin/teams', draft.value)
  draft.value = { name: '', sponsorName: '', sponsorUrl: '', domains: '' }
})
const setEnabled = (t: AdminTeam, enabled: boolean) => run(() => post(`/api/admin/teams/${t.id}`, { enabled }))
</script>

<template>
  <h2>Admin: lag</h2>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <div v-for="t in teams" :key="t.id" class="card row">
    <div>
      <strong>{{ t.name }}</strong> <span class="muted">/{{ t.slug }}</span>
      <div class="muted">{{ [t.members + (t.members === 1 ? ' smaker' : ' smakere'), t.sponsorName ? 'støttet av ' + t.sponsorName : null, t.domains ? 'foreslås for ' + t.domains : null].filter(Boolean).join(' · ') }}</div>
    </div>
    <button class="secondary" @click="setEnabled(t, !t.enabled)">{{ t.enabled ? 'Skru av' : 'Skru på' }}</button>
  </div>
  <div class="card">
    <label>Nytt lag</label>
    <input v-model="draft.name" placeholder="Navn" />
    <input v-model="draft.sponsorName" placeholder="Sponsor (valgfritt)" />
    <input v-model="draft.sponsorUrl" placeholder="Sponsor-URL (valgfritt)" />
    <input v-model="draft.domains" placeholder="E-postdomener som foreslår laget, f.eks. firma.no" />
    <button style="margin-top: 8px" :disabled="!draft.name.trim()" @click="create">Opprett</button>
  </div>
</template>
