<script setup lang="ts">
import { ref, watchEffect } from 'vue'
import QRCode from 'qrcode'

const props = defineProps<{ value: string; size?: number }>()
const dataUrl = ref('')

watchEffect(async () => {
  dataUrl.value = await QRCode.toDataURL(props.value, {
    width: props.size ?? 256,
    margin: 1,
    color: { dark: '#241a1a', light: '#ffffff' },
  })
})
</script>

<template>
  <img :src="dataUrl" :width="size ?? 256" :height="size ?? 256" alt="QR-kode for å bli med" style="border-radius: 12px; background: #fff" />
</template>
