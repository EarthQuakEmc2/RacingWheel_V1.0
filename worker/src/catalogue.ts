import catalogueJson from '../../catalogue/steering-packages.json';
import manifestJson from '../../catalogue/pack-manifest.json';

export type PriceStatus = 'set' | 'TODO';

export type PackageRecord = {
  id: string;
  name: string;
  priceCents: number | null;
  priceStatus: PriceStatus;
  summary: string;
  templates: string[];
  includedFree: string[];
  aliases?: string[];
  contentStatus?: string;
};

export type BundleRecord = {
  id: string;
  kind: 'pick' | 'all';
  name: string;
  priceCents: number;
  summary: string;
  count?: number;
};

export type Catalogue = {
  version: number;
  currency: 'USD';
  manufacturerPriceCents: number;
  notes?: string;
  bundles: BundleRecord[];
  manufacturers: PackageRecord[];
  classes: PackageRecord[];
};

export type PackManifest = {
  version: number;
  templates: Record<string, string[]>;
  files: Record<string, { sha256: string; bytes: number }>;
};

export const catalogue = catalogueJson as Catalogue;
export const packManifest = manifestJson as PackManifest;

export class PackageError extends Error {
  constructor(
    message: string,
    readonly status = 400,
  ) {
    super(message);
  }
}

export type CartLine = {
  type: 'manufacturer' | 'class' | 'bundle';
  id: string;
  manufacturers?: string[];
};

export type QuotedLine = {
  type: string;
  id: string;
  label: string;
  amountCents: number;
  manufacturers?: string[];
};

export type Quote = {
  currency: 'USD';
  amountCents: number;
  grants: string[];
  templates: string[];
  lines: QuotedLine[];
};

const CARD_KEYS = new Set(['card', 'cardnumber', 'pan', 'cvc', 'cvv', 'expiry', 'expiration', 'securitycode', 'cardholder']);

export function rejectCardData(value: unknown, depth = 0): void {
  if (depth > 8 || value == null) return;
  if (Array.isArray(value)) {
    for (const item of value) rejectCardData(item, depth + 1);
    return;
  }
  if (typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
    const normal = key.toLowerCase().replace(/[^a-z]/g, '');
    if (CARD_KEYS.has(normal)) throw new PackageError('Card data is not accepted');
    rejectCardData(child, depth + 1);
  }
}

export function isPurchasable(pack: PackageRecord): boolean {
  return pack.priceStatus === 'set' && typeof pack.priceCents === 'number' && Number.isInteger(pack.priceCents) && pack.priceCents >= 0;
}

export function manufacturerById(id: string): PackageRecord | undefined {
  return catalogue.manufacturers.find((pack) => pack.id === id);
}

export function classById(id: string): PackageRecord | undefined {
  return catalogue.classes.find((pack) => pack.id === id);
}

export function packageById(id: string): PackageRecord | undefined {
  return manufacturerById(id) ?? classById(id);
}

export function bundleById(id: string): BundleRecord | undefined {
  return catalogue.bundles.find((bundle) => bundle.id === id);
}

export function publicCatalogue() {
  return {
    version: catalogue.version,
    currency: catalogue.currency,
    manufacturerPriceCents: catalogue.manufacturerPriceCents,
    bundles: catalogue.bundles,
    manufacturers: catalogue.manufacturers.map(publicPack),
    classes: catalogue.classes.map(publicPack),
  };
}

function publicPack(pack: PackageRecord) {
  return {
    id: pack.id,
    name: pack.name,
    kind: manufacturerById(pack.id) ? 'manufacturer' : 'class',
    priceCents: pack.priceCents,
    priceStatus: pack.priceStatus,
    purchasable: isPurchasable(pack),
    summary: pack.summary,
    contentStatus: pack.contentStatus ?? (pack.templates.length ? 'ready' : 'TODO'),
    templateCount: pack.templates.length,
    templates: pack.templates,
    includedFree: pack.includedFree,
  };
}

export function templatesFor(packageIds: string[]): string[] {
  const ids = new Set<string>();
  for (const id of packageIds) {
    const pack = packageById(id);
    if (!pack) continue;
    for (const template of pack.templates) ids.add(template);
  }
  return [...ids].sort();
}

export function filesForTemplates(templateIds: string[]) {
  const names = new Set<string>();
  for (const id of templateIds) {
    for (const name of packManifest.templates[id] ?? []) names.add(name);
  }
  return [...names].sort().map((name) => {
    const file = packManifest.files[name];
    if (!file) throw new PackageError(`Pack file ${name} is not in the manifest`, 500);
    return { name, sha256: file.sha256, bytes: file.bytes };
  });
}

export function fileOwned(packageIds: string[], name: string): boolean {
  return filesForTemplates(templatesFor(packageIds)).some((file) => file.name === name);
}

export function parseLines(value: unknown): CartLine[] {
  if (!value || typeof value !== 'object') throw new PackageError('Missing lines');
  const lines = (value as { lines?: unknown }).lines;
  if (!Array.isArray(lines) || lines.length === 0) throw new PackageError('Choose at least one package');
  if (lines.length > 40) throw new PackageError('Too many lines');
  return lines.map((line) => {
    if (!line || typeof line !== 'object') throw new PackageError('Invalid line');
    const row = line as { type?: unknown; id?: unknown; manufacturers?: unknown };
    const type = row.type;
    const id = typeof row.id === 'string' ? row.id : '';
    if (type !== 'manufacturer' && type !== 'class' && type !== 'bundle') throw new PackageError('Invalid line type');
    if (!/^[a-z0-9-]{1,64}$/.test(id)) throw new PackageError('Invalid package id');
    const manufacturers = Array.isArray(row.manufacturers) ? row.manufacturers.map((item) => String(item)) : undefined;
    return { type, id, manufacturers };
  });
}

export function quoteLines(lines: CartLine[]): Quote {
  if (lines.length === 0) throw new PackageError('Choose at least one package');
  const quoted: QuotedLine[] = [];
  const grants = new Set<string>();
  let bundleCount = 0;

  for (const line of lines) {
    if (line.type === 'manufacturer') {
      const pack = manufacturerById(line.id);
      if (!pack) throw new PackageError(`Unknown manufacturer ${line.id}`);
      if (!isPurchasable(pack) || pack.priceCents == null) throw new PackageError(`${pack.name} is not for sale`);
      if (grants.has(pack.id)) throw new PackageError(`${pack.name} is already in this order`);
      grants.add(pack.id);
      quoted.push({ type: 'manufacturer', id: pack.id, label: pack.name, amountCents: pack.priceCents });
      continue;
    }
    if (line.type === 'class') {
      const pack = classById(line.id);
      if (!pack) throw new PackageError(`Unknown class ${line.id}`);
      if (pack.priceStatus === 'TODO' || !isPurchasable(pack) || pack.priceCents == null) {
        throw new PackageError(`${pack.name} price is TODO`);
      }
      if (grants.has(pack.id)) throw new PackageError(`${pack.name} is already in this order`);
      grants.add(pack.id);
      quoted.push({ type: 'class', id: pack.id, label: pack.name, amountCents: pack.priceCents });
      continue;
    }
    bundleCount += 1;
    if (bundleCount > 1) throw new PackageError('An order can contain one bundle');
    const bundle = bundleById(line.id);
    if (!bundle) throw new PackageError(`Unknown bundle ${line.id}`);
    if (!Number.isInteger(bundle.priceCents) || bundle.priceCents < 0) throw new PackageError(`${bundle.name} has no price`);
    const chosen = expandBundle(bundle, line.manufacturers ?? []);
    for (const id of chosen) {
      if (grants.has(id)) throw new PackageError('That bundle overlaps another line');
      grants.add(id);
    }
    quoted.push({
      type: 'bundle',
      id: bundle.id,
      label: bundle.name,
      amountCents: bundle.priceCents,
      manufacturers: chosen,
    });
  }

  const grantList = [...grants].sort();
  return {
    currency: 'USD',
    amountCents: quoted.reduce((sum, line) => sum + line.amountCents, 0),
    grants: grantList,
    templates: templatesFor(grantList),
    lines: quoted,
  };
}

function expandBundle(bundle: BundleRecord, manufacturers: string[]): string[] {
  if (bundle.kind === 'all') {
    if (manufacturers.length) throw new PackageError('The everything bundle does not take a selection');
    return catalogue.manufacturers.map((pack) => pack.id);
  }
  const count = bundle.count ?? 0;
  if (manufacturers.length !== count) throw new PackageError(`${bundle.name} needs exactly ${count} manufacturers`);
  const unique = [...new Set(manufacturers)];
  if (unique.length !== manufacturers.length) throw new PackageError('Choose each manufacturer once');
  for (const id of unique) {
    const pack = manufacturerById(id);
    if (!pack) throw new PackageError(`Unknown manufacturer ${id}`);
    if (!isPurchasable(pack)) throw new PackageError(`${pack.name} is not for sale`);
  }
  return unique.sort();
}

export function validateCatalogue(): string[] {
  const problems: string[] = [];
  const ids = new Set<string>();
  const free = new Set(['moza_ks', 'ferrari_sf1000', 'mercedes_amg_f1', 'mclaren_ascher_dark']);
  for (const pack of [...catalogue.manufacturers, ...catalogue.classes]) {
    if (ids.has(pack.id)) problems.push(`duplicate id ${pack.id}`);
    ids.add(pack.id);
    if (!/^[a-z0-9-]{1,64}$/.test(pack.id)) problems.push(`bad id ${pack.id}`);
    for (const template of pack.templates) {
      if (free.has(template)) problems.push(`${pack.id} lists free wheel ${template} as paid`);
      if (!packManifest.templates[template]) problems.push(`${pack.id} template ${template} has no pack files`);
    }
    if (pack.priceStatus === 'set' && (typeof pack.priceCents !== 'number' || pack.priceCents < 0)) {
      problems.push(`${pack.id} is marked set without a price`);
    }
    if (pack.priceStatus === 'TODO' && isPurchasable(pack)) problems.push(`${pack.id} TODO pack is purchasable`);
  }
  for (const bundle of catalogue.bundles) {
    if (bundle.kind === 'pick' && !bundle.count) problems.push(`${bundle.id} needs a count`);
  }
  const expected = [399, 999, 1499, 2499, 3499];
  const prices = [catalogue.manufacturerPriceCents, ...catalogue.bundles.map((bundle) => bundle.priceCents)];
  if (prices.join() !== expected.join()) problems.push(`unexpected prices ${prices.join()}`);
  if (catalogue.manufacturers.some((pack) => pack.priceCents !== 399 || pack.priceStatus !== 'set')) {
    problems.push('every manufacturer should be $3.99 and for sale');
  }
  if (catalogue.classes.some((pack) => pack.priceStatus !== 'TODO')) problems.push('class prices must stay TODO');
  return problems;
}
