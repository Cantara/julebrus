<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiFailure, get, post } from '../api/client'
import type { AdminClaimView } from '../api/types'
import { t } from '../i18n'

const pending = ref<AdminClaimView[]>([])
const approved = ref<AdminClaimView[]>([])
const error = ref<string | null>(null)
// One confirm step at a time: "<claim id>:<action>".
const confirming = ref<string | null>(null)

async function load() {
  ;[pending.value, approved.value] = await Promise.all([
    get<AdminClaimView[]>('/api/admin/history/claims?status=PENDING'),
    get<AdminClaimView[]>('/api/admin/history/claims?status=APPROVED'),
  ])
}
onMounted(() => load().catch(() => (error.value = t('adminError'))))

// The server lists pending claims grouped by key; show everyone who asks for the same person together.
const groups = computed(() => {
  const m = new Map<string, AdminClaimView[]>()
  for (const c of pending.value) m.set(c.raterKey, [...(m.get(c.raterKey) ?? []), c])
  return [...m.entries()]
})

async function act(c: AdminClaimView, action: 'APPROVE' | 'REJECT' | 'REVOKE') {
  error.value = null
  confirming.value = null
  try {
    if (action === 'REVOKE') await post(`/api/admin/history/claims/${c.id}/revoke`)
    else await post(`/api/admin/history/claims/${c.id}/decision`, { decision: action })
  } catch (e) {
    // 409: the name was taken or the claim decided meanwhile. Say so, then show the truth.
    error.value = e instanceof ApiFailure && e.status === 409 ? t('adminConflict') : `${t('adminError')}${e instanceof ApiFailure ? ` (${e.code})` : ''}`
  }
  await load().catch(() => {})
}
</script>

<template>
  <h2>{{ t('adminTitle') }}</h2>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="!pending.length" class="muted">{{ t('adminNone') }}</p>
  <div v-for="[key, claimants] in groups" :key="key" class="card">
    <strong>{{ key }}</strong>
    <div class="muted">{{ claimants[0].years.join(', ') }} · {{ claimants[0].ratingCount }} {{ t('claimRatings') }}</div>
    <p v-if="claimants.length > 1" class="muted" role="note">{{ t('adminCompeting') }}</p>
    <div v-for="c in claimants" :key="c.id" style="padding: 6px 0; border-top: 1px solid var(--line)">
      <div>{{ c.displayName }} · {{ c.email }}</div>
      <p v-if="c.linkedByEmail" class="error" role="note">{{ t('adminLinked') }}</p>
      <div v-if="confirming === c.id + ':APPROVE'" class="row" style="margin-top: 8px; justify-content: flex-start">
        <span>{{ t('confirmApprove') }}</span>
        <button @click="act(c, 'APPROVE')">{{ t('confirmYes') }}</button>
        <button class="secondary" @click="confirming = null">{{ t('cancel') }}</button>
      </div>
      <div v-else class="row" style="margin-top: 8px; justify-content: flex-start">
        <button :disabled="c.linkedByEmail" @click="confirming = c.id + ':APPROVE'">{{ t('approve') }}</button>
        <button class="secondary" @click="act(c, 'REJECT')">{{ t('reject') }}</button>
      </div>
    </div>
  </div>

  <h3 v-if="approved.length">{{ t('adminApproved') }}</h3>
  <div v-for="c in approved" :key="c.id" class="card">
    <strong>{{ c.raterKey }}</strong>
    <div class="muted">{{ c.displayName }} · {{ c.email }}</div>
    <div v-if="confirming === c.id + ':REVOKE'" class="row" style="margin-top: 8px; justify-content: flex-start">
      <span class="error">{{ t('confirmRevoke') }}</span>
      <button @click="act(c, 'REVOKE')">{{ t('confirmYes') }}</button>
      <button class="secondary" @click="confirming = null">{{ t('cancel') }}</button>
    </div>
    <button v-else class="secondary" style="margin-top: 8px" @click="confirming = c.id + ':REVOKE'">{{ t('revoke') }}</button>
  </div>
</template>
