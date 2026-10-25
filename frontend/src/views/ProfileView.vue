<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { get, logout, post } from '../api/client'
import PushSettings from '../components/PushSettings.vue'
import TeamPicker from '../components/TeamPicker.vue'
import DumbbellChart from '../components/DumbbellChart.vue'
import LiftChart from '../components/LiftChart.vue'
import ClaimHistory from '../components/ClaimHistory.vue'
import LangToggle from '../components/LangToggle.vue'
import { t } from '../i18n'
import type { BadgeView, Beer, HistoricRating, MySeason, Palate, Tasting } from '../api/types'
import BadgeTree from '../components/BadgeTree.vue'
import DiceFace from '../components/DiceFace.vue'
import { useMe } from '../stores/me'

const store = useMe()
const router = useRouter()
const confirmingWithdraw = ref(false)
const editingTeams = ref(false)
const badges = ref<BadgeView[]>([])
const mine = ref<Tasting[]>([])
const history = ref<HistoricRating[]>([])
const mySeasons = ref<MySeason[]>([])
const palate = ref<Palate | null>(null)
const beerNames = ref<Record<string, string>>({})

onMounted(async () => {
  const [b, t, h, beers] = await Promise.all([
    get<BadgeView[]>('/api/badges/me'),
    get<Tasting[]>('/api/tastings/mine'),
    get<HistoricRating[]>('/api/history/me').catch(() => []),
    get<Beer[]>('/api/beers'),
    get<MySeason[]>('/api/history/me/summary').catch(() => []).then((r) => (mySeasons.value = r)),
    get<Palate>('/api/stats/palate/me').catch(() => null).then((r) => (palate.value = r)),
  ])
  badges.value = b
  mine.value = t
  history.value = h
  beerNames.value = Object.fromEntries(beers.map((x) => [x.id, x.name]))
})

async function reloadHistory() {
  history.value = await get<HistoricRating[]>('/api/history/me').catch(() => [])
  mySeasons.value = await get<MySeason[]>('/api/history/me/summary').catch(() => [])
}

const withdrawFailed = ref(false)
async function withdrawConsent() {
  withdrawFailed.value = false
  try {
    await post('/api/consent/withdraw')
    await store.refresh()
    router.push('/consent')
  } catch {
    withdrawFailed.value = true
  }
}

const byYear = () => {
  const m = new Map<number, HistoricRating[]>()
  for (const h of history.value) m.set(h.year, [...(m.get(h.year) ?? []), h])
  return [...m.entries()].sort((a, b) => b[0] - a[0])
}
</script>

<template>
  <h1>{{ store.me?.displayName ?? 'Meg' }}</h1>
  <p class="muted">{{ store.me?.email }} · <a href="#" @click.prevent="logout()">Logg ut</a></p>

  <div v-if="store.me?.weight" class="card">
    <div class="row">
      <strong>Din vekt i år</strong>
      <span class="pill on">{{ store.me.weight.weight }}</span>
    </div>
    <p class="muted" style="margin: 6px 0 0">{{ store.me.weight.reason }}</p>
    <p class="muted" style="margin: 6px 0 0">Nye starter på 0,5. En vanlig sesong med minst sju terningkast gir 1,0 neste år. Juks gir 0, men du kan fortsatt smake og stemme.</p>
  </div>

  <div class="card">
    <div class="row">
      <div>
        <strong>Lag</strong>
        <div v-if="store.me?.teams.length"><router-link v-for="t in store.me.teams" :key="t.id" class="pill on" :to="`/lag/${t.slug}`" style="text-decoration: none">{{ t.name }} ›</router-link></div>
        <div v-else class="muted">Du spiller ikke for noe lag ennå.</div>
      </div>
      <button v-if="!editingTeams" class="secondary" @click="editingTeams = true">{{ store.me?.teams.length ? 'Endre' : 'Velg lag' }}</button>
    </div>
    <div v-if="editingTeams" style="margin-top: 10px">
      <TeamPicker @done="editingTeams = false" />
    </div>
  </div>

  <PushSettings />

  <h2>Merker</h2>
  <div class="card">
    <BadgeTree :badges="badges" />
    <details>
      <summary class="muted" style="cursor: pointer">Alle merker som liste</summary>
      <div v-for="b in badges" :key="b.id" class="row" style="padding: 6px 0" :style="{ opacity: b.earned ? 1 : 0.5 }">
        <div><strong>{{ b.earned ? '★' : '☆' }} {{ b.title }}</strong><div class="muted">{{ b.description }}</div></div>
      </div>
    </details>
  </div>

  <h2>Ganen din</h2>
  <div class="card">
    <template v-if="palate && palate.tastings >= 3">
      <p class="muted" style="margin-top: 0">{{ palate.tastings }} terningkast i år, snitt {{ palate.mean.toFixed(2) }}.</p>
      <template v-if="palate.likes.length || palate.dislikes.length">
        <strong class="display">Stikkordene dine</strong>
        <p class="muted" style="margin: 2px 0 6px">Hvor mye over eller under ditt eget snitt du gir øl med hvert stikkord.</p>
        <LiftChart :rows="[...palate.likes, ...palate.dislikes]" />
      </template>
      <p v-else class="muted">Sett stikkord på smakingene dine, så tegnes fingeravtrykket her.</p>

      <div v-if="palate.twin" class="row" style="margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--line)">
        <div>
          <strong class="display">Smaksvillingen din: {{ palate.twin.displayName }}</strong>
          <div class="muted">
            {{ palate.twin.common }} felles øl · likhet {{ palate.twin.similarity.toFixed(2) }}
            <template v-if="palate.twin.beerName"> · mest enige om {{ palate.twin.beerName }} ({{ palate.twin.yourScore }} og {{ palate.twin.theirScore }})</template>
          </div>
        </div>
      </div>
      <div v-if="palate.opposite" class="row" style="margin-top: 8px">
        <div>
          <strong class="display">Smaksmotsatsen din: {{ palate.opposite.displayName }}</strong>
          <div class="muted">
            {{ palate.opposite.common }} felles øl · likhet {{ palate.opposite.similarity.toFixed(2) }}
            <template v-if="palate.opposite.beerName"> · mest uenige om {{ palate.opposite.beerName }} ({{ palate.opposite.yourScore }} mot {{ palate.opposite.theirScore }})</template>
          </div>
        </div>
      </div>
      <p v-if="!palate.twin" class="muted" style="margin: 10px 0 0">Tvillingen dukker opp når du og noen andre har {{ palate.minCommon }} øl felles.</p>
    </template>
    <p v-else class="muted" style="margin: 0">Etter tre terningkast begynner ganen din å ta form her: stikkordene du liker, og hvem i Cantara du smaker likt med.</p>
  </div>

  <h2>Mine smakinger i år</h2>
  <div class="card">
    <table>
      <tbody>
        <tr v-for="t in mine" :key="t.id">
          <td><router-link class="lnk" :to="`/beers/${t.beerId}`">{{ beerNames[t.beerId] ?? '?' }}</router-link><div v-if="t.tags.length" class="muted">{{ t.tags.join(', ') }}</div></td>
          <td style="text-align: right"><DiceFace :value="t.score" :size="26" /></td>
        </tr>
      </tbody>
    </table>
    <p v-if="!mine.length" class="muted">Ingenting ennå.</p>
  </div>

  <h2>Mine historiske scorer</h2>
  <div v-if="mySeasons.length" class="card">
    <strong class="display">Deg mot flokken, år for år</strong>
    <p class="muted" style="margin: 2px 0 8px">Ditt snitt og sesongens vektede snitt, på 1–6.</p>
    <DumbbellChart :rows="mySeasons.map((s) => ({ label: String(s.year), a: s.myMean, b: s.crowdMean }))" a-name="Deg" b-name="Alle" />
    <p class="muted" style="margin: 6px 0 0"><router-link to="/historikk">Alle årene, med resultatlister og vinnere</router-link></p>
  </div>
  <p class="muted">Fra tidligere års smakinger, med den vektingen som gjaldt da.</p>
  <div v-for="[year, rows] in byYear()" :key="year" class="card">
    <strong>{{ year }}</strong>
    <table>
      <thead><tr><th>Øl</th><th>Score</th><th>Vekt</th><th>På 1–6</th></tr></thead>
      <tbody>
        <tr v-for="(r, i) in rows" :key="`${r.year}-${r.beerName}-${r.raterName}-${i}`">
          <td><router-link class="lnk" :to="`/historikk/ol/${encodeURIComponent(r.beerName)}`">{{ r.beerName }}</router-link><div v-if="r.notes" class="muted">{{ r.notes }}</div></td>
          <td>{{ r.score }}</td><td>{{ r.weight }}</td><td>{{ r.normalised.toFixed(1) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-if="!history.length" class="muted">Ingen historikk koblet til {{ store.me?.email }} ennå.</p>

  <ClaimHistory @changed="reloadHistory" />
  <router-link v-if="store.me?.admin" class="card row" to="/admin" style="text-decoration: none">
    <div>
      <strong class="display">Admin</strong>
      <div class="muted">Nye øl, bilder, import, lag, krav og koblinger til tidligere år.</div>
    </div>
    <span class="muted">›</span>
  </router-link>

  <h2>{{ t('withdrawTitle') }}</h2>
  <div class="card">
    <p class="muted" style="margin-top: 0">{{ t('withdrawInfo') }}</p>
    <div class="row" style="justify-content: flex-start">
      <LangToggle />
    </div>
    <button v-if="!confirmingWithdraw" class="secondary" style="margin-top: 12px" @click="confirmingWithdraw = true">{{ t('withdrawButton') }}</button>
    <div v-else style="margin-top: 12px">
      <p class="error" role="alert">{{ t('withdrawConfirm') }}</p>
      <p v-if="withdrawFailed" class="error" role="alert">{{ t('claimError') }}</p>
      <div class="row" style="justify-content: flex-start">
        <button @click="withdrawConsent">{{ t('withdrawYes') }}</button>
        <button class="secondary" @click="confirmingWithdraw = false">{{ t('cancel') }}</button>
      </div>
    </div>
  </div>
</template>
