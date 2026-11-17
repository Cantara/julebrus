<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { fx } from '../effects'

/**
 * Living snow on one canvas: three depth layers (far flakes small, slow and faint), wind that gusts and turns,
 * and near flakes that settle on the top edge of the cards and slowly melt. `intensity` (0..1) thickens the fall;
 * Home feeds it the house's progress towards the star. Drawn by code, no images. Stops when the tab is hidden,
 * when effects are off and under reduced motion.
 */
const props = defineProps<{ enabled: boolean; count?: number; intensity?: number }>()
const canvas = ref<HTMLCanvasElement | null>(null)

type Flake = { x: number; y: number; r: number; vy: number; sway: number; phase: number; layer: number; o: number }
const LAYERS = [
  { share: 0.45, r: [0.8, 1.5], v: [14, 24], o: [0.35, 0.55], wind: 0.4 },
  { share: 0.35, r: [1.4, 2.3], v: [24, 40], o: [0.55, 0.8], wind: 0.7 },
  { share: 0.2, r: [2.1, 3.4], v: [40, 62], o: [0.75, 0.95], wind: 1 },
]
const BIN = 6
const MAX_HEAP = 5

let flakes: Flake[] = []
let raf = 0
let last = 0
let wind = 0
let windTarget = 10
let nextGust = 0
let cards: Element[] = []
let cardsAt = 0
const heaps = new WeakMap<Element, Float32Array>()
let W = 0
let H = 0
let dark = false

const rnd = (a: number, b: number) => a + Math.random() * (b - a)

/**
 * One flake drawn once, then stamped: a soft white dot, with a faint warm shadow on the pale daytime page
 * (a white flake on cream needs an edge to be seen at all, and a drawn ring would look like a bubble).
 */
const SPRITE = 48
const SPRITE_R = 10
let sprite: HTMLCanvasElement | null = null
function makeSprite() {
  sprite = document.createElement('canvas')
  sprite.width = sprite.height = SPRITE
  const g = sprite.getContext('2d')
  if (!g) return
  if (!dark) {
    g.shadowColor = 'rgba(43,26,20,0.55)'
    g.shadowBlur = 7
  }
  g.fillStyle = dark ? 'rgba(255,250,240,0.9)' : '#fff'
  g.beginPath()
  g.arc(SPRITE / 2, SPRITE / 2, SPRITE_R, 0, 6.283)
  g.fill()
}

function spawn(layer: number, anywhere: boolean): Flake {
  const l = LAYERS[layer]
  return {
    x: Math.random() * W, y: anywhere ? Math.random() * H : -6, r: rnd(l.r[0], l.r[1]), vy: rnd(l.v[0], l.v[1]),
    sway: rnd(0.4, 1.3), phase: Math.random() * 6.28, layer, o: rnd(l.o[0], l.o[1]),
  }
}

function populate() {
  const base = props.count ?? 36
  const total = Math.round(base * (1 + 1.6 * Math.min(1, Math.max(0, props.intensity ?? 0))))
  flakes = []
  LAYERS.forEach((l, i) => {
    for (let n = Math.round(total * l.share); n > 0; n--) flakes.push(spawn(i, true))
  })
}

function resize() {
  const c = canvas.value
  if (!c) return
  const dpr = Math.min(2, window.devicePixelRatio || 1)
  W = window.innerWidth
  H = window.innerHeight
  c.width = Math.round(W * dpr)
  c.height = Math.round(H * dpr)
  c.getContext('2d')?.setTransform(dpr, 0, 0, dpr, 0, 0)
}

function frame(t: number) {
  raf = requestAnimationFrame(frame)
  const c = canvas.value
  const ctx = c?.getContext('2d')
  if (!c || !ctx) return
  // Snow falls slowly: thirty frames a second look the same as sixty and cost a phone half as much.
  if (t - last < 30) return
  const dt = Math.min(0.08, (t - last) / 1000 || 0)
  last = t

  if (t > nextGust) {
    windTarget = (Math.random() - 0.5) * (Math.random() < 0.25 ? 220 : 90)
    nextGust = t + rnd(4000, 10000)
  }
  wind += (windTarget - wind) * dt * 0.6

  if (t - cardsAt > 600) {
    cards = Array.from(document.querySelectorAll('main .card'))
    cardsAt = t
  }
  const ledges: { el: Element; l: number; r: number; top: number }[] = []
  for (const el of cards) {
    const b = el.getBoundingClientRect()
    if (b.bottom > 0 && b.top < H && b.width > 60) ledges.push({ el, l: b.left, r: b.right, top: b.top })
  }

  ctx.clearRect(0, 0, W, H)
  for (const f of flakes) {
    const prev = f.y
    f.y += f.vy * dt
    f.x += (wind * LAYERS[f.layer].wind + Math.sin(f.phase + (t / 1000) * f.sway) * 12) * dt
    if (f.x < -8) f.x = W + 6
    else if (f.x > W + 8) f.x = -6
    if (f.layer === 2) {
      for (const g of ledges) {
        if (prev < g.top && f.y >= g.top && f.x > g.l + 14 && f.x < g.r - 14) {
          let heap = heaps.get(g.el)
          const bins = Math.ceil((g.r - g.l) / BIN)
          if (!heap || heap.length !== bins) heaps.set(g.el, (heap = new Float32Array(bins)))
          const i = Math.floor((f.x - g.l) / BIN)
          heap[i] = Math.min(MAX_HEAP, heap[i] + 0.7)
          if (i > 0) heap[i - 1] = Math.min(MAX_HEAP, heap[i - 1] + 0.3)
          if (i < bins - 1) heap[i + 1] = Math.min(MAX_HEAP, heap[i + 1] + 0.3)
          Object.assign(f, spawn(2, false))
          break
        }
      }
    }
    if (f.y > H + 6) Object.assign(f, spawn(f.layer, false))
    const size = (SPRITE / SPRITE_R) * f.r
    ctx.globalAlpha = f.o
    ctx.drawImage(sprite!, f.x - size / 2, f.y - size / 2, size, size)
  }
  ctx.globalAlpha = 1

  // What has settled: a soft ridge along each card's top edge, melting a little every second.
  for (const g of ledges) {
    const heap = heaps.get(g.el)
    if (!heap) continue
    let any = false
    ctx.beginPath()
    ctx.moveTo(g.l + 10, g.top + 0.5)
    for (let i = 2; i < heap.length - 2; i++) {
      heap[i] *= 1 - 0.012 * dt
      if (heap[i] > 0.15) any = true
      const x = g.l + i * BIN
      ctx.quadraticCurveTo(x, g.top - heap[i], x + BIN / 2, g.top - (heap[i] + heap[i + 1]) / 2)
    }
    ctx.lineTo(g.r - 10, g.top + 0.5)
    if (!any) continue
    ctx.closePath()
    ctx.fillStyle = dark ? 'rgba(255,250,240,0.85)' : 'rgba(255,255,255,0.97)'
    ctx.fill()
    if (!dark) {
      ctx.strokeStyle = 'rgba(43,26,20,0.16)'
      ctx.lineWidth = 0.75
      ctx.stroke()
    }
  }
}

function start() {
  if (raf || !canvas.value) return
  dark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  makeSprite()
  resize()
  populate()
  last = performance.now()
  raf = requestAnimationFrame(frame)
}
function stop() {
  cancelAnimationFrame(raf)
  raf = 0
}
function onVisibility() {
  if (document.hidden) stop()
  else if (props.enabled && fx.value) start()
}

onMounted(() => {
  window.addEventListener('resize', resize)
  document.addEventListener('visibilitychange', onVisibility)
  if (props.enabled && fx.value) start()
})
onUnmounted(() => {
  stop()
  window.removeEventListener('resize', resize)
  document.removeEventListener('visibilitychange', onVisibility)
})
watch(() => props.enabled && fx.value, (on) => (on ? requestAnimationFrame(start) : stop()), { flush: 'post' })
watch(() => props.intensity, () => raf && populate())
</script>

<template>
  <canvas v-if="enabled && fx" ref="canvas" class="snow" aria-hidden="true" />
</template>
