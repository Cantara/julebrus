<script setup lang="ts">
/**
 * The blind pour at a table. The host pours one of the beers without saying which,
 * picks it here (only the host sees that form), and the guests guess from the options.
 * The answer comes out with the last guess or when the host reveals it.
 */
import { computed, ref, watch } from 'vue'
import { post } from '../api/client'
import type { BlindRound, Session } from '../api/types'

const props = defineProps<{ session: Session; rounds: BlindRound[]; myId: string; isHost: boolean }>()
const emit = defineEmits<{ changed: [] }>()

const MAX_OPTIONS = 6
const open = computed(() => props.rounds.find((r) => !r.revealed) ?? null)
const past = computed(() => props.rounds.filter((r) => r.revealed))
const guests = computed(() => props.session.members.filter((m) => m.userId !== props.session.hostId))
const canStart = computed(() => props.isHost && !props.session.closedAt && !open.value && props.session.beers.length >= 2 && guests.value.length >= 1)

const setup = ref(false)
const picked = ref<string[]>([])
const answer = ref('')
const error = ref('')
const busy = ref(false)
const showPast = ref(false)

/** Everything on the table, up to six. The host prunes from there. */
watch(setup, (on) => {
  if (!on) return
  picked.value = props.session.beers.slice(0, MAX_OPTIONS).map((b) => b.beerId)
  answer.value = ''
  error.value = ''
})

function toggle(beerId: string) {
  const i = picked.value.indexOf(beerId)
  if (i >= 0) picked.value.splice(i, 1)
  else if (picked.value.length < MAX_OPTIONS) picked.value.push(beerId)
  if (!picked.value.includes(answer.value)) answer.value = ''
}

const memberName = (id: string) => props.session.members.find((m) => m.userId === id)?.displayName ?? '?'
const optionName = (r: BlindRound, beerId?: string) => r.options.find((o) => o.beerId === beerId)?.name ?? '?'

async function call(fn: () => Promise<unknown>) {
  busy.value = true
  error.value = ''
  try {
    await fn()
    emit('changed')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}

const start = () => call(async () => {
  await post(`/api/sessions/${props.session.id}/blind`, { answerBeerId: answer.value, optionBeerIds: picked.value })
  setup.value = false
})
const guess = (round: BlindRound, beerId: string) => call(() => post(`/api/sessions/${props.session.id}/blind/${round.id}/guess`, { beerId }))
const reveal = (round: BlindRound) => call(() => post(`/api/sessions/${props.session.id}/blind/${round.id}/reveal`))

/** Rounds that flipped to revealed while we were watching get the little burst; not the ones already revealed when we arrived. */
const fresh = ref<Set<string>>(new Set())
let seen = new Set<string>()
let arrived = false
watch(() => props.rounds, (rounds) => {
  if (!arrived) {
    arrived = rounds.length > 0
    seen = new Set(rounds.filter((r) => r.revealed).map((r) => r.id))
    return
  }
  for (const r of rounds) {
    if (r.revealed && !seen.has(r.id)) {
      fresh.value = new Set([...fresh.value, r.id])
      window.setTimeout(() => {
        const next = new Set(fresh.value)
        next.delete(r.id)
        fresh.value = next
      }, 2600)
    }
  }
  seen = new Set(rounds.filter((r) => r.revealed).map((r) => r.id))
})

const hits = (r: BlindRound) => r.correct?.length ?? 0
const iGuessed = (r: BlindRound) => !!r.myGuess
const amHostOf = (r: BlindRound) => r.hostId === props.myId
</script>

<template>
  <template v-if="open || canStart || past.length">
    <h2>Blindsmaking</h2>
    <p v-if="error" class="error">{{ error }}</p>

    <!-- An open round -->
    <div v-if="open" class="card blind">
      <p class="muted" style="margin: 0 0 8px">
        {{ memberName(open.hostId) }} har skjenket ett av disse uten å si hvilket.
        <template v-if="amHostOf(open)">Bare du vet svaret.</template>
      </p>

      <template v-if="!amHostOf(open) && !iGuessed(open)">
        <strong>Hvilket øl er i glasset?</strong>
        <div class="options">
          <button v-for="o in open.options" :key="o.beerId" class="secondary" :disabled="busy" @click="guess(open, o.beerId)">
            {{ o.name }}<span v-if="o.brewery" class="muted"> · {{ o.brewery }}</span>
          </button>
        </div>
      </template>
      <p v-else-if="iGuessed(open)" style="margin: 0">Du gjettet <strong>{{ optionName(open, open.myGuess) }}</strong>. Svaret kommer når alle har gjettet.</p>

      <p class="muted" style="margin: 8px 0 0">
        {{ open.guessedBy.length }} av {{ guests.length }} har gjettet:
        <span v-for="uid in open.guessedBy" :key="uid" class="pill">{{ memberName(uid) }}</span>
      </p>
      <button v-if="amHostOf(open) && open.guessedBy.length" class="secondary" style="margin-top: 8px" :disabled="busy" @click="reveal(open)">Avslør nå</button>
    </div>

    <!-- The host's pour form -->
    <div v-else-if="canStart" class="card">
      <template v-if="!setup">
        <div class="row">
          <div>
            <strong>Skjenk blindt</strong>
            <div class="muted">Hell opp ett av ølene uten å si hvilket. De andre gjetter.</div>
          </div>
          <button class="secondary" @click="setup = true">Start</button>
        </div>
      </template>
      <template v-else>
        <label>Hvilke øl kan det være? (2–{{ MAX_OPTIONS }})</label>
        <div class="choices">
          <label v-for="b in session.beers" :key="b.beerId" class="choice">
            <input type="checkbox" :checked="picked.includes(b.beerId)" :disabled="!picked.includes(b.beerId) && picked.length >= MAX_OPTIONS" @change="toggle(b.beerId)" />
            <span>{{ b.name }}</span>
          </label>
        </div>
        <label style="margin-top: 10px">Og hva helte du egentlig i glasset? <span class="muted">(bare du ser dette)</span></label>
        <div class="choices">
          <label v-for="id in picked" :key="id" class="choice">
            <input type="radio" name="blind-answer" :value="id" v-model="answer" />
            <span>{{ session.beers.find((b) => b.beerId === id)?.name }}</span>
          </label>
        </div>
        <div class="row" style="margin-top: 10px">
          <button class="secondary" @click="setup = false">Avbryt</button>
          <button :disabled="busy || picked.length < 2 || !answer" @click="start">Skjenk</button>
        </div>
      </template>
    </div>

    <!-- Revealed rounds, newest first -->
    <template v-if="past.length">
      <div v-for="(r, i) in (showPast ? past : past.slice(0, 1))" :key="r.id" class="card blind reveal" :class="{ unwrap: fresh.has(r.id) }">
        <span class="paper" aria-hidden="true" />
        <span class="burst" aria-hidden="true"><i v-for="n in 10" :key="n" :style="{ '--a': `${n * 36}deg` }" /></span>
        <div class="row">
          <div>
            <span class="muted">Runde {{ past.length - i }} · i glasset var</span>
            <div class="display average" style="font-size: 1.2rem"><strong>{{ optionName(r, r.answerBeerId) }}</strong></div>
          </div>
          <span class="pill" :class="{ on: hits(r) > 0 }">{{ hits(r) }} av {{ r.guessedBy.length }} traff</span>
        </div>
        <div class="scores" style="margin-top: 6px">
          <span v-for="(beerId, uid) in r.guesses" :key="uid" class="pill score" :class="{ hit: r.correct?.includes(String(uid)) }">
            {{ r.correct?.includes(String(uid)) ? '✓' : '✗' }} {{ memberName(String(uid)) }}: {{ optionName(r, beerId) }}
          </span>
          <span v-if="!r.guessedBy.length" class="muted">Ingen rakk å gjette.</span>
        </div>
      </div>
      <button v-if="past.length > 1" class="link" @click="showPast = !showPast">{{ showPast ? 'Skjul tidligere runder' : `Vis alle ${past.length} runder` }}</button>
    </template>
  </template>
</template>

<style scoped>
.options { display: grid; gap: 8px; margin-top: 8px; }
.options button { text-align: left; }
.choices { display: grid; gap: 4px; margin-top: 4px; }
.choice { display: flex; align-items: center; gap: 8px; font-weight: normal; }
.choice input { width: auto; margin: 0; }
.pill.hit { background: var(--candle); color: #2b1a14; }
.link { background: none; border: none; color: var(--brand); padding: 4px 0; cursor: pointer; font: inherit; }
</style>
