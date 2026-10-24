<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '../api/client'
import type { Finale, PalatePairs } from '../api/types'
import Snow from '../components/Snow.vue'
import { useMe } from '../stores/me'

const store = useMe()
const route = useRoute()
const finale = ref<Finale | null>(null)
const pairs = ref<PalatePairs | null>(null)
const error = ref('')
const now = ref(Date.now())
let tick: number | undefined

const wantPreview = computed(() => route.query.preview === 'true')

async function load() {
  try {
    const q = wantPreview.value ? '?preview=true' : ''
    finale.value = await get<Finale>(`/api/finale${q}`)
    if (finale.value.podium) pairs.value = await get<PalatePairs>('/api/stats/palate/pairs').catch(() => null)
  } catch (e) {
    error.value = (e as Error).message
  }
}

onMounted(() => {
  load()
  tick = window.setInterval(() => {
    now.value = Date.now()
    // Flip over on our own once the clock passes the reveal.
    if (finale.value && !finale.value.revealed && now.value >= Date.parse(finale.value.revealAt)) load()
  }, 30_000)
})
onUnmounted(() => clearInterval(tick))

const countdown = computed(() => {
  if (!finale.value) return null
  const ms = Math.max(0, Date.parse(finale.value.revealAt) - now.value)
  const d = Math.floor(ms / 86_400_000)
  const h = Math.floor((ms % 86_400_000) / 3_600_000)
  const m = Math.floor((ms % 3_600_000) / 60_000)
  return { d, h, m }
})

const revealDate = computed(() =>
  finale.value ? new Date(finale.value.revealAt).toLocaleString('nb-NO', { timeZone: 'Europe/Oslo', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }) : '')

/** Podium order left to right: silver, gold, bronze. */
const podium = computed(() => {
  const p = finale.value?.podium ?? []
  return [p[1], p[0], p[2]].filter(Boolean)
})

async function share() {
  if (!finale.value?.podium) return
  const f = finale.value
  const lines = (f.podium ?? []).map((b) => `${b.rank}. ${b.name}${b.brewery ? ' (' + b.brewery + ')' : ''} – ${b.bayesian.toFixed(2)}`)
  const text = `Cantaras juleøl ${f.year}\n${lines.join('\n')}`
  const png = await renderCard(f)
  const file = png ? new File([png], `juleol-${f.year}.png`, { type: 'image/png' }) : null
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean }
  try {
    if (file && nav.share && nav.canShare?.({ files: [file] })) {
      await nav.share({ title: `Cantaras juleøl ${f.year}`, text, files: [file] })
      return
    }
    if (nav.share) {
      await nav.share({ title: `Cantaras juleøl ${f.year}`, text })
      return
    }
  } catch {
    /* cancelled */
  }
  if (png) {
    const a = document.createElement('a')
    a.href = URL.createObjectURL(png)
    a.download = `juleol-${f.year}.png`
    a.click()
    URL.revokeObjectURL(a.href)
  }
}

/** A 1080x1350 result card, drawn with the julestue palette. */
function renderCard(f: Finale): Promise<Blob | null> {
  const c = document.createElement('canvas')
  c.width = 1080
  c.height = 1350
  const ctx = c.getContext('2d')
  if (!ctx) return Promise.resolve(null)
  ctx.fillStyle = '#1b120e'
  ctx.fillRect(0, 0, c.width, c.height)
  const glow = ctx.createRadialGradient(540, 120, 10, 540, 120, 700)
  glow.addColorStop(0, 'rgba(245,200,122,0.45)')
  glow.addColorStop(1, 'rgba(245,200,122,0)')
  ctx.fillStyle = glow
  ctx.fillRect(0, 0, c.width, c.height)
  ctx.textAlign = 'center'
  ctx.fillStyle = '#f5c87a'
  ctx.font = '700 64px Fraunces, Georgia, serif'
  ctx.fillText('★', 540, 150)
  ctx.fillStyle = '#f1e6d4'
  ctx.font = '700 72px Fraunces, Georgia, serif'
  ctx.fillText(`Cantaras juleøl ${f.year}`, 540, 250)
  const medals = ['#d4a04f', '#b7b7b7', '#9a6b3c']
  ;(f.podium ?? []).forEach((b, i) => {
    const y = 420 + i * 230
    ctx.fillStyle = '#271b16'
    roundRect(ctx, 90, y - 90, 900, 180, 28)
    ctx.fill()
    ctx.fillStyle = medals[i] ?? '#b7b7b7'
    ctx.beginPath()
    ctx.arc(170, y, 46, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = '#1b120e'
    ctx.font = '700 44px system-ui, sans-serif'
    ctx.fillText(String(b.rank), 170, y + 16)
    ctx.textAlign = 'left'
    ctx.fillStyle = '#f1e6d4'
    ctx.font = '700 52px Fraunces, Georgia, serif'
    ctx.fillText(fit(ctx, b.name, 640), 250, y + 2)
    ctx.fillStyle = '#b39d8a'
    ctx.font = '400 34px system-ui, sans-serif'
    ctx.fillText(`${b.brewery ?? ''}  ·  ${b.bayesian.toFixed(2)} av 6  ·  ${b.ratings} smakere`, 250, y + 58)
    ctx.textAlign = 'center'
  })
  ctx.fillStyle = '#b39d8a'
  ctx.font = '400 34px system-ui, sans-serif'
  ctx.fillText(`${f.totals.tastings} terningkast · ${f.totals.beers} øl · ${f.totals.people} smakere`, 540, 1240)
  return new Promise((resolve) => c.toBlob(resolve, 'image/png'))
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function fit(ctx: CanvasRenderingContext2D, text: string, max: number) {
  let t = text
  while (ctx.measureText(t).width > max && t.length > 3) t = t.slice(0, -2) + '…'
  return t
}
</script>

<template>
  <Snow :enabled="true" :count="24" />
  <p v-if="error" class="error">{{ error }}</p>

  <template v-if="finale && !finale.podium">
    <div class="finale-hero">
      <div class="star">★</div>
      <h1>Finalen {{ finale.year }}</h1>
      <p class="muted">Cantaras juleøl kåres {{ revealDate }}.</p>
      <div v-if="countdown" class="countdown" role="timer">
        <div><strong>{{ countdown.d }}</strong><span>dager</span></div>
        <div><strong>{{ countdown.h }}</strong><span>timer</span></div>
        <div><strong>{{ countdown.m }}</strong><span>min</span></div>
      </div>
    </div>
    <div class="card">
      <strong class="display">Så langt i sesongen</strong>
      <table>
        <tbody>
          <tr><td>Terningkast</td><td style="text-align: right"><strong>{{ finale.totals.tastings }}</strong></td></tr>
          <tr><td>Øl på bordet</td><td style="text-align: right"><strong>{{ finale.totals.beers }}</strong></td></tr>
          <tr><td>Smakere</td><td style="text-align: right"><strong>{{ finale.totals.people }}</strong></td></tr>
          <tr><td>Smakinger i grupper</td><td style="text-align: right"><strong>{{ finale.totals.sessions }}</strong></td></tr>
        </tbody>
      </table>
      <p class="muted" style="margin: 10px 0 0">Den løpende listen ligger under <router-link to="/stats">Resultater</router-link>. Stjernen her er for seremonien.</p>
    </div>
    <p v-if="store.me?.admin && !wantPreview" class="muted" style="text-align: center">
      <router-link to="/finale?preview=true">Forhåndsvis finalen (kun admin)</router-link>
    </p>
  </template>

  <template v-else-if="finale && finale.podium">
    <p v-if="finale.preview" class="pill on" style="display: block; text-align: center">Forhåndsvisning. Bare admin ser dette før {{ revealDate }}.</p>
    <div class="finale-hero">
      <div class="star lit">★</div>
      <h1>Cantaras juleøl {{ finale.year }}</h1>
      <p v-if="finale.podium[0]" class="winner display">{{ finale.podium[0].name }}</p>
      <p v-if="finale.podium[0]" class="muted">{{ finale.podium[0].brewery }} · {{ finale.podium[0].bayesian.toFixed(2) }} av 6 · {{ finale.podium[0].ratings }} smakere</p>
    </div>

    <div class="podium" aria-label="Pallen">
      <div v-for="b in podium" :key="b.beerId" class="step" :class="`p${b.rank}`">
        <div class="label">
          <strong>{{ b.name }}</strong>
          <div class="muted">{{ b.bayesian.toFixed(2) }}</div>
        </div>
        <div class="block"><span class="display">{{ b.rank }}</span></div>
      </div>
    </div>
    <p v-if="!finale.podium.length" class="muted" style="text-align: center">Ingen terningkast i år. Pallen står tom.</p>

    <div v-if="finale.teamWinners?.length" class="card">
      <strong>Lagenes egne vinnere</strong>
      <div v-for="w in finale.teamWinners" :key="w.slug" class="row" style="margin-top: 6px">
        <span><span class="pill on">{{ w.team }}</span> {{ w.beerName }}<span v-if="w.brewery" class="muted"> · {{ w.brewery }}</span></span>
        <span class="muted">{{ w.bayesian.toFixed(2) }} · {{ w.ratings }} smakere</span>
      </div>
    </div>

    <button style="width: 100%; margin: 6px 0 14px" @click="share">Del resultatet</button>

    <h2>Duellene</h2>
    <div class="card">
      <table>
        <tbody>
          <tr v-for="d in finale.duelPodium" :key="d.beerId">
            <td>{{ d.rank }}</td><td>{{ d.name }}<div class="muted">{{ d.brewery }}</div></td><td style="text-align: right">{{ d.strength.toFixed(2) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!finale.duelPodium?.length" class="muted">Ingen dueller i år.</p>
    </div>

    <template v-if="pairs && (pairs.twins.length || pairs.opposites.length)">
      <h2>Smaksvillingene</h2>
      <div class="card">
        <p class="muted" style="margin-top: 0">Hvem sine terningkast fulgte hverandre, og hvem sine gikk hver sin vei. Minst {{ pairs.minCommon }} felles øl.</p>
        <table>
          <tbody>
            <tr v-for="p in pairs.twins" :key="'t' + p.a + p.b"><td>❤ {{ p.a }} og {{ p.b }}</td><td style="text-align: right">{{ p.similarity.toFixed(2) }} · {{ p.common }} øl</td></tr>
            <tr v-for="p in pairs.opposites" :key="'o' + p.a + p.b"><td>⚡ {{ p.a }} mot {{ p.b }}</td><td style="text-align: right">{{ p.similarity.toFixed(2) }} · {{ p.common }} øl</td></tr>
          </tbody>
        </table>
      </div>
    </template>

    <h2>Årets priser</h2>
    <div v-for="a in finale.awards" :key="a.id" class="card award" :class="{ none: !a.winnerName }">
      <strong class="display">{{ a.title }}</strong>
      <div class="muted">{{ a.description }}</div>
      <div class="who"><strong>{{ a.winnerName ?? 'Ingen i år' }}</strong><span class="muted"> · {{ a.detail }}</span></div>
    </div>

    <p class="muted" style="text-align: center; margin-top: 16px">
      {{ finale.totals.tastings }} terningkast · {{ finale.totals.beers }} øl · {{ finale.totals.people }} smakere. Takk for i år.
    </p>
  </template>
</template>
