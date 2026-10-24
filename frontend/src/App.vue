<script setup lang="ts">
import { onMounted } from 'vue'
import { useMe } from './stores/me'

const store = useMe()
onMounted(() => store.refresh().catch(() => {}))
</script>

<template>
  <main>
    <router-view />
  </main>
  <nav class="tabs">
    <router-link to="/">Hjem</router-link>
    <router-link to="/beers">Øl</router-link>
    <router-link to="/session">Smaking</router-link>
    <router-link to="/stats">Resultater</router-link>
    <router-link to="/me">Meg</router-link>
  </nav>
</template>
