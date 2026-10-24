<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get } from '../api/client'
import ConsentText from '../components/ConsentText.vue'
import LangToggle from '../components/LangToggle.vue'
import { lang, t } from '../i18n'
import { useConfig } from '../stores/config'

// Public page (the privacy policy URL for the Google OAuth consent screen). It needs no
// login and no consent, and loads only /api/privacy and /api/config, both public.
interface Privacy {
  version: string
  texts: { nb: string; en: string }
}

const config = useConfig()
const privacy = ref<Privacy | null>(null)
const failed = ref(false)

onMounted(async () => {
  try {
    privacy.value = await get<Privacy>('/api/privacy')
  } catch {
    failed.value = true
  }
})
</script>

<template>
  <div class="row">
    <h1>{{ t('privacyLink') }}</h1>
    <LangToggle />
  </div>
  <p v-if="failed" class="error" role="alert">{{ t('loadFailed') }}</p>
  <div v-if="privacy" class="card" :lang="lang">
    <ConsentText :text="lang === 'en' ? privacy.texts.en : privacy.texts.nb" />
    <p class="muted">{{ t('consentVersion') }} {{ privacy.version }}</p>
  </div>
  <p v-if="config.config.contactEmail" class="muted">
    <a :href="'mailto:' + config.config.contactEmail">{{ config.config.contactEmail }}</a>
  </p>
  <p class="muted"><router-link to="/">{{ t('privacyBack') }}</router-link></p>
</template>
