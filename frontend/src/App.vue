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
    <router-link to="/" exact-active-class="router-link-active" active-class="">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z" /></svg>Hjem
    </router-link>
    <router-link to="/beers">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 4h9v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2zM15 9h2.5A1.5 1.5 0 0 1 19 10.5v4a1.5 1.5 0 0 1-1.5 1.5H15M6 8h9" /></svg>Øl
    </router-link>
    <router-link to="/session" :class="{ waiting: (store.me?.openTable?.pendingForMe ?? 0) > 0 }">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2.5v2.5H14zM17.5 17.5H20V20h-2.5zM14 20h2.5M20 14v2.5" /></svg>Smaking
      <i v-if="(store.me?.openTable?.pendingForMe ?? 0) > 0" class="dot" aria-label="Et øl venter på terningkastet ditt" />
    </router-link>
    <router-link to="/stats">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 20h18M9 20v-8h6v8M3 20v-5h6M15 20v-4h6M12 3l1.1 2.3 2.5.4-1.8 1.7.4 2.5L12 8.7l-2.2 1.2.4-2.5-1.8-1.7 2.5-.4z" /></svg>Resultater
    </router-link>
    <router-link to="/me" :class="{ waiting: (store.me?.chores?.total ?? 0) > 0 }">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 20a8 8 0 0 1 16 0" /></svg>Meg
      <i v-if="(store.me?.chores?.total ?? 0) > 0" class="dot" :aria-label="`${store.me?.chores?.total} arrangørsysler venter`" />
    </router-link>
  </nav>
</template>
