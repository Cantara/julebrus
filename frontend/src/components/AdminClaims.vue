<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get, post } from '../api/client'
import type { AdminClaimView } from '../api/types'
import { t } from '../i18n'

const pending = ref<AdminClaimView[]>([])

async function load() {
  pending.value = await get<AdminClaimView[]>('/api/admin/history/claims?status=PENDING')
}
onMounted(() => load().catch(() => {}))

async function decide(id: string, decision: 'APPROVE' | 'REJECT') {
  // A 409 (name taken meanwhile) just means the list was stale; reload shows the truth.
  await post(`/api/admin/history/claims/${id}/decision`, { decision }).catch(() => {})
  await load().catch(() => {})
}
</script>

<template>
  <h2>{{ t('adminTitle') }}</h2>
  <p v-if="!pending.length" class="muted">{{ t('adminNone') }}</p>
  <div v-for="c in pending" :key="c.id" class="card">
    <strong>{{ c.raterKey }}</strong>
    <div class="muted">{{ c.years.join(', ') }} · {{ c.ratingCount }} {{ t('claimRatings') }}</div>
    <div>{{ c.displayName }} · {{ c.email }}</div>
    <div class="row" style="margin-top: 8px; justify-content: flex-start">
      <button @click="decide(c.id, 'APPROVE')">{{ t('approve') }}</button>
      <button class="secondary" @click="decide(c.id, 'REJECT')">{{ t('reject') }}</button>
    </div>
  </div>
</template>
