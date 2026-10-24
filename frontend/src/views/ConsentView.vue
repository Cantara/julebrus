<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get, logout, post } from '../api/client'
import type { ConsentView } from '../api/types'
import ConsentText from '../components/ConsentText.vue'
import LangToggle from '../components/LangToggle.vue'
import { lang, t } from '../i18n'
import { useMe } from '../stores/me'

const router = useRouter()
const store = useMe()
const consent = ref<ConsentView | null>(null)
const busy = ref(false)
const failed = ref(false)
const loadFailed = ref(false)

onMounted(async () => {
  try {
    consent.value = await get<ConsentView>('/api/consent')
  } catch {
    loadFailed.value = true
  }
})

async function accept() {
  if (!consent.value) return
  busy.value = true
  failed.value = false
  try {
    // Send the version that was shown, so a text that changed meanwhile is refused (409).
    consent.value = await post<ConsentView>('/api/consent/accept', { version: consent.value.version })
    await store.refresh()
    router.replace('/')
  } catch {
    failed.value = true
    consent.value = await get<ConsentView>('/api/consent').catch(() => consent.value)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="row">
    <h1>{{ t('consentTitle') }}</h1>
    <LangToggle />
  </div>

  <p v-if="loadFailed" class="error" role="alert">{{ t('loadFailed') }}</p>
  <div v-if="consent" class="card">
    <div :lang="lang"><ConsentText :text="lang === 'en' ? consent.texts.en : consent.texts.nb" /></div>
    <p class="muted">{{ t('consentVersion') }} {{ consent.version }} · <router-link to="/privacy">{{ t('privacyLink') }}</router-link></p>
    <p v-if="failed" class="error" role="alert">{{ t('consentFailed') }}</p>
    <p v-if="consent.accepted" class="ok" role="status">{{ t('consentAlready') }}</p>
    <div class="row" style="justify-content: flex-start">
      <button v-if="!consent.accepted" :disabled="busy" @click="accept">{{ t('consentAccept') }}</button>
      <router-link v-else class="btn" to="/">{{ t('consentContinue') }}</router-link>
      <!-- Logout is a security-chain endpoint, so it works while consent is still missing. -->
      <a v-if="!consent.accepted" href="#" @click.prevent="logout()">{{ t('decline') }}</a>
    </div>
  </div>
</template>
