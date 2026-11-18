import Download from './components/Download'
import Faq from './components/Faq'
import Features from './components/Features'
import Footer from './components/Footer'
import Header from './components/Header'
import Hero from './components/Hero'
import Install from './components/Install'
import Manufacturers from './components/Manufacturers'
import Pricing from './components/Pricing'
import Screenshots from './components/Screenshots'
import Privacy from './pages/Privacy'

export default function App() {
  const path = window.location.pathname.replace(/\/+$/, '')
  if (path.endsWith('/privacy')) return <Privacy />
  return (
    <>
      <a className="skip" href="#content">
        Skip to content
      </a>
      <Header />
      <main id="content">
        <Hero />
        <Screenshots />
        <Manufacturers />
        <Features />
        <Pricing />
        <Download />
        <Install />
        <Faq />
      </main>
      <Footer />
    </>
  )
}
