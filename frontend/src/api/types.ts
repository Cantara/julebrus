export interface Status {
  today: string
  tastingsToday: number
  maxPerDay: number
  soloDaysUsed: number
  maxSoloDays: number
  inSeason: boolean
}

export interface Me {
  id: string
  email: string
  displayName: string
  status: Status
  season: { year: number; start: string; end: string; maxTastingsPerDay: number; maxSoloDays: number }
  /** absent until the person has consented */
  weight: { weight: number; reason: string } | null
  admin: boolean
  consentRequired: boolean
  consentVersion: string
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
}

export interface Session {
  id: string
  hostId: string
  joinCode: string
  createdAt: string
  closedAt?: string
  solo: boolean
  members: { userId: string; displayName: string }[]
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
