<script setup lang="ts">
import { lang, setLang } from '../i18n'
</script>

<template>
  <span class="row" style="justify-content: flex-start; display: inline-flex">
    <button class="secondary" :aria-pressed="lang === 'nb'" :class="{ on: lang === 'nb' }" lang="nb" @click="setLang('nb')">NB</button>
    <button class="secondary" :aria-pressed="lang === 'en'" :class="{ on: lang === 'en' }" lang="en" @click="setLang('en')">EN</button>
  </span>
</template>
