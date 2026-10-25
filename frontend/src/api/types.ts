export interface Status {
  today: string
  tastingsToday: number
  /** the limit that applies right now: the big-table one while sitting at an open big table */
  maxPerDay: number
  soloDaysUsed: number
  maxSoloDays: number
  inSeason: boolean
  /** sitting at an open table of bigTableSize or more */
  bigTable: boolean
}

export interface Me {
  id: string
  email: string
  displayName: string
  status: Status
  season: {
    year: number
    start: string
    end: string
    maxTastingsPerDay: number
    maxSoloDays: number
    bigTableSize: number
    maxTastingsPerDayBigTable: number
  }
  /** absent until the person has consented */
  weight: { weight: number; reason: string } | null
  admin: boolean
  consentRequired: boolean
  consentVersion: string
  /** the enabled teams the person plays for; empty before the choice and after "later" */
  teams: { id: string; slug: string; name: string }[]
  /** false until the team page has been answered, "later" included */
  teamsChosen: boolean
  /** the newest open table this person sits at; null when none */
  openTable: OpenTable | null
}

export interface OpenTable {
  id: string
  hostName: string
  host: boolean
  members: number
  beers: number
  pendingForMe: number
}

export interface MySession {
  id: string
  hostName: string
  host: boolean
  members: number
  beers: number
  pendingForMe: number
  createdAt: string
  closedAt?: string
}

export interface Team {
  id: string
  slug: string
  name: string
  sponsorName?: string
  sponsorUrl?: string
  members: number
  suggested: boolean
  mine: boolean
}

export interface TeamMate {
  userId: string
  displayName: string
  tastings: number
  joinedAt: string
  me: boolean
}

export interface TeamRoster {
  id: string
  slug: string
  name: string
  sponsorName?: string
  sponsorUrl?: string
  tastings: number
  mine: boolean
  members: TeamMate[]
}

export interface AdminTeam {
  id: string
  slug: string
  name: string
  sponsorName?: string
  sponsorUrl?: string
  domains?: string
  enabled: boolean
  members: number
}

export interface Beer {
  id: string
  name: string
  brewery?: string
  style?: string
  abv?: number
  vinmonopoletId?: string
  imageUrl?: string
  ratingCount: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  mine: boolean
  homebrew: boolean
  /** a cover is available to this caller at /api/beers/{id}/photo */
  hasPhoto: boolean
  /** the caller's own newest photo of the beer */
  myPhotoId?: string | null
  myPhotoStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | null
  /** approved and Vinmonopolet strength: in the ranking and tippable */
  counts: boolean
}

export interface PushConfig {
  enabled: boolean
  publicKey?: string
  nudgeTime: string
}

export interface TipPick {
  beerId: string
  name: string
  brewery?: string
}

export interface MyTip {
  tip: { podium: TipPick[]; createdAt: string; updatedAt: string } | null
  deadline: string
  closed: boolean
  tipped: number
}

export interface TipResult {
  userId: string
  name: string
  tip: string[]
  points: number
  createdAt: string
}

export interface StructuredNotes {
  appearance?: string
  aroma?: string
  taste?: string
  finish?: string
}

export interface Tasting {
  id: string
  beerId: string
  sessionId?: string
  score: number
  prediction?: number
  notes?: string
  structuredNotes?: StructuredNotes
  tags: string[]
  tastedAt: string
  tastedDay: string
}

export interface NewTasting extends StructuredNotes {
  beerId: string
  sessionId?: string
  score: number
  prediction?: number
  notes?: string
  tags?: string[]
}

export interface SessionBeer {
  beerId: string
  name: string
  brewery?: string
  ratedBy: string[]
  revealed: boolean
  scores?: Record<string, number>
  average?: number
  /** only once revealed, and only for people who wrote something */
  notes?: Record<string, { notes?: string; tags: string[] }>
}

export interface Session {
  id: string
  hostId: string
  joinCode: string
  createdAt: string
  closedAt?: string
  solo: boolean
  members: { userId: string; displayName: string; teams: string[] }[]
  beers: SessionBeer[]
}

export interface BeerRank {
  rank: number
  beerId: string
  name: string
  brewery?: string
  ratings: number
  effectiveVotes: number
  mean: number
  bayesian: number
  controversy: number
}

export interface DuelRank {
  rank: number
  beerId: string
  name: string
  brewery?: string
  strength: number
}

export interface DuelPair {
  beerA: string
  beerB: string
}

export interface BadgeView {
  id: string
  title: string
  description: string
  earned: boolean
}

export interface HistoricSeason {
  year: number
  title: string
  scaleMin: number
  scaleMax: number
  weightingDescription?: string
  sourceUrl?: string
}

export interface HistoricRating {
  year: number
  beerName: string
  brewery?: string
  raterName: string
  raterKey: string
  score: number
  weight: number
  normalised: number
  notes?: string
}

export interface Award {
  id: string
  title: string
  description: string
  winnerName?: string
  detail: string
}

export interface Finale {
  year: number
  revealAt: string
  revealed: boolean
  preview: boolean
  totals: { tastings: number; beers: number; people: number; sessions: number }
  podium?: BeerRank[]
  duelPodium?: DuelRank[]
  awards?: Award[]
  teamWinners?: TeamWinner[]
  /** the tipping league, best first; only after the reveal */
  tips?: TipResult[]
  tipped: number
}

export interface TeamWinner {
  slug: string
  team: string
  beerId: string
  beerName: string
  brewery?: string
  bayesian: number
  ratings: number
}

export interface ConsentView {
  version: string
  accepted: boolean
  texts: { nb: string; en: string }
}

export interface PublicConfig {
  sponsor: { name: string; url?: string } | null
  contactEmail: string | null
}

export type ClaimStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED' | 'WITHDRAWN'

export interface ClaimCandidate {
  raterKey: string
  years: number[]
  ratingCount: number
  available: boolean
  linkedByEmail: boolean
  linkedToYou: boolean
  suggested: boolean
  myClaim: { id: string; status: ClaimStatus } | null
}

export interface ClaimView {
  id: string
  raterKey: string
  status: ClaimStatus
  createdAt: string
  decidedAt?: string
}

export interface AdminClaimView {
  id: string
  status: ClaimStatus
  raterKey: string
  years: number[]
  ratingCount: number
  linkedByEmail: boolean
  userId: string
  displayName?: string
  email?: string
  createdAt: string
}

export interface PendingBeer {
  id: string
  name: string
  brewery?: string
  style?: string
  abv?: number
  vinmonopoletId?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  createdAt: string
  addedBy?: string
}

export interface Login {
  id: string
  displayName: string
  email: string
}

export interface RaterRow {
  raterKey: string
  years: number[]
  ratingCount: number
  linkedByEmail: boolean
  loginEmail?: string
  linkedTo?: Login | null
}

export interface SeasonOverview {
  year: number
  title: string
  raters: number
  beers: number
  votes: number
  winnerName?: string
  winnerWeightedMean?: number
  winnerNormalisedMean?: number
  weightingDescription?: string
  sourceUrl?: string
}

export interface BeerSeason {
  year: number
  beerName: string
  brewery?: string
  ratings: number
  weightedMean: number
  normalisedMean: number
}

export interface MySeason {
  year: number
  myRatings: number
  myMean: number
  crowdMean: number
}

export interface ImportRow {
  line: number
  name: string
  brewery?: string
  style?: string
  abv?: number
  vinmonopoletId?: string
  action: 'NEW' | 'DUPLICATE' | 'INVALID'
  reason?: string
}

export interface ImportResult {
  dryRun: boolean
  created: number
  rows: ImportRow[]
}

export interface TagLift {
  tag: string
  lift: number
  count: number
}

export interface PalateMatch {
  userId: string
  displayName: string
  similarity: number
  common: number
  beerName?: string
  yourScore: number
  theirScore: number
}

export interface Palate {
  tastings: number
  mean: number
  likes: TagLift[]
  dislikes: TagLift[]
  twin?: PalateMatch | null
  opposite?: PalateMatch | null
  minCommon: number
}

export interface PalatePair {
  a: string
  b: string
  similarity: number
  common: number
}

export interface PalatePairs {
  twins: PalatePair[]
  opposites: PalatePair[]
  minCommon: number
}

export interface AdminPhoto {
  id: string
  beerId: string
  beerName: string
  uploaderName: string
  uploaderEmail: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  createdAt: string
}

export interface BlindOption {
  beerId: string
  name: string
  brewery?: string
}

/** Who has guessed is always visible; the picks and the answer only once revealed. */
export interface BlindRound {
  id: string
  hostId: string
  createdAt: string
  revealedAt?: string
  revealed: boolean
  options: BlindOption[]
  guessedBy: string[]
  myGuess?: string
  answerBeerId?: string
  guesses?: Record<string, string>
  correct?: string[]
}

/** "I kveld": one line on the day's wall. Which optional fields are set depends on kind. */
export interface FeedReaction {
  emoji: string
  count: number
  mine: boolean
}

export interface FeedEntry {
  kind: 'TASTING' | 'TABLE' | 'PHOTO' | 'BLIND'
  id: string
  at: string
  userId?: string | null
  displayName?: string | null
  me: boolean
  beerId?: string | null
  beerName?: string | null
  brewery?: string | null
  tableId?: string | null
  tableHost?: string | null
  tableSize?: number | null
  open?: boolean | null
  beers?: number | null
  correct?: number | null
  guesses?: number | null
  reactions: FeedReaction[]
}

export interface FeedOpenTable {
  id: string
  hostName: string
  members: number
  beers: number
  mine: boolean
}

export interface FeedDayBeer {
  beerId: string
  name: string
  brewery?: string | null
  count: number
  /** only from three rolls */
  average: number | null
}

export interface Feed {
  day: string
  today: string
  tastings: number
  people: number
  openTables: FeedOpenTable[]
  entries: FeedEntry[]
  beers: FeedDayBeer[]
  emojis: string[]
}
