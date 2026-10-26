<script setup lang="ts">
/** The organiser's tools, out of the profile: one section at a time so the page stays short. */
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminBeerImport from '../components/AdminBeerImport.vue'
import AdminBeers from '../components/AdminBeers.vue'
import AdminClaims from '../components/AdminClaims.vue'
import AdminMatches from '../components/AdminMatches.vue'
import AdminPeople from '../components/AdminPeople.vue'
import AdminPhotos from '../components/AdminPhotos.vue'
import AdminRaterLinks from '../components/AdminRaterLinks.vue'
import AdminTeams from '../components/AdminTeams.vue'

const SECTIONS = [
  ['beers', 'Øl'], ['photos', 'Bilder'], ['import', 'Import'], ['teams', 'Lag'], ['claims', 'Krav'], ['raters', 'Koblinger'], ['people', 'Folk'], ['matches', 'Treff'],
] as const
type Section = (typeof SECTIONS)[number][0]

const route = useRoute()
const router = useRouter()
const section = ref<Section>((SECTIONS.find(([k]) => k === route.query.s)?.[0] ?? 'beers') as Section)
watch(section, (s) => router.replace({ query: { s } }))
</script>

<template>
  <h1>Admin</h1>
  <p class="muted"><router-link to="/me">‹ Tilbake til profilen</router-link></p>
  <div class="sections" role="tablist">
    <button v-for="[key, label] in SECTIONS" :key="key" class="pill" :class="{ on: section === key }" role="tab" :aria-selected="section === key" @click="section = key">{{ label }}</button>
  </div>
  <AdminBeers v-if="section === 'beers'" />
  <AdminPhotos v-else-if="section === 'photos'" />
  <AdminBeerImport v-else-if="section === 'import'" />
  <AdminTeams v-else-if="section === 'teams'" />
  <AdminClaims v-else-if="section === 'claims'" />
  <AdminRaterLinks v-else-if="section === 'raters'" />
  <AdminPeople v-else-if="section === 'people'" />
  <AdminMatches v-else-if="section === 'matches'" />
</template>

<style scoped>
.sections { display: flex; gap: 6px; flex-wrap: wrap; margin: 0 0 12px; }
.sections .pill { border: 1px solid var(--line); cursor: pointer; font: inherit; font-size: 0.85rem; }
</style>
