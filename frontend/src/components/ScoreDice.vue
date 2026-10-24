<script setup lang="ts">
defineProps<{ modelValue: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: number] }>()
</script>

<template>
  <div class="dice" role="radiogroup" aria-label="Terningkast">
    <button
      v-for="n in 6"
      :key="n"
      type="button"
      role="radio"
      :aria-checked="modelValue === n"
      :class="{ on: modelValue === n }"
      @click="emit('update:modelValue', n)"
    >
      {{ n }}
    </button>
  </div>
</template>
