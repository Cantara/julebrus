<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { fx } from '../effects'

/**
 * A dark room read by candlelight. What is in the slot lies in the dark; a flame follows your finger (or the
 * mouse) and lights a flickering circle around it. The darkness takes no taps, so links underneath still work,
 * and with effects off or reduced motion the room is simply lit.
 */
const room = ref<HTMLElement | null>(null)
const x = ref(0)
const y = ref(0)
const radius = ref(170)
let timer: number | undefined
let moved = false

function centre() {
  const box = room.value?.getBoundingClientRect()
  if (!box || moved) return
  x.value = box.width / 2
  y.value = Math.min(box.height / 2, 110)
}

function follow(e: PointerEvent) {
  const box = room.value?.getBoundingClientRect()
  if (!box) return
  moved = true
  x.value = Math.min(box.width, Math.max(0, e.clientX - box.left))
  y.value = Math.min(box.height, Math.max(0, e.clientY - box.top))
}

onMounted(() => {
  centre()
  window.addEventListener('resize', centre)
  // A candle never burns evenly: the circle of light breathes a little, ten times a second.
  timer = window.setInterval(() => {
    if (fx.value && !document.hidden) radius.value = 160 + Math.random() * 22
  }, 100)
})
onUnmounted(() => {
  window.removeEventListener('resize', centre)
  clearInterval(timer)
})
</script>

<template>
  <div ref="room" class="room" :class="{ dark: fx }" @pointerdown="follow" @pointermove="follow">
    <slot />
    <template v-if="fx">
      <div class="darkness" :style="{ background: `radial-gradient(circle ${radius}px at ${x}px ${y}px, rgba(255,190,90,0.07) 0%, rgba(40,22,8,0.08) 50%, rgba(14,7,3,0.72) 78%, rgba(8,4,2,0.975) 100%)` }" />
      <svg class="flame" :style="{ left: `${x}px`, top: `${y}px` }" viewBox="0 0 20 30" width="16" height="24" aria-hidden="true">
        <path d="M10 1 C14 9 17 13 17 19 A7 7 0 0 1 3 19 C3 13 6 9 10 1 Z" fill="#f5a623" />
        <path d="M10 10 C12 14 13.5 16 13.5 20 A3.5 3.5 0 0 1 6.5 20 C6.5 16 8 14 10 10 Z" fill="#fff1c2" />
      </svg>
    </template>
  </div>
</template>

<style scoped>
.room { position: relative; border-radius: 14px; padding: 14px 16px; overflow: hidden; }
.room.dark { background: #20140c; color: #f3dfbd; touch-action: none; cursor: crosshair; min-height: 220px; }
.room.dark :deep(a) { color: #f5c87a; }
.room.dark :deep(.muted) { color: #c9ad86; }
.darkness { position: absolute; inset: 0; pointer-events: none; }
.flame { position: absolute; transform: translate(-50%, -150%); pointer-events: none; filter: drop-shadow(0 0 8px rgba(245, 166, 35, 0.8)); animation: flicker 0.9s ease-in-out infinite; transform-origin: 50% 100%; }
@keyframes flicker { 0%, 100% { scale: 1 1; } 40% { scale: 0.92 1.07; } 70% { scale: 1.05 0.95; } }
</style>
