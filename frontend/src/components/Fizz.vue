<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { fx } from '../effects'

/**
 * Carbonation for the whole screen. `rise()` sends a handful of beer bubbles up from the bottom (a roll came in
 * somewhere); `foam(x, y)` bursts cream foam out of a point (a skål). One canvas above the page that takes no
 * taps, and it only runs while there is something to draw.
 */
const canvas = ref<HTMLCanvasElement | null>(null)

type Bubble = { kind: 'bubble'; x: number; y: number; r: number; vy: number; phase: number; life: number }
type Fleck = { kind: 'foam'; x: number; y: number; r: number; vx: number; vy: number; life: number; max: number }
let things: (Bubble | Fleck)[] = []
let raf = 0
let last = 0

function ready(): CanvasRenderingContext2D | null {
  const c = canvas.value
  if (!c || !fx.value) return null
  const dpr = Math.min(2, window.devicePixelRatio || 1)
  if (c.width !== Math.round(window.innerWidth * dpr) || c.height !== Math.round(window.innerHeight * dpr)) {
    c.width = Math.round(window.innerWidth * dpr)
    c.height = Math.round(window.innerHeight * dpr)
  }
  const ctx = c.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)
  return ctx
}

function frame(t: number) {
  const ctx = ready()
  if (!ctx) {
    things = []
    raf = 0
    return
  }
  const dt = Math.min(0.05, (t - last) / 1000 || 0)
  last = t
  const W = window.innerWidth
  const H = window.innerHeight
  ctx.clearRect(0, 0, W, H)
  for (const p of things) {
    if (p.kind === 'bubble') {
      p.y -= p.vy * dt
      p.life = p.y / H // fades as it nears the top
      const x = p.x + Math.sin(t / 380 + p.phase) * 9
      const alpha = Math.min(1, Math.max(0, p.life * 1.6))
      ctx.beginPath()
      ctx.arc(x, p.y, p.r, 0, 6.283)
      ctx.fillStyle = `rgba(240,180,90,${0.16 * alpha})`
      ctx.fill()
      ctx.strokeStyle = `rgba(214,150,60,${0.75 * alpha})`
      ctx.lineWidth = 1.2
      ctx.stroke()
      ctx.beginPath()
      ctx.arc(x - p.r * 0.35, p.y - p.r * 0.35, p.r * 0.22, 0, 6.283)
      ctx.fillStyle = `rgba(255,255,255,${0.8 * alpha})`
      ctx.fill()
    } else {
      p.vy += 260 * dt
      p.x += p.vx * dt
      p.y += p.vy * dt
      p.life -= dt
      const alpha = Math.max(0, p.life / p.max)
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r * (0.6 + 0.4 * alpha), 0, 6.283)
      ctx.fillStyle = `rgba(255,243,214,${alpha})`
      ctx.fill()
      ctx.strokeStyle = `rgba(190,150,90,${0.5 * alpha})`
      ctx.lineWidth = 0.8
      ctx.stroke()
    }
  }
  things = things.filter((p) => (p.kind === 'bubble' ? p.y > -20 : p.life > 0))
  if (things.length) raf = requestAnimationFrame(frame)
  else {
    ctx.clearRect(0, 0, W, H)
    raf = 0
  }
}

function go() {
  if (raf) return
  last = performance.now()
  raf = requestAnimationFrame(frame)
}

function rise(count = 16) {
  if (!ready()) return
  const W = window.innerWidth
  const H = window.innerHeight
  for (let i = 0; i < count; i++) {
    things.push({ kind: 'bubble', x: Math.random() * W, y: H + 10 + Math.random() * 160, r: 3 + Math.random() * 7, vy: 120 + Math.random() * 170, phase: Math.random() * 6.28, life: 1 })
  }
  go()
}

function foam(x: number, y: number, count = 22) {
  if (!ready()) return
  for (let i = 0; i < count; i++) {
    const angle = -Math.PI / 2 + (Math.random() - 0.5) * 2.4
    const speed = 90 + Math.random() * 190
    const max = 0.6 + Math.random() * 0.5
    things.push({ kind: 'foam', x, y, r: 2.5 + Math.random() * 4.5, vx: Math.cos(angle) * speed, vy: Math.sin(angle) * speed, life: max, max })
  }
  go()
}

onUnmounted(() => cancelAnimationFrame(raf))
defineExpose({ rise, foam })
</script>

<template>
  <canvas v-if="fx" ref="canvas" class="fizz" aria-hidden="true" />
</template>

<style scoped>
.fizz { position: fixed; inset: 0; width: 100%; height: 100%; pointer-events: none; z-index: 4; }
</style>
