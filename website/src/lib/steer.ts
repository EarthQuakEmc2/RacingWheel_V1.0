export type SteerBus = {
  /** Commanded angle in radians. Positive is right. */
  target: number
  /** Smoothed angle in radians. */
  current: number
  throttle: number
  brake: number
  /** True while a pointer or the keyboard is driving the wheel. */
  pointer: boolean
  /** Extra angle mixed in while the page scrolls. */
  scroll: number
  reduced: boolean
}

export function createBus(): SteerBus {
  return {
    target: 0,
    current: 0,
    throttle: 0,
    brake: 0,
    pointer: false,
    scroll: 0,
    reduced: false,
  }
}

export function clamp(n: number, min: number, max: number) {
  return Math.max(min, Math.min(max, n))
}

export function damp(current: number, target: number, lambda: number, dt: number) {
  return current + (target - current) * (1 - Math.exp(-lambda * dt))
}
