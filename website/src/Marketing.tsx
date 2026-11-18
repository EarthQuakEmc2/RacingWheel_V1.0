import Download from './components/Download'
import Faq from './components/Faq'
import Features from './components/Features'
import Footer from './components/Footer'
import Header from './components/Header'
import Hero from './components/Hero'
import Install from './components/Install'
import Manufacturers from './components/Manufacturers'
import Screenshots from './components/Screenshots'

export default function Marketing() {
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
        <Download />
        <Install />
        <Faq />
      </main>
      <Footer />
    </>
  )
}
