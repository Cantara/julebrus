<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { buzz, fx } from '../effects'
import { prime } from '../sound'

/**
 * Rime on the window. What is in the slot sits behind a frosted pane that you wipe away with a finger; when a third
 * of it is gone the rest melts. The frost is drawn by code (ice ferns growing in from the edges, the same ones
 * all day for the same `seed`). "Hopp over" melts it without wiping, and with effects off or reduced motion there
 * is no frost at all. Once wiped, the pane stays clear for the rest of the visit.
 */
const props = defineProps<{ active: boolean; seed: string }>()
const emit = defineEmits<{ cleared: [] }>()

const KEY = `frost:${props.seed}`
function wipedBefore(): boolean {
  try {
    return sessionStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

const frosted = ref(props.active && fx.value && !wipedBefore())
const melting = ref(false)
const touched = ref(false)
const wrap = ref<HTMLElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
let lastPoint: [number, number] | null = null
let checkedAt = 0
let dpr = 1

/** The one drawing context; it is read back while wiping, which the browser wants to be told up front. */
const pen = () => canvas.value?.getContext('2d', { willReadFrequently: true }) ?? null

/** A small seeded generator, so the frost is the same picture each time the door is looked at. */
function generator(text: string) {
  let a = 0
  for (const ch of text) a = (Math.imul(a, 31) + ch.charCodeAt(0)) | 0
  return () => {
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function paint() {
  const c = canvas.value
  if (!c) return
  // Its own box, not the wrapper's: the pane overhangs the content a little, and wiping uses these coordinates.
  const box = c.getBoundingClientRect()
  dpr = Math.min(2, window.devicePixelRatio || 1)
  const W = box.width
  const H = box.height
  c.width = Math.round(W * dpr)
  c.height = Math.round(H * dpr)
  const ctx = pen()
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  const rnd = generator(props.seed)

  const ice = ctx.createLinearGradient(0, 0, W, H)
  ice.addColorStop(0, 'rgba(236,244,250,0.98)')
  ice.addColorStop(0.5, 'rgba(214,229,240,0.97)')
  ice.addColorStop(1, 'rgba(228,239,247,0.98)')
  ctx.fillStyle = ice
  ctx.fillRect(0, 0, W, H)

  // Fine grain, then ferns growing in from the edges, then a few six-armed crystals.
  for (let i = 0; i < W * H * 0.012; i++) {
    ctx.fillStyle = `rgba(255,255,255,${0.25 + rnd() * 0.5})`
    ctx.fillRect(rnd() * W, rnd() * H, 1.2, 1.2)
  }
  const fern = (x: number, y: number, angle: number, len: number, depth: number) => {
    if (depth === 0 || len < 4) return
    const x2 = x + Math.cos(angle) * len
    const y2 = y + Math.sin(angle) * len
    ctx.strokeStyle = `rgba(255,255,255,${0.5 + depth * 0.1})`
    ctx.lineWidth = 0.5 + depth * 0.35
    ctx.beginPath()
    ctx.moveTo(x, y)
    ctx.lineTo(x2, y2)
    ctx.stroke()
    for (let k = 1; k <= 4; k++) {
      const px = x + Math.cos(angle) * len * (k / 5)
      const py = y + Math.sin(angle) * len * (k / 5)
      const side = len * 0.34 * (1 - k / 7)
      fern(px, py, angle + 1.05, side, depth - 1)
      fern(px, py, angle - 1.05, side, depth - 1)
    }
    fern(x2, y2, angle + (rnd() - 0.5) * 0.5, len * 0.62, depth - 1)
  }
  for (let i = 0; i < 12; i++) {
    const edge = Math.floor(rnd() * 4)
    const along = rnd()
    const [x, y, inward] = edge === 0 ? [along * W, 0, Math.PI / 2] : edge === 1 ? [W, along * H, Math.PI] : edge === 2 ? [along * W, H, -Math.PI / 2] : [0, along * H, 0]
    fern(x, y, inward + (rnd() - 0.5) * 0.9, 34 + rnd() * 46, 3)
  }
  for (let i = 0; i < 6; i++) {
    const x = W * (0.15 + rnd() * 0.7)
    const y = H * (0.15 + rnd() * 0.7)
    const size = 9 + rnd() * 13
    for (let arm = 0; arm < 6; arm++) fern(x, y, (arm * Math.PI) / 3, size, 2)
  }
}

function point(e: PointerEvent): [number, number] {
  const box = canvas.value!.getBoundingClientRect()
  return [e.clientX - box.left, e.clientY - box.top]
}

function wipe(e: PointerEvent) {
  const ctx = pen()
  if (!ctx || melting.value) return
  const p = point(e)
  ctx.globalCompositeOperation = 'destination-out'
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.lineWidth = 56
  ctx.beginPath()
  ctx.moveTo(...(lastPoint ?? p))
  ctx.lineTo(p[0] + 0.01, p[1])
  ctx.stroke()
  ctx.globalCompositeOperation = 'source-over'
  lastPoint = p
  if (performance.now() - checkedAt > 140) check()
}

/** Melts the rest once a third of the pane is wiped clear. */
function check() {
  const c = canvas.value
  const ctx = pen()
  if (!c || !ctx || melting.value) return
  checkedAt = performance.now()
  const data = ctx.getImageData(0, 0, c.width, c.height).data
  const step = Math.max(4, Math.round(10 * dpr))
  let clear = 0
  let total = 0
  for (let y = 0; y < c.height; y += step) {
    for (let x = 0; x < c.width; x += step) {
      total++
      if (data[(y * c.width + x) * 4 + 3] < 40) clear++
    }
  }
  if (clear / total > 0.34) melt() // about two swipes across
}

function down(e: PointerEvent) {
  prime()
  touched.value = true
  canvas.value?.setPointerCapture?.(e.pointerId)
  lastPoint = null
  wipe(e)
}
function move(e: PointerEvent) {
  if (e.buttons === 0 && e.pointerType === 'mouse') return
  if (lastPoint) wipe(e)
}
function up() {
  lastPoint = null
  check() // a quick wipe can end between two throttled checks
}

function melt() {
  if (melting.value) return
  melting.value = true
  buzz(10)
  try {
    sessionStorage.setItem(KEY, '1')
  } catch {
    /* private mode */
  }
  window.setTimeout(() => {
    frosted.value = false
    emit('cleared')
  }, 650)
}

let observer: ResizeObserver | undefined
onMounted(async () => {
  if (!frosted.value) return
  await nextTick()
  paint()
  // The card can change size once fonts and the question have settled: repaint until the first touch.
  observer = new ResizeObserver(() => !touched.value && paint())
  if (wrap.value) observer.observe(wrap.value)
})
onUnmounted(() => observer?.disconnect())
</script>

<template>
  <div ref="wrap" class="frostwrap">
    <div :inert="frosted && !melting">
      <slot />
    </div>
    <template v-if="frosted">
      <canvas
        ref="canvas" class="frost" :class="{ melting }" aria-hidden="true"
        @pointerdown="down" @pointermove="move" @pointerup="up" @pointercancel="up"
      />
      <p class="hint" :class="{ gone: touched }" aria-hidden="true">❄ Tørk av rimet med fingeren</p>
      <button v-if="!melting" type="button" class="skip" @click="melt">Hopp over</button>
    </template>
  </div>
</template>

<style scoped>
.frostwrap { position: relative; }
.frost {
  position: absolute; inset: -6px; width: calc(100% + 12px); height: calc(100% + 12px); border-radius: 12px;
  touch-action: none; cursor: grab; animation: grow 500ms ease-out; transition: opacity 650ms ease, filter 650ms ease;
}
.frost.melting { opacity: 0; filter: blur(6px); pointer-events: none; }
@keyframes grow { from { opacity: 0; transform: scale(1.04); } to { opacity: 1; transform: none; } }
.hint {
  position: absolute; left: 0; right: 0; top: 42%; margin: 0; text-align: center; pointer-events: none;
  color: #34506a; font-weight: 600; text-shadow: 0 0 8px rgba(255, 255, 255, 0.9); transition: opacity 300ms;
}
.hint.gone { opacity: 0; }
.skip {
  position: absolute; right: 2px; bottom: 0; padding: 4px 8px; font-size: 0.8rem; background: rgba(255, 255, 255, 0.7);
  color: #34506a; border: 1px solid rgba(52, 80, 106, 0.35); border-radius: 8px;
}
</style>
