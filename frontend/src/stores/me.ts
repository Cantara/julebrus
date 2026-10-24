import { defineStore } from 'pinia'
import { ref } from 'vue'
import { get } from '../api/client'
import type { Me } from '../api/types'

export const useMe = defineStore('me', () => {
  const me = ref<Me | null>(null)
  const loading = ref(false)

  async function refresh() {
    loading.value = true
    try {
      me.value = await get<Me>('/api/me')
    } finally {
      loading.value = false
    }
  }

  return { me, loading, refresh }
})
