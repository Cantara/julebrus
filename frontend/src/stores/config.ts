import { defineStore } from 'pinia'
import { ref } from 'vue'
import { get } from '../api/client'
import type { PublicConfig } from '../api/types'

/** Public config (no login needed): currently only the sponsor line. */
export const useConfig = defineStore('config', () => {
  const config = ref<PublicConfig>({ sponsor: null, contactEmail: null })

  async function load() {
    try {
      config.value = await get<PublicConfig>('/api/config')
    } catch {
      /* no sponsor line if the config cannot be read */
    }
  }

  return { config, load }
})
