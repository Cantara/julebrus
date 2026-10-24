<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiFailure, del, get, post } from '../api/client'
import type { ClaimView, ClaimCandidate } from '../api/types'
import { t } from '../i18n'

const emit = defineEmits<{ changed: [] }>()

const raters = ref<ClaimCandidate[]>([])
const claims = ref<ClaimView[]>([])
const showAll = ref(false)
const error = ref<string | null>(null)

async function load(notify = false) {
  ;[raters.value, claims.value] = await Promise.all([
    get<ClaimCandidate[]>('/api/history/claim-candidates'),
    get<ClaimView[]>('/api/history/claims/me'),
  ])
  // Only after a claim or withdrawal: the page loaded /api/history/me itself on mount.
  if (notify) emit('changed')
}
onMounted(() => load().catch(() => (error.value = t('claimError'))))

const suggested = computed(() => raters.value.filter((r) => r.suggested))
const others = computed(() => raters.value.filter((r) => !r.suggested))

async function claim(name: string) {
  error.value = null
  try {
    await post('/api/history/claims', { raterKey: name })
  } catch (e) {
    const code = e instanceof ApiFailure ? e.code : ''
    error.value = code === 'RATER_LINKED' ? t('claimLinked') : code === 'CLAIM_LIMIT' ? t('claimLimit')
      : code === 'CLAIM_DAILY_LIMIT' ? t('claimDailyLimit') : code === 'CLAIM_REVOKED' ? t('claimRevoked') : code === 'CLAIM_EXISTS' ? t('claimExists')
      : code === 'RATER_TAKEN' ? t('claimTakenErr') : t('claimError')
  }
  await load(true).catch(() => {})
}

async function withdraw(id: string) {
  error.value = null
  await del(`/api/history/claims/${id}`).catch(() => (error.value = t('claimError')))
  await load(true).catch(() => {})
}

const describe = (r: ClaimCandidate) => `${r.years.join(', ')} · ${r.ratingCount} ${t('claimRatings')}`
const taken = (r: ClaimCandidate) => (r.linkedToYou ? t('claimLinkedToYou') : r.linkedByEmail ? t('claimLinked') : t('claimTaken'))
</script>

<template>
  <h2>{{ t('claimTitle') }}</h2>
  <p class="muted">{{ t('claimIntro') }}</p>
  <p v-if="error" class="error" role="alert">{{ error }}</p>

  <div v-if="claims.length" class="card">
    <strong>{{ t('claimMine') }}</strong>
    <div v-for="c in claims" :key="c.id" class="row" style="padding: 6px 0">
      <span>{{ c.raterKey }} <span class="pill" :class="{ on: c.status === 'APPROVED' }">{{ t(c.status) }}</span></span>
      <button v-if="c.status === 'PENDING'" class="secondary" @click="withdraw(c.id)">{{ t('claimWithdraw') }}</button>
    </div>
  </div>

  <div class="card">
    <strong>{{ t('claimSuggested') }}</strong>
    <p v-if="!suggested.length" class="muted">{{ t('claimNone') }}</p>
    <div v-for="r in suggested" :key="r.raterKey" class="row" style="padding: 6px 0">
      <span>{{ r.raterKey }}<div class="muted">{{ describe(r) }}</div><div v-if="r.myClaim?.status === 'REJECTED'" class="muted">{{ t('claimRejectedNote') }}</div></span>
      <span v-if="r.myClaim && r.myClaim.status !== 'REJECTED'" class="pill">{{ t(r.myClaim.status) }}</span>
      <span v-else-if="!r.available" class="muted">{{ taken(r) }}</span>
      <button v-else @click="claim(r.raterKey)">{{ t('claimButton') }}</button>
    </div>
  </div>

  <button class="secondary" @click="showAll = !showAll">{{ showAll ? t('claimHideAll') : t('claimShowAll') }}</button>
  <div v-if="showAll" class="card" style="margin-top: 12px">
    <strong>{{ t('claimOthers') }}</strong>
    <div v-for="r in others" :key="r.raterKey" class="row" style="padding: 6px 0">
      <span>{{ r.raterKey }}<div class="muted">{{ describe(r) }}</div><div v-if="r.myClaim?.status === 'REJECTED'" class="muted">{{ t('claimRejectedNote') }}</div></span>
      <span v-if="r.myClaim && r.myClaim.status !== 'REJECTED'" class="pill">{{ t(r.myClaim.status) }}</span>
      <span v-else-if="!r.available" class="muted">{{ taken(r) }}</span>
      <button v-else class="secondary" @click="claim(r.raterKey)">{{ t('claimButton') }}</button>
    </div>
  </div>
</template>
