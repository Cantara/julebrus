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

export function subscribeSession(
  sessionId: string,
  onChange: (kind: string) => void,
  onStatus: (status: LiveStatus) => void,
): () => void {
  // EventSource cannot set headers, so the dev impersonation rides on the query string.
  const dev = devUser()
  const url = `/api/sessions/${sessionId}/events` + (dev ? `?devUser=${encodeURIComponent(dev)}` : '')
  const source = new EventSource(url, { withCredentials: true })
  onStatus('connecting')
  source.addEventListener('hello', () => onStatus('live'))
  for (const kind of ['session', 'tasting', 'duel', 'blind']) {
    source.addEventListener(kind, () => onChange(kind))
  }
  source.onopen = () => onStatus('live')
  source.onerror = () => onStatus(source.readyState === EventSource.CLOSED ? 'offline' : 'connecting')
  return () => source.close()
}
