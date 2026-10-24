<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiFailure, del, upload } from '../api/client'
import type { Beer } from '../api/types'
import { t, type Key } from '../i18n'

const props = defineProps<{ beer: Beer }>()
const emit = defineEmits<{ (e: 'changed'): void }>()

const input = ref<HTMLInputElement | null>(null)
const progress = ref<number | null>(null)
const message = ref('')
const failed = ref(false)
// Bumped after a change so the browser fetches the cover again instead of showing its cached copy.
const bust = ref(0)
const busy = computed(() => progress.value !== null)

const MAX_SIDE = 1600

/**
 * Shrinks a big camera picture before it is sent (a 12 MP phone photo is several MB). The server does its own
 * scaling and never trusts this; if the browser cannot do it the original file is sent as is.
 */
async function shrink(file: File): Promise<{ blob: Blob; name: string }> {
  try {
    if (file.type !== 'image/jpeg' && file.type !== 'image/png') return { blob: file, name: file.name }
    const bmp = await createImageBitmap(file) // follows the EXIF orientation in current browsers
    const scale = Math.min(1, MAX_SIDE / Math.max(bmp.width, bmp.height))
    if (scale === 1 && file.size < 1_500_000) {
      bmp.close()
      return { blob: file, name: file.name }
    }
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bmp.width * scale)
    canvas.height = Math.round(bmp.height * scale)
    const ctx = canvas.getContext('2d')
    if (!ctx) return { blob: file, name: file.name }
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(bmp, 0, 0, canvas.width, canvas.height)
    bmp.close()
    const blob = await new Promise<Blob | null>((res) => canvas.toBlob(res, 'image/jpeg', 0.9))
    return blob ? { blob, name: 'label.jpg' } : { blob: file, name: file.name }
  } catch {
    return { blob: file, name: file.name }
  }
}

async function chosen(e: Event) {
  const el = e.target as HTMLInputElement
  const file = el.files?.[0]
  el.value = ''
  if (!file) return
  message.value = ''
  failed.value = false
  progress.value = 0
  try {
    const { blob, name } = await shrink(file)
    await upload(`/api/beers/${props.beer.id}/photos`, blob, name, (f) => (progress.value = f))
    message.value = t('photoDone')
    bust.value++
    emit('changed')
  } catch (err) {
    failed.value = true
    message.value = err instanceof ApiFailure && err.code in codeKeys ? t(codeKeys[err.code]) : t('photoError')
  } finally {
    progress.value = null
  }
}

const codeKeys: Record<string, Key> = {
  PHOTO_FORMAT: 'PHOTO_FORMAT',
  PHOTO_TOO_LARGE: 'PHOTO_TOO_LARGE',
  PHOTO_DIMENSIONS: 'PHOTO_DIMENSIONS',
  PHOTO_UNREADABLE: 'PHOTO_UNREADABLE',
  PHOTO_MISSING: 'PHOTO_MISSING',
  PHOTO_DAILY_LIMIT: 'PHOTO_DAILY_LIMIT',
  PHOTO_SEASON_LIMIT: 'PHOTO_SEASON_LIMIT',
  PHOTO_PENDING_EXISTS: 'PHOTO_PENDING_EXISTS',
  PHOTO_BUSY: 'PHOTO_BUSY',
  PHOTO_IN_PROGRESS: 'PHOTO_IN_PROGRESS',
  BEER_REJECTED: 'BEER_REJECTED',
}

async function remove() {
  if (!props.beer.myPhotoId) return
  failed.value = false
  try {
    await del(`/api/beers/photos/${props.beer.myPhotoId}`)
    message.value = t('photoDeleted')
    bust.value++
    emit('changed')
  } catch {
    failed.value = true
    message.value = t('photoError')
  }
}
</script>

<template>
  <div class="photo-box">
    <img
      v-if="beer.hasPhoto"
      class="photo-cover"
      :src="`/api/beers/${beer.id}/photo?v=${bust}`"
      loading="lazy"
      :alt="`${t('photoAlt')} ${beer.name}`"
    />
    <p v-if="beer.myPhotoStatus === 'PENDING'" class="muted photo-state">{{ t('photoPending') }}</p>
    <p v-else-if="beer.myPhotoStatus === 'REJECTED'" class="muted photo-state">{{ t('photoRejected') }}</p>
    <div class="photo-actions">
      <button type="button" class="secondary" :disabled="busy || beer.myPhotoStatus === 'PENDING'" @click="input?.click()">
        {{ beer.hasPhoto ? t('photoReplace') : t('photoButton') }}
      </button>
      <button v-if="beer.myPhotoId" type="button" class="secondary" :disabled="busy" @click="remove">{{ t('photoDelete') }}</button>
      <input ref="input" class="photo-input" type="file" accept="image/*" capture="environment" @change="chosen" />
    </div>
    <p class="muted photo-hint">{{ t('photoHint') }}</p>
    <progress v-if="busy" :value="progress ?? 0" max="1" :aria-label="t('photoUploading')"></progress>
    <p aria-live="polite" role="status" :class="failed ? 'error' : 'muted'">{{ message }}</p>
  </div>
</template>

<style scoped>
.photo-box { margin-top: 8px; }
.photo-cover { display: block; max-width: 100%; max-height: 220px; border-radius: 8px; margin-bottom: 8px; }
.photo-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.photo-input { display: none; }
.photo-hint, .photo-state { margin: 4px 0; }
progress { width: 100%; }
</style>
