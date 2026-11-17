import { soundOn } from './effects'

/**
 * Every sound is synthesized here with Web Audio: no sound files, in the spirit of the rest of the effects.
 * Off unless the person switched sound on. A browser only lets audio start from a tap, so tap handlers call
 * prime() and the sounds that follow a moment later can play.
 */
let ctx: AudioContext | null = null

function audio(): AudioContext | null {
  if (!soundOn.value) return null
  try {
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Ctor) return null
    ctx ??= new Ctor()
    if (ctx.state === 'suspended') void ctx.resume()
    return ctx
  } catch {
    return null
  }
}

export function prime() {
  audio()
}

function tone(a: AudioContext, freq: number, at: number, dur: number, gain: number, type: OscillatorType = 'sine') {
  const o = a.createOscillator()
  const g = a.createGain()
  o.type = type
  o.frequency.value = freq
  g.gain.setValueAtTime(0.0001, at)
  g.gain.exponentialRampToValueAtTime(gain, at + 0.008)
  g.gain.exponentialRampToValueAtTime(0.0001, at + dur)
  o.connect(g).connect(a.destination)
  o.start(at)
  o.stop(at + dur + 0.02)
}

function noise(a: AudioContext, at: number, dur: number, gain: number, freq: number, q = 1) {
  const len = Math.max(1, Math.floor(a.sampleRate * dur))
  const buffer = a.createBuffer(1, len, a.sampleRate)
  const data = buffer.getChannelData(0)
  for (let i = 0; i < len; i++) data[i] = Math.random() * 2 - 1
  const src = a.createBufferSource()
  src.buffer = buffer
  const filter = a.createBiquadFilter()
  filter.type = 'bandpass'
  filter.frequency.value = freq
  filter.Q.value = q
  const g = a.createGain()
  g.gain.setValueAtTime(gain, at)
  g.gain.exponentialRampToValueAtTime(0.0001, at + dur)
  src.connect(filter).connect(g).connect(a.destination)
  src.start(at)
}

/** A bell: a fundamental with the inharmonic partials that make it ring. */
function bell(a: AudioContext, freq: number, at: number, gain = 0.12) {
  tone(a, freq, at, 1.4, gain)
  tone(a, freq * 2.76, at, 0.9, gain * 0.45)
  tone(a, freq * 5.4, at, 0.5, gain * 0.2)
}

function play(fn: (a: AudioContext, t: number) => void) {
  const a = audio()
  if (!a) return
  try {
    fn(a, a.currentTime + 0.01)
  } catch {
    /* a sound is never worth an error */
  }
}

/** A die landing on a wooden table: two knocks, the second softer. */
export const clack = () => play((a, t) => {
  noise(a, t, 0.05, 0.5, 1800, 2)
  noise(a, t + 0.16, 0.04, 0.28, 2300, 2)
  noise(a, t + 0.27, 0.03, 0.14, 2600, 2)
})

/** A door opening: two sleigh-bell notes. */
export const chime = () => play((a, t) => {
  bell(a, 1318.5, t)
  bell(a, 1760, t + 0.16)
})

/** Beer going into the glass: a hiss that rises as it fills, with a few bubbles. */
export const pour = () => play((a, t) => {
  for (let i = 0; i < 6; i++) noise(a, t + i * 0.18, 0.22, 0.08, 900 + i * 320, 0.8)
  for (let i = 0; i < 5; i++) tone(a, 500 + Math.random() * 700, t + 0.5 + i * 0.17, 0.06, 0.05)
})

/** Two glasses meeting. */
export const clink = () => play((a, t) => {
  tone(a, 2093, t, 0.5, 0.1)
  tone(a, 2637, t + 0.015, 0.45, 0.08)
  tone(a, 3520, t + 0.02, 0.3, 0.04)
})

/** The star lights up: bells climbing a major chord. */
export const fanfare = () => play((a, t) => {
  ;[659.3, 784, 987.8, 1318.5].forEach((f, i) => bell(a, f, t + i * 0.22, 0.1))
})
