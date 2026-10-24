<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get, post } from '../api/client'
import type { ConsentView } from '../api/types'
import ConsentText from '../components/ConsentText.vue'
import { lang, setLang, t } from '../i18n'
import { useMe } from '../stores/me'

const router = useRouter()
const store = useMe()
const consent = ref<ConsentView | null>(null)
const busy = ref(false)
const failed = ref(false)

onMounted(async () => {
  consent.value = await get<ConsentView>('/api/consent')
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
    <span>
      <button class="secondary" :class="{ on: lang === 'nb' }" :disabled="lang === 'nb'" @click="setLang('nb')">NB</button>
      <button class="secondary" :class="{ on: lang === 'en' }" :disabled="lang === 'en'" @click="setLang('en')">EN</button>
    </span>
  </div>

  <div v-if="consent" class="card">
    <ConsentText :text="lang === 'en' ? consent.texts.en : consent.texts.nb" />
    <p class="muted">{{ t('consentVersion') }} {{ consent.version }}</p>
    <p v-if="failed" class="error">{{ t('consentFailed') }}</p>
    <p v-if="consent.accepted" class="ok">{{ t('consentAlready') }}</p>
    <button v-if="!consent.accepted" :disabled="busy" @click="accept">{{ t('consentAccept') }}</button>
    <router-link v-else class="btn" to="/">{{ t('consentContinue') }}</router-link>
  </div>
</template>
