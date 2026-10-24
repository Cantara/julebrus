export type ApiError = { code: string; message: string }

export class ApiFailure extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message)
  }
}

function csrfToken(): string | undefined {
  return document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=')[1]
}

/** Dev only: lets a second browser tab act as another person. Set in the browser console. */
function devUser(): string | null {
  try {
    return localStorage.getItem('devUser')
  } catch {
    return null
  }
}

function localStorageLang(): string | null {
  try {
    return localStorage.getItem('lang')
  } catch {
    return null
  }
}

export async function api<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...(init.headers as Record<string, string>) }
  if (init.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json'
  const xsrf = csrfToken()
  if (xsrf && init.method && init.method !== 'GET') headers['X-XSRF-TOKEN'] = decodeURIComponent(xsrf)
  const dev = devUser()
  if (dev) headers['X-Dev-User'] = dev

  const res = await fetch(path, { credentials: 'same-origin', ...init, headers })
  // /privacy is public: nothing on it may send a logged-out visitor to Google.
  if (res.status === 401 && window.location.pathname !== '/privacy') {
    // On the front door (a cached app shell, an installed PWA, after logout) a logged-out visitor
    // gets the public landing page with its own sign-in button; deep links still go to Google.
    const frontDoor = window.location.pathname === '/' || window.location.pathname === '/index.html'
    if (frontDoor) window.location.replace('/velkommen' + (localStorageLang() === 'en' ? '?lang=en' : ''))
    else window.location.href = '/oauth2/authorization/google'
    throw new ApiFailure(401, 'UNAUTHORIZED', 'Logging in')
  }
  // A mutating call without an XSRF cookie yet (fresh login, cookies cleared): the server has
  // issued one on this very response, so fetch once and try again rather than show a 403.
  if (res.status === 403 && !retried && init.method && init.method !== 'GET' && !xsrf) {
    await fetch('/api/me', { credentials: 'same-origin' }).catch(() => {})
    if (csrfToken()) return api<T>(path, init, true)
  }
  if (!res.ok) {
    let err: ApiError = { code: String(res.status), message: res.statusText }
    try {
      err = await res.json()
    } catch {
      /* no body */
    }
    // The server enforces consent on every call; send the person to the consent page.
    if (res.status === 403 && err.code === 'CONSENT_REQUIRED' && window.location.pathname !== '/consent') {
      window.location.href = '/consent'
    }
    throw new ApiFailure(res.status, err.code, err.message)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const get = <T>(path: string) => api<T>(path)
export const post = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })
/** POST a raw text body (CSV upload); the content type is passed through as given. */
export const postText = <T>(path: string, text: string, contentType = 'text/csv') =>
  api<T>(path, { method: 'POST', body: text, headers: { 'Content-Type': contentType } })
export const put = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) })
export const del = <T>(path: string, body?: unknown) =>
  api<T>(path, { method: 'DELETE', body: body === undefined ? undefined : JSON.stringify(body) })

/** Ends the session. The server answers with a redirect to "/", so the body is ignored. */
export async function logout(): Promise<void> {
  try {
    await api('/api/logout', { method: 'POST' })
  } catch {
    /* the redirect target is HTML, and the dev profile has no logout: either way we leave */
  }
  window.location.href = '/'
}

/**
 * Multipart upload of one file (part "file") with progress. fetch cannot report upload progress, so this uses XHR.
 * Resolves with the parsed JSON; rejects with an ApiFailure carrying the server's stable code.
 */
export function upload<T>(path: string, file: Blob, filename: string, onProgress?: (fraction: number) => void): Promise<T> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', path)
    xhr.setRequestHeader('Accept', 'application/json')
    const xsrf = csrfToken()
    if (xsrf) xhr.setRequestHeader('X-XSRF-TOKEN', decodeURIComponent(xsrf))
    const dev = devUser()
    if (dev) xhr.setRequestHeader('X-Dev-User', dev)
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(e.loaded / e.total)
    }
    xhr.onerror = () => reject(new ApiFailure(0, 'NETWORK', 'Network error'))
    xhr.onload = () => {
      let body: { code?: string; message?: string } = {}
      try {
        body = JSON.parse(xhr.responseText)
      } catch {
        /* no body */
      }
      if (xhr.status >= 200 && xhr.status < 300) return resolve(body as T)
      if (xhr.status === 403 && body.code === 'CONSENT_REQUIRED' && window.location.pathname !== '/consent') {
        window.location.href = '/consent'
      }
      reject(new ApiFailure(xhr.status, body.code ?? String(xhr.status), body.message ?? xhr.statusText))
    }
    const form = new FormData()
    form.append('file', file, filename)
    xhr.send(form)
  })
}
