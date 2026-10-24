<script setup lang="ts">
/** The "Varsler" card on the profile: one switch for the daily nudge and the finale announcement. */
import { onMounted, ref } from 'vue'
import { get } from '../api/client'
import { disable, enable, needsHomeScreen, sendTest, state, type PushState } from '../api/push'
import type { PushConfig } from '../api/types'

const config = ref<PushConfig | null>(null)
const current = ref<PushState>('off')
const busy = ref(false)
const note = ref('')

async function refresh() {
  if (!config.value) return
  current.value = (await state(config.value)).state
}

onMounted(async () => {
  config.value = await get<PushConfig>('/api/push/config').catch(() => null)
  await refresh().catch(() => {})
})

async function run(fn: () => Promise<unknown>, done: string) {
  busy.value = true
  note.value = ''
  try {
    await fn()
    note.value = done
  } catch (e) {
    note.value = (e as Error).message === 'denied' ? 'Nettleseren sa nei til varsler. Du kan endre det i innstillingene.' : 'Det gikk ikke. Prøv igjen.'
  } finally {
    busy.value = false
    await refresh().catch(() => {})
  }
}

const nudgeTime = () => (config.value?.nudgeTime ?? '17:00').slice(0, 5)
</script>

<template>
  <div v-if="config?.enabled" class="card">
    <div class="row">
      <div>
        <strong>Varsler</strong>
        <div class="muted">Et dytt klokka {{ nudgeTime() }} de dagene luka står uåpnet, og ett når stjernen tennes.</div>
      </div>
      <button v-if="current === 'available'" :disabled="busy" @click="run(() => enable(config!), 'Varsler er på.')">Skru på</button>
      <button v-else-if="current === 'on'" class="secondary" :disabled="busy" @click="run(disable, 'Varsler er av.')">Skru av</button>
    </div>
    <p v-if="current === 'unsupported'" class="muted" style="margin: 8px 0 0">
      {{ needsHomeScreen() ? 'På iPhone: legg appen på hjemskjermen først (Del → Legg til på Hjem-skjerm), så kan du skru på varsler her.' : 'Nettleseren din støtter ikke varsler.' }}
    </p>
    <p v-else-if="current === 'denied'" class="muted" style="margin: 8px 0 0">Nettleseren har blokkert varsler for denne siden. Endre det i nettleserens innstillinger.</p>
    <p v-if="current === 'on'" class="muted" style="margin: 8px 0 0">
      <a href="#" @click.prevent="run(sendTest, 'Sendt. Sjekk varslene dine.')">Send et testvarsel</a>
    </p>
    <p v-if="note" class="muted" style="margin: 8px 0 0">{{ note }}</p>
  </div>
</template>
