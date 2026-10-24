<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '../api/client'
import type { Session } from '../api/types'

const props = defineProps<{ code?: string }>()
const router = useRouter()
const code = ref(props.code ?? '')
const error = ref('')

async function join() {
  error.value = ''
  try {
    const s = await post<Session>('/api/sessions/join', { code: code.value })
    router.replace(`/session/${s.id}`)
  } catch (e) {
    error.value = (e as Error).message
  }
}

onMounted(() => {
  if (props.code) join()
})
</script>

<template>
  <h1>Bli med</h1>
  <form class="card" @submit.prevent="join">
    <p class="muted">Skann QR-koden med kameraappen, eller skriv inn de seks tegnene.</p>
    <input v-model="code" class="code" maxlength="6" autocapitalize="characters" autocomplete="off" placeholder="ABC234" style="font-size: 1.6rem" />
    <p v-if="error" class="error">{{ error }}</p>
    <button :disabled="code.length < 6" style="width: 100%; margin-top: 12px">Bli med</button>
  </form>
</template>
