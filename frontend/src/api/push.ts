/**
 * Web Push from the browser's side: the service worker's push subscription, handed to
 * the server. The server encrypts with the browser's keys and signs with its own (VAPID).
 */
import { del, get, post } from './client'
import type { PushConfig } from './types'

export type PushState = 'unsupported' | 'off' | 'denied' | 'on' | 'available'

export function supported(): boolean {
  return typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}

/** iOS only delivers push to an app on the home screen. */
export function needsHomeScreen(): boolean {
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent)
  const standalone = window.matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true
  return ios && !standalone
}

function toKey(base64url: string): Uint8Array {
  const padded = base64url + '='.repeat((4 - (base64url.length % 4)) % 4)
  const raw = atob(padded.replace(/-/g, '+').replace(/_/g, '/'))
  return Uint8Array.from(raw, (c) => c.charCodeAt(0))
}

async function registration(): Promise<ServiceWorkerRegistration> {
  return navigator.serviceWorker.ready
}

/** Where this browser stands: not supported, feature off on the server, denied, subscribed, or available. */
export async function state(config: PushConfig): Promise<{ state: PushState; endpoint?: string }> {
  if (!config.enabled) return { state: 'off' }
  if (!supported()) return { state: 'unsupported' }
  if (Notification.permission === 'denied') return { state: 'denied' }
  const sub = await (await registration()).pushManager.getSubscription().catch(() => null)
  if (!sub) return { state: 'available' }
  const mine = await get<{ endpoints: string[] }>('/api/push/me').catch(() => ({ endpoints: [] as string[] }))
  return mine.endpoints.includes(sub.endpoint) ? { state: 'on', endpoint: sub.endpoint } : { state: 'available', endpoint: sub.endpoint }
}

export async function enable(config: PushConfig): Promise<void> {
  if (!config.publicKey) throw new Error('Push is off')
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') throw new Error('denied')
  const reg = await registration()
  const sub = (await reg.pushManager.getSubscription()) ?? (await reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: toKey(config.publicKey).buffer as ArrayBuffer,
  }))
  const json = sub.toJSON()
  await post('/api/push/subscriptions', { endpoint: sub.endpoint, keys: { p256dh: json.keys?.p256dh, auth: json.keys?.auth } })
}

export async function disable(): Promise<void> {
  const reg = await registration()
  const sub = await reg.pushManager.getSubscription()
  if (!sub) return
  await del('/api/push/subscriptions', { endpoint: sub.endpoint }).catch(() => {})
  await sub.unsubscribe().catch(() => {})
}

export const sendTest = () => post<{ sent: number }>('/api/push/test')
