import catalogueJson from '../../../catalogue/steering-packages.json'
import manifestJson from '../../../catalogue/pack-manifest.json'

export type PriceStatus = 'set' | 'TODO'

export type CataloguePack = {
  id: string
  name: string
  priceCents: number | null
  priceStatus: PriceStatus
  summary: string
  templates: string[]
  includedFree: string[]
  aliases?: string[]
  contentStatus?: string
}

export type BundleDef = {
  id: string
  kind: 'pick' | 'all'
  name: string
  priceCents: number
  summary: string
  count?: number
}

type CatalogueFile = {
  version: number
  currency: 'USD'
  manufacturerPriceCents: number
  bundles: BundleDef[]
  manufacturers: CataloguePack[]
  classes: CataloguePack[]
}

type Manifest = {
  templates: Record<string, string[]>
}

export const catalogue = catalogueJson as CatalogueFile
export const packFiles = manifestJson as Manifest

export type CartLine = {
  type: 'manufacturer' | 'class' | 'bundle'
  id: string
  manufacturers?: string[]
}

export function formatMoney(cents: number | null, status: PriceStatus = 'set') {
  if (status === 'TODO' || cents == null) return 'TODO'
  return `$${(cents / 100).toFixed(2)}`
}

export function purchasable(pack: CataloguePack) {
  return pack.priceStatus === 'set' && typeof pack.priceCents === 'number' && pack.priceCents >= 0
}

export function manufacturer(id: string) {
  return catalogue.manufacturers.find((pack) => pack.id === id)
}

export function classPack(id: string) {
  return catalogue.classes.find((pack) => pack.id === id)
}

export function anyPack(id: string) {
  return manufacturer(id) ?? classPack(id)
}

export function bundle(id: string) {
  return catalogue.bundles.find((item) => item.id === id)
}

export function previewImage(pack: CataloguePack) {
  for (const id of pack.templates) {
    const image = (packFiles.templates[id] ?? []).find((name) => name.endsWith('.png'))
    if (image) return `${import.meta.env.BASE_URL}wheels/${image}`
  }
  return ''
}

export function contentLabel(pack: CataloguePack) {
  if (pack.templates.length === 0) return 'No templates in this build yet'
  return `${pack.templates.length} wheel template${pack.templates.length === 1 ? '' : 's'}`
}
