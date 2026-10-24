<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiFailure, get, post } from '../api/client'
import type { Login, RaterRow } from '../api/types'

/**
 * The mapping table: historic names on the left, logins on the right. An admin
 * picks a login and links, or unlinks. Names tied through raters.csv are shown
 * but locked; that file is the stronger source.
 */
const rows = ref<RaterRow[]>([])
const logins = ref<Login[]>([])
const q = ref('')
const onlyUnlinked = ref(false)
const choice = ref<Record<string, string>>({})
const error = ref('')
const busy = ref<string | null>(null)

async function load() {
  ;[rows.value, logins.value] = await Promise.all([
    get<RaterRow[]>('/api/admin/history/raters'),
    get<Login[]>('/api/admin/history/raters/logins'),
  ])
  // An empty string selects the placeholder option; undefined would leave the select blank.
  for (const r of rows.value) choice.value[r.raterKey] ??= ''
}
onMounted(() => load().catch(() => (error.value = 'Kunne ikke hente listen.')))

const filtered = computed(() => {
  const needle = q.value.trim().toLowerCase()
  return rows.value
    .filter((r) => !onlyUnlinked.value || (!r.linkedTo && !r.linkedByEmail))
    .filter((r) => !needle || r.raterKey.toLowerCase().includes(needle) || (r.linkedTo?.displayName ?? '').toLowerCase().includes(needle))
})
const linkedCount = computed(() => rows.value.filter((r) => r.linkedTo || r.linkedByEmail).length)

async function link(r: RaterRow) {
  const userId = choice.value[r.raterKey]
  if (!userId) return
  await act(r, () => post(`/api/admin/history/raters/link`, { raterKey: r.raterKey, userId }))
}
async function unlink(r: RaterRow) {
  await act(r, () => post(`/api/admin/history/raters/unlink`, { raterKey: r.raterKey }))
}
async function act(r: RaterRow, fn: () => Promise<unknown>) {
  error.value = ''
  busy.value = r.raterKey
  try {
    await fn()
    await load()
  } catch (e) {
    error.value = e instanceof ApiFailure ? `${e.message} (${e.code})` : 'Noe gikk galt.'
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <h2>Admin: koble historiske navn til innlogginger</h2>
  <p class="muted">{{ linkedCount }} av {{ rows.length }} navn er koblet. Folk som har logget inn minst én gang dukker opp i lista.</p>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <div class="row" style="margin-bottom: 8px">
    <input v-model="q" placeholder="Søk på navn" />
    <label style="margin: 0; white-space: nowrap"><input v-model="onlyUnlinked" type="checkbox" style="width: auto" /> bare ukoblede</label>
  </div>
  <div v-for="r in filtered" :key="r.raterKey" class="card">
    <div class="row">
      <div>
        <strong>{{ r.raterKey }}</strong>
        <div class="muted">{{ r.years.join(', ') }} · {{ r.ratingCount }} terningkast</div>
      </div>
      <span v-if="r.linkedByEmail" class="pill on">raters.csv · {{ r.loginEmail }}</span>
      <span v-else-if="r.linkedTo" class="pill on">{{ r.linkedTo.displayName }}</span>
    </div>
    <div v-if="r.linkedByEmail" class="muted" style="margin-top: 6px">Koblet gjennom raters.csv. Endre fila for å endre dette.</div>
    <div v-else-if="r.linkedTo" class="row" style="margin-top: 8px; justify-content: flex-start">
      <span class="muted">{{ r.linkedTo.email }}</span>
      <button class="secondary" :disabled="busy === r.raterKey" @click="unlink(r)">Koble fra</button>
    </div>
    <div v-else class="row" style="margin-top: 8px">
      <select v-model="choice[r.raterKey]">
        <option value="" disabled>Velg innlogging</option>
        <option v-for="l in logins" :key="l.id" :value="l.id">{{ l.displayName }} · {{ l.email }}</option>
      </select>
      <button :disabled="!choice[r.raterKey] || busy === r.raterKey" @click="link(r)">Koble</button>
    </div>
  </div>
  <p v-if="!filtered.length" class="muted">Ingen treff.</p>
</template>
