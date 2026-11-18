import policy from '../../PRIVACY.md?raw'
import Footer from '../components/Footer'
import Header from '../components/Header'

export default function Privacy() {
  return (
    <>
      <Header />
      <main id="content" className="privacy">
        <article className="wrap privacy-article">
          <Markdown source={policy} />
        </article>
      </main>
      <Footer />
    </>
  )
}

function Markdown({ source }: { source: string }) {
  const blocks = source.replace(/\r\n/g, '\n').trim().split(/\n\n+/)
  return (
    <>
      {blocks.map((block, index) => {
        const lines = block.split('\n')
        if (lines[0].startsWith('# ')) return <h1 key={index}>{lines[0].slice(2)}</h1>
        if (lines[0].startsWith('## ')) return <h2 key={index}>{lines[0].slice(3)}</h2>
        if (lines.every((line) => line.startsWith('- '))) {
          return (
            <ul key={index}>
              {lines.map((line) => (
                <li key={line}>{inline(line.slice(2))}</li>
              ))}
            </ul>
          )
        }
        return <p key={index}>{inline(block.replace(/\n/g, ' '))}</p>
      })}
    </>
  )
}

function inline(text: string) {
  const parts = text.split(/(\[[^\]]+\]\([^)]+\)|https?:\/\/[^\s)]+)/g)
  return parts.map((part, index) => {
    const labeled = part.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
    if (labeled) {
      return (
        <a key={index} href={labeled[2]}>
          {labeled[1]}
        </a>
      )
    }
    if (part.startsWith('http://') || part.startsWith('https://')) {
      const href = part.replace(/[.,]$/, '')
      return (
        <a key={index} href={href}>
          {part}
        </a>
      )
    }
    return <span key={index}>{part}</span>
  })
}
