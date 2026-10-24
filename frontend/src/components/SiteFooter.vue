<script setup lang="ts">
import SponsorLine from './SponsorLine.vue'
import { t } from '../i18n'
</script>

<template>
  <footer>
    <SponsorLine />
    <p class="muted" style="text-align: center; margin: 4px 0 0">
      <!-- A full page load: /privacy is a server-rendered page. -->
      <router-link to="/historien">{{ t('storyLink') }}</router-link> ·
      <a href="/privacy">{{ t('privacyLink') }}</a>
    </p>
  </footer>
</template>
