import { computed, ref, watch } from 'vue'

/**
 * One switch for everything that moves for fun (snow, dice, the glass, frost, fizz, the night sky, the candle).
 * It is the old "snø" switch under the same storage key, so nobody's choice is lost. Reduced motion always wins.
 * Sound is a second, opt-in switch: nothing makes a noise until the person asks for it.
 */
function read(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}
function write(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    /* private mode: the choice lasts for this visit */
  }
}

export const effectsOn = ref(read('snow') !== 'off')
export const soundOn = ref(read('sound') === 'on')
watch(effectsOn, (v) => write('snow', v ? 'on' : 'off'))
watch(soundOn, (v) => write('sound', v ? 'on' : 'off'))

const query = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null
export const reducedMotion = ref(!!query?.matches)
query?.addEventListener?.('change', (e) => (reducedMotion.value = e.matches))

/** True when effects may run: switched on, and the person has not asked the system for less motion. */
export const fx = computed(() => effectsOn.value && !reducedMotion.value)

/** A short tick in the hand, where the phone can (Android); silently nothing elsewhere. */
export function buzz(ms: number | number[] = 12) {
  if (!fx.value) return
  try {
    navigator.vibrate?.(ms)
  } catch {
    /* not allowed here */
  }
}
