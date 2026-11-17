<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { fx } from '../effects'

/**
 * The finale's sky: northern lights, stars, and the star's light rays, computed per pixel in one small WebGL
 * fragment shader (no images), with gold sparks on a 2D canvas on top. `lit` false is the waiting sky, faint and
 * slow; when it turns true the aurora flares, the rays come out and the sparks fall. Only the hero is drawn, at
 * 30 frames a second, paused off screen and in a hidden tab. Without WebGL, with effects off or under reduced
 * motion it is a still gradient (one frame is drawn when WebGL is there).
 */
const props = defineProps<{ lit: boolean }>()
const sky = ref<HTMLCanvasElement | null>(null)
const sparks = ref<HTMLCanvasElement | null>(null)
const root = ref<HTMLElement | null>(null)

const VERT = 'attribute vec2 p; void main() { gl_Position = vec4(p, 0.0, 1.0); }'
const FRAG = `
precision mediump float;
uniform vec2 res;
uniform float time;
uniform float lit;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) { return 0.5 * noise(p) + 0.25 * noise(p * 2.03) + 0.125 * noise(p * 4.01); }
void main() {
  vec2 uv = gl_FragCoord.xy / res;
  float aspect = res.x / res.y;
  vec3 col = mix(vec3(0.07, 0.10, 0.20), vec3(0.02, 0.03, 0.09), uv.y);

  // Stars: a grid of cells, about a third of them holding one soft round star at its own spot, size and twinkle.
  vec2 g = gl_FragCoord.xy / (res.y / 24.0);
  vec2 cell = floor(g);
  float s = hash(cell);
  vec2 spot = 0.2 + 0.6 * vec2(hash(cell + 3.1), hash(cell + 5.7));
  float size = mix(0.05, 0.13, hash(cell + 9.3));
  float twinkle = 0.55 + 0.45 * sin(time * (1.0 + 3.0 * hash(cell + 7.0)) + s * 40.0);
  col += vec3(0.9, 0.92, 1.0) * smoothstep(size, 0.0, length(fract(g) - spot)) * step(0.66, s) * twinkle * (0.3 + 0.7 * uv.y);

  // Northern lights: three curtains whose height wanders with noise, striped by finer noise, green low and violet high.
  float power = mix(0.28, 1.0, lit);
  float t = time * mix(0.05, 0.11, lit);
  for (int k = 0; k < 3; k++) {
    float fk = float(k);
    float base = 0.42 + 0.17 * fk + 0.16 * (fbm(vec2(uv.x * (1.3 + 0.5 * fk) + t * (1.0 + 0.4 * fk), fk * 3.7 + t * 0.6)) - 0.5);
    float d = uv.y - base;
    float curtain = exp(-d * d / (d > 0.0 ? 0.035 : 0.0016));
    float streaks = 0.55 + 0.45 * noise(vec2(uv.x * 46.0 + fk * 11.0 + t * 3.0, t * 2.0));
    vec3 tint = mix(vec3(0.16, 0.95, 0.52), vec3(0.50, 0.30, 0.95), clamp(d * 4.5 + 0.15 * fk, 0.0, 1.0));
    col += tint * curtain * streaks * power * (0.55 - 0.1 * fk);
  }

  // The star: a warm core and rays that turn slowly.
  vec2 sp = vec2(0.5, 0.80);
  vec2 dv = vec2((uv.x - sp.x) * aspect, uv.y - sp.y);
  float dist = length(dv);
  float angle = atan(dv.y, dv.x);
  float rays = pow(0.5 + 0.5 * sin(angle * 8.0 + time * 0.25), 6.0) + 0.6 * pow(0.5 + 0.5 * sin(angle * 13.0 - time * 0.17), 9.0);
  vec3 gold = vec3(1.0, 0.80, 0.45);
  col += gold * (exp(-dist * 26.0) * 1.6 + rays * exp(-dist * 4.2) * 0.55 + exp(-dist * 7.0) * 0.35) * lit;
  col += gold * exp(-dist * 30.0) * 0.25 * (1.0 - lit);

  gl_FragColor = vec4(col, 1.0);
}`

let gl: WebGLRenderingContext | null = null
let uniforms: Record<string, WebGLUniformLocation | null> = {}
let raf = 0
let lastDraw = 0
let started = 0
let litLevel = 0
let visible = true
/** Canvas pixels per CSS pixel for the sky. Starts sharp; drops to 1 if the first second of frames runs slow. */
let scale = 0
let slowFrames = 0
let counted = 0
let observer: IntersectionObserver | undefined

type Spark = { x: number; y: number; vx: number; vy: number; r: number; life: number; max: number }
let sparkList: Spark[] = []
let lastSpark = 0

function setup(): boolean {
  const c = sky.value
  if (!c) return false
  gl = c.getContext('webgl', { antialias: false, alpha: false, powerPreference: 'low-power' })
  if (!gl) return false
  const compile = (type: number, src: string) => {
    const sh = gl!.createShader(type)!
    gl!.shaderSource(sh, src)
    gl!.compileShader(sh)
    return gl!.getShaderParameter(sh, gl!.COMPILE_STATUS) ? sh : null
  }
  const vs = compile(gl.VERTEX_SHADER, VERT)
  const fs = compile(gl.FRAGMENT_SHADER, FRAG)
  const program = gl.createProgram()
  if (!vs || !fs || !program) return (gl = null), false
  gl.attachShader(program, vs)
  gl.attachShader(program, fs)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) return (gl = null), false
  gl.useProgram(program)
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer())
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW)
  const loc = gl.getAttribLocation(program, 'p')
  gl.enableVertexAttribArray(loc)
  gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0)
  uniforms = { res: gl.getUniformLocation(program, 'res'), time: gl.getUniformLocation(program, 'time'), lit: gl.getUniformLocation(program, 'lit') }
  return true
}

function size() {
  const el = root.value
  if (!el) return
  const box = el.getBoundingClientRect()
  const dpr = Math.min(2, window.devicePixelRatio || 1)
  if (!scale) scale = dpr
  // A small picture (the hero only), so the shader is cheap; a phone that still struggles gets it at half size.
  if (sky.value) {
    sky.value.width = Math.max(2, Math.round(box.width * scale))
    sky.value.height = Math.max(2, Math.round(box.height * scale))
  }
  if (sparks.value) {
    sparks.value.width = Math.round(box.width * dpr)
    sparks.value.height = Math.round(box.height * dpr)
    sparks.value.getContext('2d')?.setTransform(dpr, 0, 0, dpr, 0, 0)
  }
}

function drawSky(seconds: number) {
  if (!gl || !sky.value) return
  gl.viewport(0, 0, sky.value.width, sky.value.height)
  gl.uniform2f(uniforms.res, sky.value.width, sky.value.height)
  gl.uniform1f(uniforms.time, seconds)
  gl.uniform1f(uniforms.lit, litLevel)
  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
}

function burst(count: number) {
  const el = root.value
  if (!el) return
  const box = el.getBoundingClientRect()
  for (let i = 0; i < count; i++) {
    const angle = Math.random() * 6.283
    const speed = 30 + Math.random() * 150
    const max = 2.2 + Math.random() * 2.6
    sparkList.push({ x: box.width / 2, y: box.height * 0.2, vx: Math.cos(angle) * speed, vy: Math.sin(angle) * speed - 40, r: 1 + Math.random() * 1.8, life: max, max })
  }
}

function drawSparks(dt: number, now: number) {
  const c = sparks.value
  const ctx = c?.getContext('2d')
  const el = root.value
  if (!c || !ctx || !el) return
  const box = el.getBoundingClientRect()
  ctx.clearRect(0, 0, box.width, box.height)
  // After the burst a slow drizzle keeps falling from the star.
  if (litLevel > 0.9 && now - lastSpark > 260) {
    lastSpark = now
    const max = 2.5 + Math.random() * 2
    sparkList.push({ x: box.width * (0.3 + Math.random() * 0.4), y: box.height * 0.22, vx: (Math.random() - 0.5) * 26, vy: 10 + Math.random() * 20, r: 0.8 + Math.random() * 1.4, life: max, max })
  }
  for (const s of sparkList) {
    s.vy += 46 * dt
    s.vx *= 1 - 0.6 * dt
    s.x += s.vx * dt
    s.y += s.vy * dt
    s.life -= dt
    const a = Math.max(0, s.life / s.max) * (0.6 + 0.4 * Math.sin(now / 90 + s.x))
    ctx.fillStyle = `rgba(255,214,130,${a})`
    ctx.beginPath()
    ctx.arc(s.x, s.y, s.r, 0, 6.283)
    ctx.fill()
  }
  sparkList = sparkList.filter((s) => s.life > 0 && s.y < box.height + 6)
}

function frame(now: number) {
  raf = requestAnimationFrame(frame)
  if (!visible || document.hidden || now - lastDraw < 33) return
  const dt = Math.min(0.1, (now - lastDraw) / 1000)
  lastDraw = now
  if (scale > 1 && counted < 30) {
    counted++
    if (dt > 0.04) slowFrames++ // under 25 frames a second
    if (counted === 30 && slowFrames > 10) {
      scale = 1
      size()
    }
  }
  // Ease towards the lit state over a couple of seconds: the ignition.
  litLevel += ((props.lit ? 1 : 0) - litLevel) * Math.min(1, dt * 1.4)
  drawSky((now - started) / 1000)
  drawSparks(dt, now)
}

function start() {
  cancelAnimationFrame(raf)
  size()
  if (!fx.value) {
    // Still: one frame of the sky as it is, no sparks.
    litLevel = props.lit ? 1 : 0
    drawSky(12)
    sparks.value?.getContext('2d')?.clearRect(0, 0, sparks.value.width, sparks.value.height)
    return
  }
  started = performance.now()
  lastDraw = started
  if (props.lit) window.setTimeout(() => burst(90), 900)
  raf = requestAnimationFrame(frame)
}

onMounted(() => {
  setup()
  observer = new IntersectionObserver(([e]) => (visible = e.isIntersecting))
  if (root.value) observer.observe(root.value)
  window.addEventListener('resize', size)
  start()
})
onUnmounted(() => {
  cancelAnimationFrame(raf)
  observer?.disconnect()
  window.removeEventListener('resize', size)
})
watch(fx, start)
watch(() => props.lit, (on) => on && fx.value && window.setTimeout(() => burst(90), 900))
</script>

<template>
  <div ref="root" class="nightsky" :class="{ lit }" aria-hidden="true">
    <canvas ref="sky" />
    <canvas ref="sparks" />
  </div>
</template>

<style scoped>
/* The gradient is what shows when WebGL is not there. */
.nightsky { position: absolute; inset: 0; border-radius: 16px; overflow: hidden; background: linear-gradient(#05081a, #101a36 55%, #12303a); }
.nightsky.lit { background: radial-gradient(circle at 50% 20%, #6b5320 0, transparent 38%), linear-gradient(#05081a, #101a36 55%, #12303a); }
.nightsky canvas { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
</style>
