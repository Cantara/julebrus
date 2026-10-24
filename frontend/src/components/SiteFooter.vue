<script setup lang="ts">
import SponsorLine from './SponsorLine.vue'
import { t } from '../i18n'
</script>

<template>
  <footer>
    <SponsorLine />
    <p class="muted" style="text-align: center; margin: 4px 0 0">
      <router-link to="/privacy">{{ t('privacyLink') }}</router-link>
    </p>
  </footer>
</template>
