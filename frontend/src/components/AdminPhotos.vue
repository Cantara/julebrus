<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiFailure, get, post } from '../api/client'
import type { AdminPhoto } from '../api/types'
import { t } from '../i18n'

const pending = ref<AdminPhoto[]>([])
const error = ref('')
// One confirm step at a time: "<photo id>:<APPROVE|REJECT>".
const confirming = ref<string | null>(null)

async function load() {
  pending.value = await get<AdminPhoto[]>('/api/admin/photos?status=PENDING')
}
onMounted(() => load().catch(() => (error.value = t('adminError'))))

async function decide(p: AdminPhoto, decision: 'APPROVE' | 'REJECT') {
  error.value = ''
  confirming.value = null
  try {
    await post(`/api/admin/photos/${p.id}/decision`, { decision })
  } catch (e) {
    error.value = e instanceof ApiFailure && e.status === 409 ? t('adminPhotoConflict') : `${t('adminError')}${e instanceof ApiFailure ? ` (${e.code})` : ''}`
  }
  await load().catch(() => {})
}
</script>

<template>
  <h2>{{ t('adminPhotosTitle') }}</h2>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p v-if="!pending.length" class="muted">{{ t('adminPhotosNone') }}</p>
  <div v-for="p in pending" :key="p.id" class="card">
    <strong>{{ p.beerName }}</strong>
    <div class="muted">{{ t('adminPhotoBy') }} {{ p.uploaderName }} · {{ p.uploaderEmail }} · {{ new Date(p.createdAt).toLocaleString() }}</div>
    <img :src="`/api/beers/photos/${p.id}/image`" loading="lazy" :alt="`${t('photoAlt')} ${p.beerName}`"
         style="display: block; max-width: 100%; max-height: 260px; border-radius: 8px; margin: 8px 0" />
    <div v-if="confirming === p.id + ':APPROVE' || confirming === p.id + ':REJECT'" class="row" style="justify-content: flex-start">
      <span>{{ confirming.endsWith('APPROVE') ? t('adminPhotoConfirmApprove') : t('adminPhotoConfirmReject') }}</span>
      <button @click="decide(p, confirming.endsWith('APPROVE') ? 'APPROVE' : 'REJECT')">{{ t('confirmYes') }}</button>
      <button class="secondary" @click="confirming = null">{{ t('cancel') }}</button>
    </div>
    <div v-else class="row" style="justify-content: flex-start">
      <button @click="confirming = p.id + ':APPROVE'">{{ t('approve') }}</button>
      <button class="secondary" @click="confirming = p.id + ':REJECT'">{{ t('reject') }}</button>
    </div>
  </div>
</template>
