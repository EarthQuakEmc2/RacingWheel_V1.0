import assert from 'node:assert/strict'
import test from 'node:test'
import { accountStep, healthStep, progress, secureStep } from '../src/lib/splashChecks.ts'

test('secure context is the browser flag, not a made-up scan', () => {
  const ok = secureStep(true, 'https:')
  assert.equal(ok.ok, true)
  assert.match(ok.detail, /loaded over HTTPS/)
  const local = secureStep(true, 'http:')
  assert.equal(local.ok, true)
  assert.match(local.detail, /not HTTPS/)
  const bad = secureStep(false, 'http:')
  assert.equal(bad.stopped, true)
  assert.match(bad.detail, /not on a secure connection/)
})

test('health uses the JSON the server returned', () => {
  const ok = healthStep(200, { ok: true, version: '0.6.0-preview' })
  assert.equal(ok.ok, true)
  assert.match(ok.detail, /0\.6\.0-preview/)
  const down = healthStep(0, null)
  assert.equal(down.stopped, true)
  assert.match(down.detail, /Can't reach server/)
  const sick = healthStep(200, { ok: false })
  assert.equal(sick.ok, false)
})

test('a missing sign-in is a finished check', () => {
  const guest = accountStep(401, null)
  assert.equal(guest.ok, true)
  assert.equal(guest.stopped, false)
  assert.match(guest.detail, /Not signed in/)
  const member = accountStep(200, 'ada@example.com')
  assert.match(member.detail, /ada@example.com/)
  const broken = accountStep(0, null)
  assert.equal(broken.stopped, true)
  assert.equal(progress(2, 3), 2 / 3)
})
