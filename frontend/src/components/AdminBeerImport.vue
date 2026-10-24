<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiFailure, postText } from '../api/client'
import type { ImportResult } from '../api/types'

/**
 * Paste or pick a CSV, preview what is new, duplicate or broken, then import the
 * new rows as approved beers. Made for the Vinmonopolet drop on 4 November.
 */
const text = ref('')
const preview = ref<ImportResult | null>(null)
const done = ref<ImportResult | null>(null)
const error = ref('')
const busy = ref(false)

const newCount = computed(() => preview.value?.rows.filter((r) => r.action === 'NEW').length ?? 0)

async function onFile(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (!f) return
  text.value = await f.text()
  preview.value = null
  done.value = null
}

async function run(dryRun: boolean) {
  error.value = ''
  busy.value = true
  try {
    const result = await postText<ImportResult>(`/api/admin/beers/import?dryRun=${dryRun}`, text.value)
    if (dryRun) preview.value = result
    else {
      done.value = result
      preview.value = null
    }
  } catch (e) {
    error.value = e instanceof ApiFailure ? `${e.message} (${e.code})` : 'Noe gikk galt.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <h2>Admin: importer øl fra CSV</h2>
  <div class="card">
    <p class="muted" style="margin-top: 0">
      Kolonner: <code>name, brewery, style, abv, vinmonopolet_id</code>. Komma eller semikolon, desimalkomma går fint.
      Nye rader blir godkjente øl; de som finnes fra før (samme Polet-nummer eller navn) hoppes over.
    </p>
    <input type="file" accept=".csv,text/csv,text/plain" @change="onFile" />
    <label>Eller lim inn</label>
    <textarea v-model="text" rows="6" placeholder="name;brewery;style;abv;vinmonopolet_id&#10;Nøgne Ø God Jul;Nøgne Ø;Juleøl, sterk;8,5;1234501" @input="preview = null; done = null" />
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="row" style="margin-top: 10px; justify-content: flex-start">
      <button class="secondary" :disabled="!text.trim() || busy" @click="run(true)">Forhåndsvis</button>
      <button :disabled="!preview || !newCount || busy" @click="run(false)">Importer {{ newCount }} nye</button>
    </div>
    <p v-if="done" class="ok" role="status">Importerte {{ done.created }} øl.</p>

    <table v-if="preview" style="margin-top: 10px">
      <thead><tr><th>#</th><th>Øl</th><th>Status</th></tr></thead>
      <tbody>
        <tr v-for="r in preview.rows" :key="r.line">
          <td>{{ r.line }}</td>
          <td>
            {{ r.name || '(uten navn)' }}
            <div class="muted">{{ [r.brewery, r.style, r.abv != null ? r.abv + ' %' : null, r.vinmonopoletId ? 'Polet ' + r.vinmonopoletId : null].filter(Boolean).join(' · ') }}</div>
          </td>
          <td>
            <span class="pill" :class="{ on: r.action === 'NEW' }">{{ r.action === 'NEW' ? 'ny' : r.action === 'DUPLICATE' ? 'finnes' : 'feil' }}</span>
            <div v-if="r.reason" class="muted">{{ r.reason }}</div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
