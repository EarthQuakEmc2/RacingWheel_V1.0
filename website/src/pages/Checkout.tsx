import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { formatMoney } from '../data/catalogue'
import { clearCart, lineLabel, readCart } from '../lib/cart'
import { api } from '../lib/trace'

type Me = { id: string; email: string }
type Order = {
  orderId: string
  status: string
  provider: string
  mode: string
  amountCents: number
  currency: string
  grants?: string[]
  lines?: { label: string; amountCents: number }[]
  redirectUrl?: string | null
  packages?: string[]
}

export default function CheckoutPage() {
  const [lines] = useState(() => readCart())
  const [me, setMe] = useState<Me | null>(null)
  const [ready, setReady] = useState(false)
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState<Order | null>(null)

  useEffect(() => {
    api<Me>('/api/auth/me')
      .then(setMe)
      .catch(() => setMe(null))
      .finally(() => setReady(true))
  }, [])

  return (
    <>
      <Header />
      <main className="coach-page">
        <div className="wrap">
          <p className="kicker">
            <Link to="/cart">Cart</Link>
          </p>
          <h1>Checkout</h1>
          <p className="lede">Sandbox only. The form never asks for a card. The provider adapter refuses live keys.</p>
          {!ready ? <p>Checking the session…</p> : null}
          {ready && !me ? <Auth onDone={(profile) => setMe(profile)} /> : null}
          {me ? <p>Signed in as {me.email}.</p> : null}
          {lines.length === 0 && !done ? <p>Nothing to buy. <Link to="/packages">Browse packages</Link>.</p> : null}
          {lines.length > 0 && !done ? (
            <ul className="cart-lines">
              {lines.map((line, index) => (
                <li key={`${line.id}-${index}`}>
                  <strong>{lineLabel(line)}</strong>
                </li>
              ))}
            </ul>
          ) : null}
          {error ? <p className="form-error">{error}</p> : null}
          {order && !done ? (
            <section className="panel">
              <h2>Server total {formatMoney(order.amountCents)}</h2>
              <p>
                Provider {order.provider}, mode {order.mode}. Order {order.orderId}.
              </p>
              {order.redirectUrl ? (
                <p>
                  <a href={order.redirectUrl}>Continue on the test payment page</a>
                </p>
              ) : (
                <button className="btn btn-red" type="button" disabled={busy} onClick={() => pay(order.orderId)}>
                  Complete test payment
                </button>
              )}
            </section>
          ) : null}
          {me && lines.length > 0 && !order && !done ? (
            <button className="btn btn-red" type="button" disabled={busy} onClick={place}>
              Create sandbox order
            </button>
          ) : null}
          {done ? (
            <section className="panel">
              <h2>Paid in sandbox</h2>
              <p>
                {formatMoney(done.amountCents)} recorded for this account. No card was sent or stored. Open the app, sign in
                with the same email, and refresh the licence. The wheels stay available offline after that check.
              </p>
              <p>Owned: {(done.packages ?? []).join(', ') || 'none'}.</p>
              <Link className="btn btn-ghost" to="/account">
                Account
              </Link>
            </section>
          ) : null}
        </div>
      </main>
      <Footer />
    </>
  )

  async function place() {
    setBusy(true)
    setError('')
    try {
      const body = await api<Order>('/api/checkout', { method: 'POST', body: JSON.stringify({ lines }) })
      setOrder(body)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not start checkout')
    } finally {
      setBusy(false)
    }
  }

  async function pay(orderId: string) {
    setBusy(true)
    setError('')
    try {
      const body = await api<Order>(`/api/checkout/${orderId}/confirm`, { method: 'POST', body: '{}' })
      clearCart()
      setDone(body)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Payment was not confirmed')
    } finally {
      setBusy(false)
    }
  }
}

function Auth({ onDone }: { onDone: (me: Me) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  return (
    <form
      className="panel auth-form"
      onSubmit={async (event) => {
        event.preventDefault()
        setBusy(true)
        setError('')
        try {
          await api(`/api/auth/${mode}`, { method: 'POST', body: JSON.stringify({ email, password }) })
          onDone(await api<Me>('/api/auth/me'))
        } catch (err) {
          setError(err instanceof Error ? err.message : 'Could not sign in')
        } finally {
          setBusy(false)
        }
      }}
    >
      <h2>{mode === 'login' ? 'Sign in' : 'Create an account'}</h2>
      <p>Uses the same account as the coach. The password is not a payment credential.</p>
      <label>
        Email
        <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="username" required />
      </label>
      <label>
        Password
        <input
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          type="password"
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          minLength={8}
          required
        />
      </label>
      {error ? <p className="form-error">{error}</p> : null}
      <div className="cta">
        <button className="btn btn-red" type="submit" disabled={busy}>
          {mode === 'login' ? 'Sign in' : 'Register'}
        </button>
        <button className="btn btn-ghost" type="button" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? 'Need an account?' : 'Have an account?'}
        </button>
      </div>
    </form>
  )
}
