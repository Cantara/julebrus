<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiFailure, del, get, post } from '../api/client'
import type { ClaimView, RaterView } from '../api/types'
import { t } from '../i18n'

const emit = defineEmits<{ changed: [] }>()

const raters = ref<RaterView[]>([])
const claims = ref<ClaimView[]>([])
const showAll = ref(false)
const error = ref<string | null>(null)

async function load() {
  ;[raters.value, claims.value] = await Promise.all([
    get<RaterView[]>('/api/history/raters'),
    get<ClaimView[]>('/api/history/claims/me'),
  ])
  emit('changed')
}
onMounted(() => load().catch(() => {}))

const suggested = computed(() => raters.value.filter((r) => r.suggested))
const others = computed(() => raters.value.filter((r) => !r.suggested))

async function claim(name: string) {
  error.value = null
  try {
    await post('/api/history/claims', { raterName: name })
  } catch (e) {
    const code = e instanceof ApiFailure ? e.code : ''
    error.value = code === 'CLAIM_LIMIT' ? t('claimLimit') : code === 'CLAIM_EXISTS' ? t('claimExists')
      : code === 'RATER_TAKEN' ? t('claimTakenErr') : t('claimError')
  }
  await load().catch(() => {})
}

async function withdraw(id: string) {
  error.value = null
  await del(`/api/history/claims/${id}`).catch(() => (error.value = t('claimError')))
  await load().catch(() => {})
}

const describe = (r: RaterView) => `${r.years.join(', ')} · ${r.ratingCount} ${t('claimRatings')}`
</script>

<template>
  <h2>{{ t('claimTitle') }}</h2>
  <p class="muted">{{ t('claimIntro') }}</p>
  <p v-if="error" class="error">{{ error }}</p>

  <div v-if="claims.length" class="card">
    <strong>{{ t('claimMine') }}</strong>
    <div v-for="c in claims" :key="c.id" class="row" style="padding: 6px 0">
      <span>{{ c.raterName }} <span class="pill" :class="{ on: c.status === 'APPROVED' }">{{ t(c.status) }}</span></span>
      <button v-if="c.status === 'PENDING'" class="secondary" @click="withdraw(c.id)">{{ t('claimWithdraw') }}</button>
    </div>
  </div>

  <div class="card">
    <strong>{{ t('claimSuggested') }}</strong>
    <p v-if="!suggested.length" class="muted">{{ t('claimNone') }}</p>
    <div v-for="r in suggested" :key="r.raterName" class="row" style="padding: 6px 0">
      <span>{{ r.raterName }}<div class="muted">{{ describe(r) }}</div></span>
      <span v-if="r.myClaim" class="pill">{{ t(r.myClaim.status) }}</span>
      <span v-else-if="!r.available" class="muted">{{ t('claimTaken') }}</span>
      <button v-else @click="claim(r.raterName)">{{ t('claimButton') }}</button>
    </div>
  </div>

  <button class="secondary" @click="showAll = !showAll">{{ showAll ? t('claimHideAll') : t('claimShowAll') }}</button>
  <div v-if="showAll" class="card" style="margin-top: 12px">
    <strong>{{ t('claimOthers') }}</strong>
    <div v-for="r in others" :key="r.raterName" class="row" style="padding: 6px 0">
      <span>{{ r.raterName }}<div class="muted">{{ describe(r) }}</div></span>
      <span v-if="r.myClaim" class="pill">{{ t(r.myClaim.status) }}</span>
      <span v-else-if="!r.available" class="muted">{{ t('claimTaken') }}</span>
      <button v-else class="secondary" @click="claim(r.raterName)">{{ t('claimButton') }}</button>
    </div>
  </div>
</template>
