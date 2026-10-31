<script setup lang="ts">
import { computed, ref } from 'vue'
import DiceFace from './DiceFace.vue'

/**
 * The season as an advent calendar: one door per day from start to end.
 * Doors you tasted behind are filled, one hue light to dark by how many beers
 * (a sequential encoding, 1..5). A door you opened (answered its question) gets
 * a small star; today's shut door glows until you open it. Future doors are shut.
 */
export interface DayEntry {
  beers: { id?: string; name: string; score: number }[]
  solo: boolean
}

const props = defineProps<{
  start: string
  end: string
  today: string
  days: Record<string, DayEntry>
  /** only the week that holds today (Monday to Sunday), still on the 7-column grid */
  compact?: boolean
  /** advent doors opened, by day: true answered right, false wrong, null without a question */
  opened?: Record<string, boolean | null>
}>()

const WEEKDAYS = ['ma', 'ti', 'on', 'to', 'fr', 'lø', 'sø']
const MONTHS = ['jan', 'feb', 'mar', 'apr', 'mai', 'jun', 'jul', 'aug', 'sep', 'okt', 'nov', 'des']

const emit = defineEmits<{ finale: []; door: [day: string] }>()
const isOpened = (date: string) => !!props.opened && date in props.opened
const selected = ref<string | null>(null)

function iso(d: Date) {
  return d.toISOString().slice(0, 10)
}

const cells = computed(() => {
  const out: ({ kind: 'pad' } | { kind: 'day'; date: string; dom: number; month: string | null })[] = []
  const start = new Date(props.start + 'T00:00:00Z')
  const end = new Date(props.end + 'T00:00:00Z')
  if (props.compact) {
    // The week around today, clipped to the season; pads keep the weekday columns.
    const anchor = new Date((props.today < props.start ? props.start : props.today > props.end ? props.end : props.today) + 'T00:00:00Z')
    const monday = new Date(anchor)
    monday.setUTCDate(anchor.getUTCDate() - ((anchor.getUTCDay() + 6) % 7))
    for (let i = 0; i < 7; i++) {
      const d = new Date(monday)
      d.setUTCDate(monday.getUTCDate() + i)
      if (d < start || d > end) out.push({ kind: 'pad' })
      else out.push({ kind: 'day', date: iso(d), dom: d.getUTCDate(), month: i === 0 || d.getUTCDate() === 1 ? MONTHS[d.getUTCMonth()] : null })
    }
    return out
  }
  const lead = (start.getUTCDay() + 6) % 7 // Monday first
  for (let i = 0; i < lead; i++) out.push({ kind: 'pad' })
  for (let d = new Date(start); d <= end; d.setUTCDate(d.getUTCDate() + 1)) {
    out.push({
      kind: 'day',
      date: iso(d),
      dom: d.getUTCDate(),
      month: d.getUTCDate() === 1 || iso(d) === props.start ? MONTHS[d.getUTCMonth()] : null,
    })
  }
  return out
})

function classes(date: string) {
  const entry = props.days[date]
  const n = entry ? Math.min(entry.beers.length, 5) : 0
  return {
    shut: date > props.today,
    today: date === props.today,
    tasted: n > 0,
    opened: isOpened(date),
    waiting: date === props.today && !!props.opened && !isOpened(date),
    [`c${n}`]: n > 0,
    selected: selected.value === date,
    finale: date === props.end,
    month: true,
  }
}

function title(date: string) {
  const entry = props.days[date]
  if (date > props.today) return date === props.end ? 'Finalen' : 'Ikke ennå'
  const door = isOpened(date) ? 'Luka er åpnet' : 'Luka er ikke åpnet'
  if (!entry) return door
  return `${door}. ${entry.beers.map((b) => `${b.name} (${b.score})`).join(', ')}`
}

function toggle(date: string) {
  if (date > props.today) {
    if (date === props.end) emit('finale')
    return
  }
  // A shut door with nothing behind it goes straight to its question; anything else unfolds first.
  if (!props.days[date] && !isOpened(date)) {
    emit('door', date)
    return
  }
  selected.value = selected.value === date ? null : date
}

const detail = computed(() => (selected.value ? props.days[selected.value] ?? { beers: [], solo: false } : null))
const detailOpened = computed(() => (selected.value ? isOpened(selected.value) : false))
const detailCorrect = computed(() => (selected.value && props.opened ? props.opened[selected.value] : null))
const detailDate = computed(() => {
  if (!selected.value) return ''
  const d = new Date(selected.value + 'T00:00:00Z')
  return `${d.getUTCDate()}. ${MONTHS[d.getUTCMonth()]}`
})
</script>

<template>
  <div class="advent" role="grid" aria-label="Adventskalender">
    <div v-for="w in WEEKDAYS" :key="w" class="wd" role="columnheader">{{ w }}</div>
    <template v-for="(c, i) in cells" :key="i">
      <span v-if="c.kind === 'pad'" aria-hidden="true" />
      <button
        v-else
        type="button"
        class="door"
        role="gridcell"
        :class="classes(c.date)"
        :data-month="c.month ?? ''"
        :title="title(c.date)"
        :aria-label="`${c.dom}. ${c.month ?? ''}: ${title(c.date)}`"
        :tabindex="c.date <= today || c.date === end ? 0 : -1"
        @click="toggle(c.date)"
      >
        {{ c.dom }}
        <span v-if="isOpened(c.date)" class="o" aria-hidden="true">✦</span>
        <span v-if="days[c.date]" class="n">{{ days[c.date].beers.length }}</span>
      </button>
    </template>
  </div>

  <div v-if="detail" class="card" style="margin-top: 10px">
    <div class="row">
      <strong class="display">{{ detailDate }}</strong>
      <span v-if="detail.solo" class="pill">solo-dag</span>
    </div>
    <p class="muted" style="margin: 4px 0 6px">
      <template v-if="detailOpened">Luka er åpnet<template v-if="detailCorrect === true">, riktig svar</template><template v-else-if="detailCorrect === false">, feil svar</template>.</template>
      <template v-else>Luka er ikke åpnet ennå.</template>
      <router-link class="lnk" :to="`/luke/${selected}`">{{ detailOpened ? 'Se luka' : 'Åpne luka' }}</router-link>
    </p>
    <table v-if="detail.beers.length">
      <tbody>
        <tr v-for="b in detail.beers" :key="b.name">
          <td><router-link v-if="b.id" class="lnk" :to="`/beers/${b.id}`">{{ b.name }}</router-link><template v-else>{{ b.name }}</template></td>
          <td style="text-align: right"><DiceFace :value="b.score" :size="26" /></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
