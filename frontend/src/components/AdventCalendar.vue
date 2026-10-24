<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * The season as an advent calendar: one door per day from start to end.
 * Doors you tasted behind are filled, one hue light to dark by how many beers
 * (a sequential encoding, 1..5). Today glows, future doors are shut.
 */
export interface DayEntry {
  beers: { name: string; score: number }[]
  solo: boolean
}

const props = defineProps<{
  start: string
  end: string
  today: string
  days: Record<string, DayEntry>
}>()

const WEEKDAYS = ['ma', 'ti', 'on', 'to', 'fr', 'lø', 'sø']
const MONTHS = ['jan', 'feb', 'mar', 'apr', 'mai', 'jun', 'jul', 'aug', 'sep', 'okt', 'nov', 'des']

const selected = ref<string | null>(null)

function iso(d: Date) {
  return d.toISOString().slice(0, 10)
}

const cells = computed(() => {
  const out: ({ kind: 'pad' } | { kind: 'day'; date: string; dom: number; month: string | null })[] = []
  const start = new Date(props.start + 'T00:00:00Z')
  const end = new Date(props.end + 'T00:00:00Z')
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
    [`c${n}`]: n > 0,
    selected: selected.value === date,
    finale: date === props.end,
    month: true,
  }
}

function title(date: string) {
  const entry = props.days[date]
  if (date > props.today) return 'Ikke ennå'
  if (!entry) return 'Ingen smaking'
  return entry.beers.map((b) => `${b.name} (${b.score})`).join(', ')
}

function toggle(date: string) {
  if (!props.days[date]) return
  selected.value = selected.value === date ? null : date
}

const detail = computed(() => (selected.value ? props.days[selected.value] : null))
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
        :tabindex="days[c.date] ? 0 : -1"
        @click="toggle(c.date)"
      >
        {{ c.dom }}
        <span v-if="days[c.date]" class="n">{{ days[c.date].beers.length }}</span>
      </button>
    </template>
  </div>

  <div v-if="detail" class="card" style="margin-top: 10px">
    <div class="row">
      <strong class="display">{{ detailDate }}</strong>
      <span v-if="detail.solo" class="pill">solo-dag</span>
    </div>
    <table>
      <tbody>
        <tr v-for="b in detail.beers" :key="b.name">
          <td>{{ b.name }}</td>
          <td style="text-align: right"><strong>{{ b.score }}</strong></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
