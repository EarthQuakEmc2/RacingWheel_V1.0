import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import LedStrip from './LedStrip'

const LINKS = [
  { href: '/#drive', id: 'drive', label: 'Drive' },
  { href: '/#wheels', id: 'wheels', label: 'Wheels' },
  { href: '/#makers', id: 'makers', label: 'Makers' },
  { href: '/#download', id: 'download', label: 'Download' },
  { href: '/#install', id: 'install', label: 'Install' },
  { href: '/leaderboard', id: 'leaderboard', label: 'Leaderboard' },
  { href: '/account', id: 'account', label: 'Account' },
]

export default function Header() {
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState('top')
  const location = useLocation()
  const routeActive = location.pathname === '/account' ? 'account' : location.pathname === '/leaderboard' ? 'leaderboard' : ''

  useEffect(() => {
    const ids = ['top', ...LINKS.map((l) => l.id), 'notes']
    const nodes = ids
      .map((id) => document.getElementById(id))
      .filter((node): node is HTMLElement => node !== null)
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
        if (visible?.target.id) setActive(visible.target.id)
      },
      { rootMargin: '-45% 0px -45% 0px', threshold: [0, 0.2, 0.5] },
    )
    nodes.forEach((node) => observer.observe(node))
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <div className="chrome">
      <LedStrip animate />
      <header className="topbar">
        <a className="brand" href="/#top">
          <WheelMark />
          <span>
            Racing Wheel <em>Pro</em>
          </span>
        </a>
        <button
          className="menu-btn"
          type="button"
          aria-expanded={open}
          aria-controls="site-nav"
          onClick={() => setOpen((value) => !value)}
        >
          {open ? 'Close' : 'Menu'}
        </button>
        <nav id="site-nav" className={open ? 'nav is-open' : 'nav'} aria-label="Page">
          {LINKS.map((link) => (
            <a
              key={link.id}
              href={link.href}
              aria-current={(routeActive || active) === link.id ? 'location' : undefined}
              onClick={() => setOpen(false)}
            >
              {link.label}
            </a>
          ))}
          <a
            className="nav-git"
            href="https://github.com/EarthQuakEmc2/RacingWheel_V1.0"
            target="_blank"
            rel="noreferrer"
          >
            GitHub
          </a>
        </nav>
      </header>
    </div>
  )
}

function WheelMark() {
  return (
    <svg className="mark" viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="4" fill="#10141a" stroke="#2c343d" />
      <path
        d="M6 16c2.2-6 6-9 10-9s7.8 3 10 9c-2.2 6-6 9-10 9s-7.8-3-10-9z"
        fill="none"
        stroke="#f3f1ec"
        strokeWidth="1.6"
      />
      <rect x="12" y="12.5" width="8" height="5.5" rx="0.8" fill="#0b0d10" stroke="#f5c518" strokeWidth="0.7" />
      <circle cx="10.2" cy="15" r="1.1" fill="#f5c518" />
      <circle cx="21.8" cy="15" r="1.1" fill="#e040fb" />
      <circle cx="13.2" cy="20.5" r="1.15" fill="#3ddc57" />
      <circle cx="18.8" cy="20.5" r="1.15" fill="#e10600" />
    </svg>
  )
}
