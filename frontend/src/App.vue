<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import SiteFooter from './components/SiteFooter.vue'
import { useConfig } from './stores/config'
import { useMe } from './stores/me'

const store = useMe()
const config = useConfig()
const route = useRoute()
onMounted(() => {
  // A logged-out visitor on /privacy must not be sent to Google by the /api/me call.
  if (window.location.pathname !== '/privacy') store.refresh().catch(() => {})
  config.load()
})
</script>

<template>
  <main>
    <router-view />
    <SiteFooter />
  </main>
  <nav v-if="!store.me?.consentRequired && route.path !== '/privacy'" class="tabs">
    <router-link to="/">Hjem</router-link>
    <router-link to="/beers">Øl</router-link>
    <router-link to="/session">Smaking</router-link>
    <router-link to="/stats">Resultater</router-link>
    <router-link to="/me">Meg</router-link>
  </nav>
</template>
