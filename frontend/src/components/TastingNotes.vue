<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { get } from '../api/client'
import type { StructuredNotes } from '../api/types'

/**
 * Structured notes for the professionals. Collapsed by default so the quick
 * "dice and done" path stays quick.
 */
const notes = defineModel<StructuredNotes>('notes', { required: true })
const tags = defineModel<string[]>('tags', { required: true })

const open = ref(false)
const suggested = ref<string[]>([])
const custom = ref('')

onMounted(async () => {
  suggested.value = await get<string[]>('/api/tastings/tags').catch(() => [])
})

function toggle(tag: string) {
  tags.value = tags.value.includes(tag) ? tags.value.filter((t) => t !== tag) : [...tags.value, tag]
}

function addCustom() {
  const t = custom.value.trim().toLowerCase()
  if (t && !tags.value.includes(t)) tags.value = [...tags.value, t]
  custom.value = ''
}
</script>

<template>
  <button type="button" class="secondary" style="width: 100%" @click="open = !open">
    {{ open ? 'Skjul' : 'Vis' }} smaksnotater for proffene
  </button>
  <div v-if="open">
    <label>Utseende</label>
    <input v-model="notes.appearance" placeholder="Farge, skum, klarhet" />
    <label>Duft</label>
    <input v-model="notes.aroma" placeholder="Rosin, nellik, karamell …" />
    <label>Smak</label>
    <input v-model="notes.taste" placeholder="Malt, sødme, bitterhet, balanse" />
    <label>Ettersmak</label>
    <input v-model="notes.finish" placeholder="Lengde, tørrhet, varme" />

    <label>Stikkord</label>
    <div>
      <span v-for="t in suggested" :key="t" class="pill" :class="{ on: tags.includes(t) }" role="button" @click="toggle(t)">
        {{ t }}
      </span>
      <span v-for="t in tags.filter((x) => !suggested.includes(x))" :key="t" class="pill on" role="button" @click="toggle(t)">
        {{ t }} ×
      </span>
    </div>
    <div class="row" style="margin-top: 8px">
      <input v-model="custom" placeholder="Eget stikkord" @keydown.enter.prevent="addCustom" />
      <button type="button" class="secondary" @click="addCustom">Legg til</button>
    </div>
  </div>
</template>
