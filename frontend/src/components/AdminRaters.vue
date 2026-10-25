<script setup lang="ts">
/**
 * Every person with consent, with this season's weight and why. The policy decides from
 * the seasons it can see, and the archive has holes, so an organiser who knows better
 * sets the weight by hand here; "Tilbakestill" hands it back to the policy.
 */
import { computed, onMounted, ref } from 'vue'
import { ApiFailure, get, post } from '../api/client'
import type { AdminRater } from '../api/types'

const raters = ref<AdminRater[]>([])
const error = ref('')
const q = ref('')
const onlyHalf = ref(false)
const busy = ref('')

async function load() {
  raters.value = await get<AdminRater[]>('/api/admin/raters')
}
async function run(email: string, fn: () => Promise<unknown>) {
  error.value = ''
  busy.value = email
  try {
    await fn()
    await load()
  } catch (e) {
    error.value = e instanceof ApiFailure ? e.message : (e as Error).message
  } finally {
    busy.value = ''
  }
}
function setWeight(r: AdminRater, weight: number) {
  const suggested = weight === 1 ? 'Kjent fra tidligere år' : weight === 0 ? 'Teller ikke i år' : 'Satt av arrangør'
  const reason = window.prompt(`Vekt ${fmt(weight)} for ${r.displayName}. Begrunnelse (vises for personen):`, suggested)
  if (reason === null || !reason.trim()) return
  run(r.email, () => post('/api/admin/raters/override', { email: r.email, weight, reason: reason.trim() }))
}
const reset = (r: AdminRater) => run(r.email, () => post('/api/admin/raters/reset', { email: r.email }))
function flag(r: AdminRater) {
  const note = r.flagged ? '' : window.prompt(`Flagg ${r.displayName} for juks i år. Notat:`, '')
  if (!r.flagged && note === null) return
  run(r.email, () => post('/api/admin/raters/flag', { email: r.email, flagged: !r.flagged, note: note ?? '' }))
}

const shown = computed(() => {
  const needle = q.value.trim().toLowerCase()
  return [...raters.value]
    .filter((r) => !onlyHalf.value || r.weight < 1)
    .filter((r) => !needle || r.displayName.toLowerCase().includes(needle) || r.email.toLowerCase().includes(needle))
    .sort((a, b) => a.weight - b.weight || a.displayName.localeCompare(b.displayName, 'nb'))
})
const halfCount = computed(() => raters.value.filter((r) => r.weight < 1).length)
const fmt = (w: number) => w.toFixed(1).replace('.', ',')
onMounted(load)
</script>

<template>
  <h2>Admin: smakere og vekt</h2>
  <p class="muted">
    Vekten avgjøres av siste sesong appen kjenner til. Arkivet har hull, så en som du vet har vært med får 1,0 her.
    Begrunnelsen vises for personen under Meg.
  </p>
  <div class="row" style="margin-bottom: 10px">
    <input v-model="q" placeholder="Søk på navn eller e-post" style="flex: 1" />
    <button class="pill" :class="{ on: onlyHalf }" style="border: 0; cursor: pointer; white-space: nowrap" @click="onlyHalf = !onlyHalf">Under 1,0 · {{ halfCount }}</button>
  </div>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <div v-for="r in shown" :key="r.email" class="card" :class="{ dim: busy === r.email }">
    <div style="min-width: 0">
      <strong>{{ r.displayName }}</strong>
      <span class="pill" :class="{ on: r.weight >= 1, zero: r.weight === 0 }" style="margin-left: 6px">{{ fmt(r.weight) }}</span>
      <span v-if="r.flagged" class="pill zero" title="Flagget for juks">⚑</span>
      <span v-if="r.overridden" class="pill" title="Satt av arrangør">håndsatt</span>
      <div class="muted" style="word-break: break-all">{{ r.email }} · {{ r.scores }} terningkast i år</div>
      <div class="muted">{{ r.reason }}<template v-if="r.flagged && r.flagNote"> · {{ r.flagNote }}</template></div>
    </div>
    <div class="actions">
      <button v-if="r.weight !== 1" class="secondary small" :disabled="busy === r.email" @click="setWeight(r, 1)">Sett 1,0</button>
      <button v-if="r.weight !== 0.5" class="secondary small" :disabled="busy === r.email" @click="setWeight(r, 0.5)">Sett 0,5</button>
      <button v-if="r.weight !== 0" class="secondary small" :disabled="busy === r.email" @click="setWeight(r, 0)">Sett 0</button>
      <button v-if="r.overridden" class="secondary small" :disabled="busy === r.email" @click="reset(r)">Tilbakestill</button>
      <button class="secondary small" :disabled="busy === r.email" @click="flag(r)">{{ r.flagged ? 'Fjern flagg' : 'Flagg' }}</button>
    </div>
  </div>
  <p v-if="!shown.length" class="muted">Ingen treff.</p>
</template>

<style scoped>
.actions { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
.small { padding: 6px 10px; font-size: 0.85rem; }
.pill.zero { background: var(--danger); color: #fff; }
.dim { opacity: 0.6; }
</style>
