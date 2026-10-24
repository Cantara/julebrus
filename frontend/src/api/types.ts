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
  weight: { weight: number; reason: string }
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

export interface ConsentView {
  version: string
  accepted: boolean
  texts: { nb: string; en: string }
}

export interface PublicConfig {
  sponsor: { name: string; url?: string } | null
  contactEmail: string | null
}

export type ClaimStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

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
  userId: string
  displayName?: string
  email?: string
  createdAt: string
}
