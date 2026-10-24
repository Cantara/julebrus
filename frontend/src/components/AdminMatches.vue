<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiFailure, get, post } from '../api/client'
import type { BulkResult, MatchCandidate, MatchPage, MatchStatus, MatchSummary, MatchView, ProductHit } from '../api/types'
import { t, type Key } from '../i18n'

/**
 * Review of the machine matches between the tasting votes (1998-2019) and Vinmonopolet's beers.
 * Every button is a plain button and the search box is a plain input, so Tab / Enter / Space do everything.
 * Decisions are saved at once; the row stays where it is (with an Undo) until the list is next loaded.
 */
const PAGE_SIZE = 25
const STATUSES: MatchStatus[] = ['review', 'none', 'likely', 'auto', 'not-a-product']
const YEARS = Array.from({ length: 2019 - 1998 + 1 }, (_, i) => 1998 + i)

const items = ref<MatchView[]>([])
const summary = ref<MatchSummary | null>(null)
const page = ref(0)
const totalPages = ref(0)
const totalItems = ref(0)
const status = ref('')
const year = ref('')
const undecidedOnly = ref(true)
const busy = ref(false)
const error = ref('')
const message = ref('')

const percent = computed(() => (summary.value && summary.value.total ? Math.round((100 * summary.value.decided) / summary.value.total) : 0))

function query(p: number, size = PAGE_SIZE): string {
  const q = new URLSearchParams({ page: String(p), size: String(size) })
  if (status.value) q.set('status', status.value)
  if (year.value) q.set('year', year.value)
  if (undecidedOnly.value) q.set('decided', 'false')
  return q.toString()
}

async function load(p = page.value) {
  error.value = ''
  try {
    const r = await get<MatchPage>(`/api/admin/matches?${query(p)}`)
    items.value = r.items
    summary.value = r.summary
    page.value = r.page
    totalPages.value = r.totalPages
    totalItems.value = r.totalItems
  } catch (e) {
    error.value = `${t('matchLoadError')}${e instanceof ApiFailure ? ` (${e.code})` : ''}`
  }
}
onMounted(() => load(0))

function refilter() {
  closeSearch()
  bulkAsk.value = null
  load(0)
}

async function refreshSummary() {
  try {
    summary.value = (await get<MatchPage>('/api/admin/matches?size=1')).summary
  } catch {
    /* the numbers catch up on the next load */
  }
}

// --- decisions

type Body = { action: 'CONFIRM' | 'CHOOSE' | 'NONE' | 'NOT_A_PRODUCT' | 'CLEAR'; articleNo?: string; productName?: string }

async function decide(row: MatchView, body: Body) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    const updated = await post<MatchView>(`/api/admin/matches/${row.id}/decision`, { ...body, version: row.version })
    const i = items.value.findIndex((r) => r.id === row.id)
    if (i >= 0) items.value[i] = updated
    message.value = body.action === 'CLEAR' ? t('matchUndone') : t('matchSaved')
    closeSearch()
    await refreshSummary()
  } catch (e) {
    if (e instanceof ApiFailure && e.status === 409) {
      error.value = t('matchConflict')
      await load()
    } else {
      error.value = `${t('matchError')}${e instanceof ApiFailure ? ` (${e.code})` : ''}`
    }
  } finally {
    busy.value = false
  }
}

function choose(row: MatchView, c: { name: string; articleNo: string | null }) {
  return decide(row, { action: 'CHOOSE', productName: c.name, ...(c.articleNo ? { articleNo: c.articleNo } : {}) })
}
const candidateLabel = (c: MatchCandidate) => (c.score != null ? `${c.name} (${c.score.toFixed(2)})` : c.name)

// --- type-ahead for "another beer"

const searchRow = ref<string | null>(null)
const searchText = ref('')
const hits = ref<ProductHit[]>([])
const searched = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
let ticket = 0

function closeSearch() {
  searchRow.value = null
  searchText.value = ''
  hits.value = []
  searched.value = false
  clearTimeout(timer)
}

function onSearch(row: MatchView, value: string) {
  searchRow.value = row.id
  searchText.value = value
  clearTimeout(timer)
  if (value.trim().length < 2) {
    hits.value = []
    searched.value = false
    return
  }
  const mine = ++ticket
  timer = setTimeout(async () => {
    try {
      const r = await get<ProductHit[]>(`/api/admin/products/search?q=${encodeURIComponent(value.trim())}`)
      if (mine === ticket) {
        hits.value = r
        searched.value = true
      }
    } catch (e) {
      if (mine === ticket) error.value = `${t('matchLoadError')}${e instanceof ApiFailure ? ` (${e.code})` : ''}`
    }
  }, 250)
}

function searchEnter(row: MatchView) {
  if (searchRow.value === row.id && hits.value.length) choose(row, hits.value[0])
}

// --- accept a whole year

const bulkYear = ref('')
const bulkStatus = ref<'likely' | 'auto'>('likely')
const bulkAsk = ref<BulkResult | null>(null)
const bulkNone = ref(false)

async function bulkPreview() {
  error.value = ''
  message.value = ''
  bulkNone.value = false
  bulkAsk.value = null
  if (!bulkYear.value) {
    error.value = t('matchPickYear')
    return
  }
  try {
    const r = await post<BulkResult>('/api/admin/matches/bulk', {
      action: 'CONFIRM', year: Number(bulkYear.value), status: bulkStatus.value, dryRun: true,
    })
    if (r.count === 0) bulkNone.value = true
    else bulkAsk.value = r
  } catch (e) {
    error.value = `${t('matchError')}${e instanceof ApiFailure ? ` (${e.code})` : ''}`
  }
}

async function bulkApply() {
  const ask = bulkAsk.value
  if (!ask || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const r = await post<BulkResult>('/api/admin/matches/bulk', {
      action: 'CONFIRM', year: ask.year, status: ask.status, dryRun: false, expectedCount: ask.count,
    })
    message.value = `${t('matchBulkDone')} ${r.count}.`
    bulkAsk.value = null
    await load()
  } catch (e) {
    if (e instanceof ApiFailure && e.status === 409) {
      error.value = t('matchConflict')
      bulkAsk.value = null
      await load()
    } else {
      error.value = `${t('matchError')}${e instanceof ApiFailure ? ` (${e.code})` : ''}`
    }
  } finally {
    busy.value = false
  }
}

const statusLabel = (s: string) => t(`status-${s}` as Key)
const decisionLabel = (k: string) => t(`decision-${k}` as Key)
</script>

<template>
  <h2>{{ t('matchTitle') }}</h2>
  <div class="card">
    <p class="muted" style="margin-top: 0">{{ t('matchIntro') }}</p>
    <div v-if="summary">
      <div class="row">
        <strong>{{ t('matchProgress') }}: {{ summary.decided }} {{ t('matchOf') }} {{ summary.total }}</strong>
        <span class="muted">{{ percent }} %</span>
      </div>
      <div class="meter" role="progressbar" :aria-valuenow="summary.decided" aria-valuemin="0" :aria-valuemax="summary.total"
           :aria-label="t('matchProgress')">
        <span :style="{ width: percent + '%' }" />
      </div>
      <p style="margin: 8px 0 0">
        <span v-for="s in STATUSES" :key="s" class="pill" :class="{ on: summary.byStatus[s]?.undecided }">
          {{ statusLabel(s) }}: {{ summary.byStatus[s]?.undecided ?? 0 }} {{ t('matchUndecided') }} / {{ summary.byStatus[s]?.total ?? 0 }}
        </span>
      </p>
    </div>
    <p style="margin-bottom: 0">
      <a class="btn secondary" href="/api/admin/matches/overrides.csv" download="vote_match_overrides.csv">{{ t('matchDownload') }}</a>
    </p>
    <p class="muted" style="margin-bottom: 0">{{ t('matchDownloadHint') }}</p>
  </div>

  <div class="card">
    <div class="row" style="align-items: flex-end">
      <div style="flex: 1">
        <label for="m-status">{{ t('matchFilterStatus') }}</label>
        <select id="m-status" v-model="status" @change="refilter">
          <option value="">{{ t('matchAll') }}</option>
          <option v-for="s in STATUSES" :key="s" :value="s">{{ statusLabel(s) }}</option>
        </select>
      </div>
      <div style="flex: 1">
        <label for="m-year">{{ t('matchFilterYear') }}</label>
        <select id="m-year" v-model="year" @change="refilter">
          <option value="">{{ t('matchAll') }}</option>
          <option v-for="y in YEARS" :key="y" :value="String(y)">{{ y }}</option>
        </select>
      </div>
    </div>
    <label style="display: flex; gap: 8px; align-items: center; color: var(--ink)">
      <input v-model="undecidedOnly" type="checkbox" style="width: auto" @change="refilter" />
      {{ t('matchUndecidedOnly') }}
    </label>

    <h3 style="margin-bottom: 4px">{{ t('matchBulkTitle') }}</h3>
    <div class="row" style="align-items: flex-end; justify-content: flex-start; flex-wrap: wrap">
      <div>
        <label for="b-year">{{ t('matchFilterYear') }}</label>
        <select id="b-year" v-model="bulkYear" @change="bulkAsk = null; bulkNone = false">
          <option value="">–</option>
          <option v-for="y in YEARS" :key="y" :value="String(y)">{{ y }}</option>
        </select>
      </div>
      <div>
        <label for="b-status">{{ t('matchFilterStatus') }}</label>
        <select id="b-status" v-model="bulkStatus" @change="bulkAsk = null; bulkNone = false">
          <option value="likely">{{ statusLabel('likely') }}</option>
          <option value="auto">{{ statusLabel('auto') }}</option>
        </select>
      </div>
      <button class="secondary" :disabled="busy" @click="bulkPreview">{{ t('matchBulkPreview') }}</button>
    </div>
    <div v-if="bulkAsk" class="row" style="margin-top: 8px; justify-content: flex-start; flex-wrap: wrap">
      <span>{{ t('matchBulkAsk') }} {{ bulkAsk.count }} {{ t('matchBulkEntries') }} {{ bulkAsk.year }} ({{ statusLabel(bulkAsk.status) }})?</span>
      <button :disabled="busy" @click="bulkApply">{{ t('matchBulkYes') }}</button>
      <button class="secondary" @click="bulkAsk = null">{{ t('cancel') }}</button>
    </div>
    <p v-if="bulkNone" class="muted">{{ t('matchBulkNone') }}</p>
  </div>

  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <p class="ok" role="status" aria-live="polite">{{ message }}</p>
  <p v-if="summary && !items.length && !error" class="muted">{{ t('matchEmpty') }}</p>

  <div v-for="row in items" :key="row.id" class="card" :aria-label="`${row.year} ${row.voteBeer}`">
    <div class="row" style="align-items: flex-start">
      <strong>{{ row.year }} · {{ row.voteBeer }}</strong>
      <span>
        <span class="pill">{{ statusLabel(row.machine.status) }}</span>
        <span v-if="row.decision" class="pill on">{{ decisionLabel(row.decision.kind) }}</span>
      </span>
    </div>

    <p v-if="row.decision && (row.decision.productName || row.decision.articleNo)" class="ok" style="margin: 6px 0">
      {{ row.decision.productName }}<template v-if="row.decision.articleNo"> · {{ t('matchArticle') }} {{ row.decision.articleNo }}</template>
    </p>

    <p class="muted" style="margin: 6px 0">
      {{ t('matchGuess') }}:
      <template v-if="row.machine.productName">
        <strong style="color: var(--ink)">{{ row.machine.productName }}</strong>
        <template v-if="row.machine.articleNo"> · {{ t('matchArticle') }} {{ row.machine.articleNo }}</template>
        <template v-else-if="row.machine.articleOptions.length"> · {{ t('matchArticles') }} {{ row.machine.articleOptions.join(', ') }}</template>
        <template v-if="row.machine.score != null"> · {{ t('matchScore') }} {{ row.machine.score.toFixed(2) }}</template>
      </template>
      <template v-else>{{ t('matchNoGuess') }}</template>
    </p>

    <div v-if="row.candidates.length">
      <div class="muted">{{ t('matchCandidates') }}</div>
      <div class="row" style="flex-wrap: wrap; justify-content: flex-start; gap: 6px; margin-top: 4px">
        <button v-for="(c, i) in row.candidates" :key="i" class="secondary" style="padding: 6px 10px; font-size: 0.9rem"
                :disabled="busy" @click="choose(row, c)">{{ candidateLabel(c) }}</button>
      </div>
    </div>

    <div class="row" style="margin-top: 10px; flex-wrap: wrap; justify-content: flex-start">
      <button :disabled="busy || !row.machine.productName" @click="decide(row, { action: 'CONFIRM' })">{{ t('matchAccept') }}</button>
      <button class="secondary" :disabled="busy" @click="decide(row, { action: 'NONE' })">{{ t('matchNone') }}</button>
      <button class="secondary" :disabled="busy" @click="decide(row, { action: 'NOT_A_PRODUCT' })">{{ t('matchNotProduct') }}</button>
      <button v-if="row.decision" class="secondary" :disabled="busy" @click="decide(row, { action: 'CLEAR' })">{{ t('matchUndo') }}</button>
    </div>

    <label :for="`s-${row.id}`">{{ t('matchSearch') }}</label>
    <input :id="`s-${row.id}`" type="search" autocomplete="off" :aria-describedby="`sh-${row.id}`"
           :value="searchRow === row.id ? searchText : ''"
           @input="onSearch(row, ($event.target as HTMLInputElement).value)"
           @keydown.enter.prevent="searchEnter(row)" @keydown.esc="closeSearch" />
    <div :id="`sh-${row.id}`" class="muted">{{ t('matchSearchHint') }}</div>
    <ul v-if="searchRow === row.id && (hits.length || searched)" style="list-style: none; padding: 0; margin: 6px 0 0">
      <li v-for="(h, i) in hits" :key="i">
        <button class="secondary" style="width: 100%; text-align: left; margin-bottom: 4px; padding: 8px 10px" :disabled="busy"
                @click="choose(row, h)">
          {{ h.name }}<span v-if="h.articleNo" class="muted"> · {{ t('matchArticle') }} {{ h.articleNo }}</span>
        </button>
      </li>
      <li v-if="!hits.length" class="muted">{{ t('matchNoHits') }}</li>
    </ul>
  </div>

  <div v-if="totalPages > 1" class="row">
    <button class="secondary" :disabled="page <= 0 || busy" @click="load(page - 1)">{{ t('matchPrev') }}</button>
    <span class="muted">{{ t('matchPage') }} {{ page + 1 }} {{ t('matchOf') }} {{ totalPages }} ({{ totalItems }})</span>
    <button class="secondary" :disabled="page + 1 >= totalPages || busy" @click="load(page + 1)">{{ t('matchNext') }}</button>
  </div>
</template>
