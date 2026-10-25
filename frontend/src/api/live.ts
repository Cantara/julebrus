/**
 * Live reveal: one EventSource per table. The server sends a small event for every
 * write at the table (a score, a duel, a join, a beer, a blind guess); we refetch on
 * each, so every phone unwraps at the same moment. The browser reconnects on its own.
 */

export type LiveStatus = 'connecting' | 'live' | 'offline'

function devUser(): string | null {
  try {
    return localStorage.getItem('devUser')
  } catch {
    return null
  }
}

const KINDS = ['session', 'tasting', 'duel', 'blind', 'photo', 'reaction']

function listen(path: string, onChange: (kind: string) => void, onStatus: (status: LiveStatus) => void): () => void {
  // EventSource cannot set headers, so the dev impersonation rides on the query string.
  const dev = devUser()
  const url = path + (dev ? `?devUser=${encodeURIComponent(dev)}` : '')
  const source = new EventSource(url, { withCredentials: true })
  onStatus('connecting')
  source.addEventListener('hello', () => onStatus('live'))
  for (const kind of KINDS) {
    source.addEventListener(kind, () => onChange(kind))
  }
  source.onopen = () => onStatus('live')
  source.onerror = () => onStatus(source.readyState === EventSource.CLOSED ? 'offline' : 'connecting')
  return () => source.close()
}

export function subscribeSession(
  sessionId: string,
  onChange: (kind: string) => void,
  onStatus: (status: LiveStatus) => void,
): () => void {
  return listen(`/api/sessions/${sessionId}/events`, onChange, onStatus)
}

/** The day's wall: every table's events plus solo rolls, approved labels and cheers. */
export function subscribeFeed(onChange: (kind: string) => void, onStatus: (status: LiveStatus) => void): () => void {
  return listen('/api/feed/events', onChange, onStatus)
}
