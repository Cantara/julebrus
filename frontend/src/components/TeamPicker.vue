<script setup lang="ts">
/**
 * Which teams do you play for? Multi-select over the enabled teams, a card for asking
 * about another team, and "later". Used on the signup page and on the profile.
 */
import { computed, onMounted, ref } from 'vue'
import { ApiFailure, get, put } from '../api/client'
import type { Team } from '../api/types'
import { useConfig } from '../stores/config'
import { useMe } from '../stores/me'

const props = defineProps<{ allowLater?: boolean }>()
const emit = defineEmits<{ done: [] }>()
const store = useMe()
const config = useConfig()

const teams = ref<Team[]>([])
const picked = ref<Set<string>>(new Set())
const error = ref('')
const busy = ref(false)

onMounted(async () => {
  await config.load().catch(() => {})
  try {
    teams.value = await get<Team[]>('/api/teams')
    // The current choice, or on a first visit the e-mail domain's suggestion.
    const mine = teams.value.filter((t) => t.mine).map((t) => t.id)
    const suggested = teams.value.filter((t) => t.suggested).map((t) => t.id)
    picked.value = new Set(store.me?.teamsChosen ? mine : suggested)
  } catch {
    error.value = 'Kunne ikke hente lagene.'
  }
})

function toggle(id: string) {
  const next = new Set(picked.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  picked.value = next
}

const contactHref = computed(() => {
  const to = config.config.contactEmail ?? ''
  const subject = encodeURIComponent('Nytt lag i juleølsmakingen')
  const body = encodeURIComponent('Hei! Vi vil gjerne ha et eget lag i juleølsmakingen. Lagnavn: \n\nHilsen ')
  return `mailto:${to}?subject=${subject}&body=${body}`
})

async function save(ids: string[]) {
  busy.value = true
  error.value = ''
  try {
    await put('/api/me/teams', { teamIds: ids })
    await store.refresh()
    emit('done')
  } catch (e) {
    error.value = e instanceof ApiFailure ? `Kunne ikke lagre (${e.code}).` : 'Kunne ikke lagre.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <div class="teams">
    <button v-for="t in teams" :key="t.id" type="button" class="team" :class="{ on: picked.has(t.id) }" :aria-pressed="picked.has(t.id)" @click="toggle(t.id)">
      <span class="mark" aria-hidden="true">{{ picked.has(t.id) ? '✓' : '' }}</span>
      <span class="body">
        <strong class="display">{{ t.name }}</strong>
        <span class="muted">{{ t.members }} {{ t.members === 1 ? 'smaker' : 'smakere' }}<template v-if="t.suggested && !store.me?.teamsChosen"> · foreslått fra e-posten din</template></span>
        <span v-if="t.sponsorName" class="muted">Støttet av {{ t.sponsorName }}</span>
      </span>
    </button>
    <a class="team ask" :href="contactHref">
      <span class="mark" aria-hidden="true">?</span>
      <span class="body">
        <strong class="display">Et annet lag?</strong>
        <span class="muted">Ta kontakt, så skrur vi på laget ditt mot et bidrag til smakingen.</span>
      </span>
    </a>
  </div>
  <p class="muted" style="margin: 10px 0">Lagene dine vises ved navnet ditt rundt bordet og i resultatene. Du kan være med i flere, og endre det på profilen din.</p>
  <div class="row">
    <button v-if="allowLater" class="secondary" :disabled="busy" @click="save([])">Velg senere</button>
    <span v-else />
    <button :disabled="busy" @click="save([...picked])">{{ picked.size ? 'Lagre' : 'Ingen lag' }}</button>
  </div>
</template>

<style scoped>
.teams { display: grid; gap: 10px; }
.team { display: flex; align-items: center; gap: 12px; text-align: left; background: var(--card); color: var(--ink); border: 1px solid var(--line); border-radius: 14px; padding: 12px 14px; cursor: pointer; text-decoration: none; }
.team.on { border-color: var(--brand); box-shadow: inset 0 0 0 1px var(--brand); }
.team .mark { flex: 0 0 28px; height: 28px; border-radius: 50%; border: 1px solid var(--line); display: grid; place-items: center; font-weight: bold; color: #fff; }
.team.on .mark { background: var(--brand); border-color: var(--brand); }
.team.ask .mark { color: var(--muted); }
.team .body { display: grid; gap: 2px; }
.team .body .muted { font-size: 0.85rem; }
</style>
