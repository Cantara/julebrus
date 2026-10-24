<script setup lang="ts">
import { onMounted } from 'vue'
import SponsorLine from './components/SponsorLine.vue'
import { useConfig } from './stores/config'
import { useMe } from './stores/me'

const store = useMe()
const config = useConfig()
onMounted(() => {
  store.refresh().catch(() => {})
  config.load()
})
</script>

<template>
  <main>
    <router-view />
    <SponsorLine />
  </main>
  <nav v-if="!store.me?.consentRequired" class="tabs">
    <router-link to="/">Hjem</router-link>
    <router-link to="/beers">Øl</router-link>
    <router-link to="/session">Smaking</router-link>
    <router-link to="/stats">Resultater</router-link>
    <router-link to="/me">Meg</router-link>
  </nav>
</template>
