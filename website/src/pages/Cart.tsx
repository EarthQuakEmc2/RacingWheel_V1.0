import { useState } from 'react'
import { Link } from 'react-router-dom'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { bundle, classPack, formatMoney, manufacturer } from '../data/catalogue'
import { lineLabel, readCart, removeLine } from '../lib/cart'
import { api } from '../lib/trace'

type Quote = { amountCents: number; currency: string; grants: string[]; error?: string }

export default function CartPage() {
  const [lines, setLines] = useState(() => readCart())
  const [quote, setQuote] = useState<Quote | null>(null)
  const [error, setError] = useState('')

  const local = localTotal(lines)

  return (
    <>
      <Header />
      <main className="coach-page">
        <div className="wrap">
          <p className="kicker">
            <Link to="/packages">Steering Packages</Link>
          </p>
          <h1>Cart</h1>
          {lines.length === 0 ? <p className="lede">The cart is empty.</p> : null}
          <ul className="cart-lines">
            {lines.map((line, index) => (
              <li key={`${line.type}-${line.id}-${index}`}>
                <div>
                  <strong>{lineLabel(line)}</strong>
                  <span>{local.rows[index]}</span>
                </div>
                <button
                  className="btn btn-ghost"
                  type="button"
                  onClick={() => {
                    removeLine(index)
                    setLines(readCart())
                    setQuote(null)
                  }}
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
          {lines.length > 0 ? (
            <section className="panel">
              <p>
                Catalogue total <strong>{local.amount == null ? '—' : formatMoney(local.amount)}</strong>
              </p>
              <p className="fine">Checkout asks the server for this total again. The server price is the one that is charged.</p>
              {error ? <p className="form-error">{error}</p> : null}
              {quote ? (
                <p className="form-ok">
                  Server quote {formatMoney(quote.amountCents)} {quote.currency}. Unlocks {quote.grants.join(', ')}.
                </p>
              ) : null}
              <div className="cta">
                <button
                  className="btn btn-ghost"
                  type="button"
                  onClick={async () => {
                    setError('')
                    try {
                      const body = await api<Quote>('/api/packages/quote', {
                        method: 'POST',
                        body: JSON.stringify({ lines }),
                      })
                      setQuote(body)
                    } catch (err) {
                      setQuote(null)
                      setError(err instanceof Error ? err.message : 'Could not quote')
                    }
                  }}
                >
                  Ask the server
                </button>
                <Link className="btn btn-red" to="/checkout">
                  Checkout
                </Link>
              </div>
            </section>
          ) : null}
        </div>
      </main>
      <Footer />
    </>
  )
}

function localTotal(lines: ReturnType<typeof readCart>) {
  const rows: string[] = []
  let amount = 0
  let priced = true
  for (const line of lines) {
    if (line.type === 'manufacturer') {
      const pack = manufacturer(line.id)
      rows.push(pack ? formatMoney(pack.priceCents, pack.priceStatus) : 'Unknown')
      if (!pack || pack.priceCents == null || pack.priceStatus !== 'set') priced = false
      else amount += pack.priceCents
    } else if (line.type === 'class') {
      const pack = classPack(line.id)
      rows.push(pack ? formatMoney(pack.priceCents, pack.priceStatus) : 'Unknown')
      priced = false
    } else {
      const found = bundle(line.id)
      rows.push(found ? formatMoney(found.priceCents) : 'Unknown')
      if (!found) priced = false
      else amount += found.priceCents
    }
  }
  return { rows, amount: priced ? amount : null }
}
