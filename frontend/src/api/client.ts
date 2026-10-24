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

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...(init.headers as Record<string, string>) }
  if (init.body) headers['Content-Type'] = 'application/json'
  const xsrf = csrfToken()
  if (xsrf && init.method && init.method !== 'GET') headers['X-XSRF-TOKEN'] = decodeURIComponent(xsrf)
  const dev = devUser()
  if (dev) headers['X-Dev-User'] = dev

  const res = await fetch(path, { credentials: 'same-origin', ...init, headers })
  if (res.status === 401) {
    window.location.href = '/oauth2/authorization/google'
    throw new ApiFailure(401, 'UNAUTHORIZED', 'Logging in')
  }
  if (!res.ok) {
    let err: ApiError = { code: String(res.status), message: res.statusText }
    try {
      err = await res.json()
    } catch {
      /* no body */
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
