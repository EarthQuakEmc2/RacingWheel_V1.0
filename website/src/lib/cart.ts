import { bundle, classPack, manufacturer, purchasable, type CartLine } from '../data/catalogue'

const KEY = 'rwp-steering-cart'

export function readCart(): CartLine[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) || '[]') as CartLine[]
    return Array.isArray(parsed) ? parsed.filter((line) => line && typeof line.id === 'string') : []
  } catch {
    return []
  }
}

export function writeCart(lines: CartLine[]) {
  localStorage.setItem(KEY, JSON.stringify(lines))
}

export function addLine(line: CartLine) {
  const lines = readCart().filter((item) => !(item.type === line.type && item.id === line.id))
  if (line.type === 'bundle') {
    const next = lines.filter((item) => item.type !== 'bundle')
    next.push(line)
    writeCart(next)
    return
  }
  lines.push(line)
  writeCart(lines)
}

export function removeLine(index: number) {
  const lines = readCart()
  lines.splice(index, 1)
  writeCart(lines)
}

export function clearCart() {
  localStorage.removeItem(KEY)
}

export function lineLabel(line: CartLine) {
  if (line.type === 'manufacturer') return manufacturer(line.id)?.name ?? line.id
  if (line.type === 'class') return classPack(line.id)?.name ?? line.id
  const found = bundle(line.id)
  if (!found) return line.id
  if (found.kind === 'all') return found.name
  const names = (line.manufacturers ?? []).map((id) => manufacturer(id)?.name ?? id)
  return `${found.name}: ${names.join(', ')}`
}

export function lineReady(line: CartLine) {
  if (line.type === 'manufacturer') {
    const pack = manufacturer(line.id)
    return Boolean(pack && purchasable(pack))
  }
  if (line.type === 'class') {
    const pack = classPack(line.id)
    return Boolean(pack && purchasable(pack))
  }
  const found = bundle(line.id)
  if (!found) return false
  if (found.kind === 'all') return true
  return (line.manufacturers ?? []).length === found.count
}
