<script setup lang="ts">
import { useConfig } from '../stores/config'
import { t } from '../i18n'

const store = useConfig()
</script>

<template>
  <p v-if="store.config.sponsor" class="sponsor muted">
    {{ t('sponsoredBy') }}
    <a v-if="store.config.sponsor.url" :href="store.config.sponsor.url" target="_blank" rel="noopener">{{ store.config.sponsor.name }}</a>
    <template v-else>{{ store.config.sponsor.name }}</template>
  </p>
</template>

<style scoped>
.sponsor { text-align: center; margin: 24px 0 0; }
</style>
