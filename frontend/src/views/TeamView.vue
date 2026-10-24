<script setup lang="ts">
import { useRouter } from 'vue-router'
import TeamPicker from '../components/TeamPicker.vue'

const router = useRouter()
</script>

<template>
  <h1>Hvilket lag spiller du for?</h1>
  <p class="muted">Cantara mot Rebel, eller begge. Lagene får hver sin vinner i finalen, og årets lag kåres på terningkast per hode.</p>
  <TeamPicker allow-later @done="router.replace('/')" />
</template>
