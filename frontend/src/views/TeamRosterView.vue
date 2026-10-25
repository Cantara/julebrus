<script setup lang="ts">
/** Who plays for a team: names and rolls this season, most active first. */
import { onMounted, ref, watch } from 'vue'
import { get } from '../api/client'
import type { TeamRoster } from '../api/types'

const props = defineProps<{ slug: string }>()
const roster = ref<TeamRoster | null>(null)
const error = ref('')

async function load() {
  error.value = ''
  try {
    roster.value = await get<TeamRoster>(`/api/teams/${encodeURIComponent(props.slug)}`)
  } catch {
    error.value = 'Fant ikke laget.'
  }
}
onMounted(load)
watch(() => props.slug, load)
</script>

<template>
  <p class="muted"><router-link to="/me">‹ Meg</router-link></p>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <template v-if="roster">
    <h1>Lag {{ roster.name }}</h1>
    <p class="muted">
      {{ roster.members.length }} {{ roster.members.length === 1 ? 'smaker' : 'smakere' }} · {{ roster.tastings }} terningkast i år
      <template v-if="roster.sponsorName"> · støttet av <a v-if="roster.sponsorUrl" :href="roster.sponsorUrl" target="_blank" rel="noopener">{{ roster.sponsorName }}</a><template v-else>{{ roster.sponsorName }}</template></template>
    </p>
    <div class="card">
      <ol class="mates">
        <li v-for="m in roster.members" :key="m.userId" class="row" :class="{ me: m.me }">
          <span><strong>{{ m.displayName }}</strong><span v-if="m.me" class="pill on" style="margin-left: 6px">deg</span></span>
          <span class="muted">{{ m.tastings }} {{ m.tastings === 1 ? 'terningkast' : 'terningkast' }}</span>
        </li>
      </ol>
      <p v-if="!roster.members.length" class="muted">Ingen har valgt laget ennå.</p>
    </div>
    <div class="row" style="margin-top: 12px">
      <router-link class="btn secondary" style="flex: 1; text-align: center" :to="`/stats?team=${roster.slug}`">Lagets liste</router-link>
      <router-link v-if="!roster.mine" class="btn" style="flex: 1; text-align: center" to="/me">Bli med</router-link>
    </div>
  </template>
</template>

<style scoped>
.mates { list-style: none; margin: 0; padding: 0; }
.mates li { padding: 8px 0; border-bottom: 1px solid var(--line); }
.mates li:last-child { border-bottom: none; }
</style>
