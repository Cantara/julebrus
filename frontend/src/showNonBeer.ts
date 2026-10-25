import { ref, watch } from 'vue'

// The fenalår and other food that were scored at the old tastings stay out of the beer views unless the reader asks.
// Remembered per browser; the page works without storage (private window, blocked site data).
const KEY = 'showNonBeer'

function read(): boolean {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

export const showNonBeer = ref(read())

watch(showNonBeer, (v) => {
  try {
    localStorage.setItem(KEY, v ? '1' : '0')
  } catch {
    /* nothing to remember it in */
  }
})
