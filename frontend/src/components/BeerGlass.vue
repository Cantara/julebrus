<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'

/**
 * A glass that fills to your score. Six marks on the side, one per dice value: the beer rises to the mark you
 * rolled, in the beer's own colour, bubbles climb and the foam settles on top. Drawn on a canvas by code.
 * `animate` false draws the finished glass once.
 */
const props = withDefaults(defineProps<{ score: number; colour: string; animate?: boolean; width?: number; height?: number }>(), {
  animate: true, width: 170, height: 230,
})
const emit = defineEmits<{ done: [] }>()
const canvas = ref<HTMLCanvasElement | null>(null)

const FILL_MS = 1400
const TOTAL_MS = 2300
let raf = 0
let startAt = 0

/** The same 22 bubbles every time: where across the glass, how fast, how big, where in their climb. */
const BUBBLES = Array.from({ length: 22 }, (_, i) => {
  const h = (n: number) => {
    const x = Math.sin((i + 1) * n) * 43758.5453
    return x - Math.floor(x)
  }
  return { x: h(12.9898), speed: 26 + h(78.233) * 44, r: 1.2 + h(37.719) * 2.2, phase: h(4.581) * 300, wobble: h(9.17) * 6.28 }
})

function mix(hex: string, with_: [number, number, number], amount: number): string {
  const n = parseInt(hex.slice(1), 16)
  const c = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v, i) => Math.round(v + (with_[i] - v) * amount))
  return `rgb(${c[0]},${c[1]},${c[2]})`
}

function draw(ms: number) {
  const c = canvas.value
  const ctx = c?.getContext('2d')
  if (!c || !ctx) return
  const W = props.width
  const H = props.height
  const dpr = Math.min(2, window.devicePixelRatio || 1)
  if (c.width !== Math.round(W * dpr)) {
    c.width = Math.round(W * dpr)
    c.height = Math.round(H * dpr)
  }
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, W, H)

  const ink = getComputedStyle(c).color
  const cx = W / 2
  const top = 16
  const bottom = H - 12
  const half = (y: number) => W * 0.26 + ((bottom - y) / (bottom - top)) * W * 0.11 // wider at the rim
  const inner = bottom - top - 6
  const fill = Math.min(1, ms / FILL_MS)
  const eased = 1 - Math.pow(1 - fill, 3)
  const level = 0.84 * (Math.min(6, Math.max(1, props.score)) / 6) * eased
  const surface = bottom - 4 - inner * level
  const t = ms / 1000

  const glass = () => {
    ctx.beginPath()
    ctx.moveTo(cx - half(top), top)
    ctx.lineTo(cx - half(bottom) + 6, bottom - 4)
    ctx.quadraticCurveTo(cx - half(bottom) + 6, bottom, cx - half(bottom) + 12, bottom)
    ctx.lineTo(cx + half(bottom) - 12, bottom)
    ctx.quadraticCurveTo(cx + half(bottom) - 6, bottom, cx + half(bottom) - 6, bottom - 4)
    ctx.lineTo(cx + half(top), top)
  }

  ctx.save()
  glass()
  ctx.closePath()
  ctx.clip()

  // The beer, with a surface that sloshes while it pours and calms as it fills.
  const slosh = 3.5 * (1 - eased)
  const body = ctx.createLinearGradient(0, surface, 0, bottom)
  body.addColorStop(0, mix(props.colour, [255, 214, 140], 0.32))
  body.addColorStop(1, props.colour)
  ctx.fillStyle = body
  ctx.beginPath()
  ctx.moveTo(0, bottom + 2)
  for (let x = 0; x <= W; x += 6) ctx.lineTo(x, surface + Math.sin(x * 0.09 + t * 9) * slosh)
  ctx.lineTo(W, bottom + 2)
  ctx.closePath()
  ctx.fill()

  // Bubbles climbing through it.
  const depth = bottom - surface
  if (depth > 8) {
    for (const b of BUBBLES) {
      const y = bottom - 4 - ((t * b.speed + b.phase) % depth)
      const span = half(y) - 9
      const x = cx + (b.x * 2 - 1) * span + Math.sin(t * 2.2 + b.wobble) * 2
      ctx.beginPath()
      ctx.arc(x, y, b.r, 0, 6.283)
      ctx.fillStyle = 'rgba(255,255,255,0.2)'
      ctx.fill()
      ctx.strokeStyle = 'rgba(255,255,255,0.6)'
      ctx.lineWidth = 0.8
      ctx.stroke()
    }
  }

  // The head: it rises behind the beer and settles into a bumpy cap.
  const foam = Math.min(1, Math.max(0, (ms - 500) / 1100))
  if (foam > 0) {
    const thick = (8 + inner * 0.07) * foam
    ctx.fillStyle = '#fff3d6'
    ctx.beginPath()
    ctx.rect(0, surface - thick + 3, W, thick)
    ctx.fill()
    for (let i = 0; i < 9; i++) {
      const x = cx - half(surface) + (i + 0.5) * ((half(surface) * 2) / 9)
      ctx.beginPath()
      ctx.arc(x, surface - thick + 4, 7 + (i % 3) * 1.6, 0, 6.283)
      ctx.fill()
    }
  }
  ctx.restore()

  // The glass itself: outline, a streak of light, and six marks, one per dice value.
  glass()
  ctx.strokeStyle = ink
  ctx.globalAlpha = 0.55
  ctx.lineWidth = 2.5
  ctx.lineJoin = 'round'
  ctx.stroke()
  ctx.globalAlpha = 1
  ctx.strokeStyle = 'rgba(255,255,255,0.4)'
  ctx.lineWidth = 3
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.moveTo(cx - half(top + 26) + 9, top + 26)
  ctx.lineTo(cx - half(bottom - 40) + 9, bottom - 40)
  ctx.stroke()
  for (let k = 1; k <= 6; k++) {
    const y = bottom - 4 - inner * 0.84 * (k / 6)
    const reached = y >= surface - 1
    ctx.strokeStyle = ink
    ctx.globalAlpha = reached ? 0.9 : 0.35
    ctx.lineWidth = k === props.score && reached ? 2.4 : 1.4
    ctx.beginPath()
    ctx.moveTo(cx + half(y) - 3, y)
    ctx.lineTo(cx + half(y) - (k === props.score ? 16 : 10), y)
    ctx.stroke()
  }
  ctx.globalAlpha = 1
}

function frame(now: number) {
  const ms = now - startAt
  draw(ms)
  if (ms < TOTAL_MS) raf = requestAnimationFrame(frame)
  else emit('done')
}

function play() {
  cancelAnimationFrame(raf)
  if (!props.animate) {
    draw(TOTAL_MS)
    return
  }
  startAt = performance.now()
  raf = requestAnimationFrame(frame)
}

onMounted(play)
onUnmounted(() => cancelAnimationFrame(raf))
watch(() => [props.score, props.colour], play)
</script>

<template>
  <canvas ref="canvas" class="glass" :style="{ width: `${width}px`, height: `${height}px` }" role="img" :aria-label="`Glasset er fylt til ${score} av 6`" />
</template>

<style scoped>
.glass { display: block; color: var(--ink); }
</style>
