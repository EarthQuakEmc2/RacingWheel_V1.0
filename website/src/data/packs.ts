/**
 * Steering wheel packs sold from /pricing.
 *
 * Leave the promo fields unset to show the regular price only.
 * Set them on a pack to turn a promotion on without changing the card:
 * - promoPrice: the price shown in the cart bar
 * - discountPercent: the badge, for example 25 for "-25%"
 * - promoLabel: the promotion line under the subtitle
 */
export type WheelPack = {
  id: string
  brand: string
  title: string
  subtitle: string
  banner: string
  bannerAlt: string
  tag: string
  platforms: readonly string[]
  compatibility: string
  price: string
  checkoutUrl: string
  promoPrice?: string
  discountPercent?: number
  promoLabel?: string
}

const PRICE = 'USD$ 3.99'
const TAG = 'Wheel Pack'
const PLATFORMS = ['Android', 'Windows'] as const
const COMPATIBILITY = 'Assetto Corsa'

function pack(id: string, brand: string, checkoutUrl: string): WheelPack {
  return {
    id,
    brand,
    title: `Buy ${brand} Pack`,
    subtitle: 'Assetto Corsa steering wheel pack for Racing Wheel Pro',
    banner: `/packs/${id}.jpg`,
    bannerAlt: `${brand} mark beside the MyRacingWheel signature`,
    tag: TAG,
    platforms: PLATFORMS,
    compatibility: COMPATIBILITY,
    price: PRICE,
    checkoutUrl,
  }
}

export const WHEEL_PACKS: readonly WheelPack[] = [
  pack('ferrari', 'Ferrari', 'https://buy.stripe.com/test_14A6oJakEeCEepbc977ss01'),
  pack('porsche', 'Porsche', 'https://buy.stripe.com/test_4gM5kFcsM7ac4OB8WV7ss03'),
  pack('lamborghini', 'Lamborghini', 'https://buy.stripe.com/test_00w28t2ScfGIgxj2yx7ss04'),
  pack('audi', 'Audi', 'https://buy.stripe.com/test_bJe8wR78seCE3Kx3CB7ss05'),
  pack('alfa-romeo', 'Alfa Romeo', 'https://buy.stripe.com/test_4gM4gB2SceCE5SFflj7ss06'),
  pack('bmw', 'BMW', 'https://buy.stripe.com/test_6oU00l0K47ac1Cpddb7ss07'),
  pack('mclaren', 'McLaren', 'https://buy.stripe.com/test_dRmfZj0K49ikgxj6ON7ss08'),
  pack('mercedes', 'Mercedes', 'https://buy.stripe.com/test_cNi7sN50k9ik94R1ut7ss09'),
  pack('lotus', 'Lotus', 'https://buy.stripe.com/test_3cIaEZgJ2eCEftf7SR7ss0a'),
  pack('pagani', 'Pagani', 'https://buy.stripe.com/test_eVqcN73Wg7ac0yl7SR7ss0b'),
]
