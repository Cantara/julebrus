<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { get, post } from '../api/client'
import type { Beer, DuelPair, Session } from '../api/types'
import QrCode from '../components/QrCode.vue'
import { useMe } from '../stores/me'

const props = defineProps<{ id: string }>()
const store = useMe()

const session = ref<Session | null>(null)
const catalogue = ref<Beer[]>([])
const q = ref('')
const pending = ref<DuelPair[]>([])
const error = ref('')

const isHost = computed(() => session.value && store.me && session.value.hostId === store.me.id)
const joinUrl = computed(() => `${location.origin}/join/${session.value?.joinCode ?? ''}`)
const myId = computed(() => store.me?.id)

const beerName = (id: string) => session.value?.beers.find((b) => b.beerId === id)?.name ?? '?'
const memberName = (id: string) => session.value?.members.find((m) => m.userId === id)?.displayName ?? '?'

const filtered = computed(() => {
  const inSession = new Set(session.value?.beers.map((b) => b.beerId))
  const needle = q.value.toLowerCase()
  return catalogue.value
    .filter((b) => !inSession.has(b.id))
    .filter((b) => !needle || b.name.toLowerCase().includes(needle) || (b.brewery ?? '').toLowerCase().includes(needle))
    .slice(0, 8)
})

async function refresh() {
  session.value = await get<Session>(`/api/sessions/${props.id}`)
  pending.value = await get<DuelPair[]>(`/api/duels/pending/${props.id}`).catch(() => [])
}

async function addBeer(beerId: string) {
  await post(`/api/sessions/${props.id}/beers`, { beerId })
  q.value = ''
  await refresh()
}

async function answerDuel(winner: string, loser: string) {
  await post('/api/duels', { sessionId: props.id, winnerBeerId: winner, loserBeerId: loser })
  await refresh()
}

async function close() {
  await post(`/api/sessions/${props.id}/close`)
  await refresh()
}

let timer: number | undefined
onMounted(async () => {
  try {
    await refresh()
    catalogue.value = await get<Beer[]>('/api/beers')
  } catch (e) {
    error.value = (e as Error).message
  }
  // Cheap polling keeps everyone's reveal state in sync around the table.
  timer = window.setInterval(() => refresh().catch(() => {}), 5000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <p v-if="error" class="error">{{ error }}</p>
  <template v-if="session">
    <h1>Smaking {{ session.closedAt ? '(avsluttet)' : '' }}</h1>

    <div v-if="!session.closedAt" class="card" style="text-align: center">
      <QrCode :value="joinUrl" :size="220" />
      <p class="code" style="margin: 10px 0 0">{{ session.joinCode }}</p>
      <p class="muted">Skann eller skriv inn koden for å bli med</p>
    </div>

    <div class="card">
      <strong>Rundt bordet</strong>
      <div><span v-for="m in session.members" :key="m.userId" class="pill" :class="{ on: m.userId === myId }">{{ m.displayName }}</span></div>
      <p v-if="session.solo" class="muted">Du er alene. Dette teller som en solo-dag til noen blir med.</p>
    </div>

    <h2>Øl på bordet</h2>
    <div v-for="b in session.beers" :key="b.beerId" class="card">
      <div class="row">
        <div>
          <strong>{{ b.name }}</strong>
          <div class="muted">{{ b.brewery }}</div>
        </div>
        <router-link v-if="myId && !b.ratedBy.includes(myId) && !session.closedAt" class="btn" :to="`/beers/${b.beerId}/rate?session=${session.id}`">Gi terningkast</router-link>
      </div>
      <template v-if="b.revealed && b.scores">
        <p class="ok" style="margin: 8px 0 4px">Snitt {{ b.average?.toFixed(2) }}</p>
        <div><span v-for="(s, uid) in b.scores" :key="uid" class="pill">{{ memberName(String(uid)) }}: {{ s }}</span></div>
      </template>
      <p v-else class="muted" style="margin: 8px 0 0">
        {{ b.ratedBy.length }} av {{ session.members.length }} har svart. Scorene vises når alle er ferdige.
      </p>
    </div>

    <div v-if="!session.closedAt" class="card">
      <label>Legg et øl på bordet</label>
      <input v-model="q" placeholder="Søk i katalogen" />
      <div v-for="b in filtered" :key="b.id" class="row" style="padding: 8px 0; border-bottom: 1px solid var(--line)">
        <span>{{ b.name }} <span class="muted">{{ b.brewery }}</span></span>
        <button class="secondary" @click="addBeer(b.id)">Legg til</button>
      </div>
      <p class="muted" style="margin-top: 8px">Mangler ølet? <router-link to="/beers">Legg det til i katalogen.</router-link></p>
    </div>

    <template v-if="pending.length">
      <h2>Duell</h2>
      <p class="muted">Hvilket var best? Dette lager sideranking uavhengig av hvor snill du er med terningen.</p>
      <div v-for="p in pending" :key="p.beerA + p.beerB" class="card row">
        <button class="secondary" style="flex: 1" @click="answerDuel(p.beerA, p.beerB)">{{ beerName(p.beerA) }}</button>
        <span class="muted">vs</span>
        <button class="secondary" style="flex: 1" @click="answerDuel(p.beerB, p.beerA)">{{ beerName(p.beerB) }}</button>
      </div>
    </template>

    <button v-if="isHost && !session.closedAt" class="secondary" style="width: 100%; margin-top: 12px" @click="close">Avslutt smakingen</button>
  </template>
</template>
