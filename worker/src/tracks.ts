/** Official Assetto Corsa tracks. `folder` and layout `id` match the game's shared-memory fields. */

export type TrackLayout = { id: string; name: string; lengthM?: number };

export type TrackInfo = {
  folder: string;
  name: string;
  dlc: boolean;
  layouts: TrackLayout[];
};

export const TRACKS: TrackInfo[] = [
  { folder: 'magione', name: 'Magione', dlc: false, layouts: [{ id: '', name: 'Full', lengthM: 2507 }] },
  { folder: 'imola', name: 'Imola', dlc: false, layouts: [{ id: '', name: 'Grand Prix', lengthM: 4909 }] },
  { folder: 'mugello', name: 'Mugello', dlc: false, layouts: [{ id: '', name: 'Full', lengthM: 5245 }] },
  { folder: 'monza', name: 'Monza', dlc: false, layouts: [{ id: '', name: 'Grand Prix', lengthM: 5793 }] },
  {
    folder: 'ks_monza66',
    name: 'Monza 1966',
    dlc: false,
    layouts: [
      { id: 'full', name: 'Full' },
      { id: 'junior', name: 'Junior' },
      { id: 'road', name: 'Road' },
    ],
  },
  {
    folder: 'ks_vallelunga',
    name: 'Vallelunga',
    dlc: false,
    layouts: [
      { id: 'extended_circuit', name: 'Extended', lengthM: 4085 },
      { id: 'club_circuit', name: 'Club', lengthM: 1746 },
      { id: 'classic_circuit', name: 'Classic', lengthM: 3222 },
    ],
  },
  {
    folder: 'ks_black_cat_county',
    name: 'Black Cat County',
    dlc: false,
    layouts: [
      { id: 'layout_long', name: 'Long' },
      { id: 'layout_int', name: 'Intermediate' },
      { id: 'layout_short', name: 'Short' },
    ],
  },
  { folder: 'spa', name: 'Spa-Francorchamps', dlc: false, layouts: [{ id: '', name: 'Grand Prix', lengthM: 7004 }] },
  { folder: 'ks_zandvoort', name: 'Zandvoort', dlc: false, layouts: [{ id: '', name: 'Grand Prix', lengthM: 4320 }] },
  {
    folder: 'ks_drag',
    name: 'Drag Strip',
    dlc: false,
    layouts: [
      { id: 'drag200', name: '200 m', lengthM: 200 },
      { id: 'drag400', name: '400 m', lengthM: 400 },
      { id: 'drag500', name: '500 m', lengthM: 500 },
      { id: 'drag1000', name: '1000 m', lengthM: 1000 },
      { id: 'drag2000', name: '2000 m', lengthM: 2000 },
    ],
  },
  { folder: 'drift', name: 'Drift', dlc: false, layouts: [{ id: '', name: 'Full' }] },
  {
    folder: 'ks_highlands',
    name: 'Highlands',
    dlc: false,
    layouts: [
      { id: 'layout_long', name: 'Long' },
      { id: 'layout_int', name: 'Intermediate' },
      { id: 'layout_short', name: 'Short' },
      { id: 'layout_drift', name: 'Drift' },
    ],
  },
  { folder: 'ks_laguna_seca', name: 'Laguna Seca', dlc: false, layouts: [{ id: '', name: 'Full', lengthM: 3602 }] },
  {
    folder: 'ks_nurburgring',
    name: 'Nürburgring GP',
    dlc: false,
    layouts: [
      { id: 'layout_gp_a', name: 'GP A', lengthM: 5148 },
      { id: 'layout_gp_b', name: 'GP B', lengthM: 5148 },
      { id: 'layout_sprint_a', name: 'Sprint A', lengthM: 3619 },
      { id: 'layout_sprint_b', name: 'Sprint B', lengthM: 3619 },
    ],
  },
  {
    folder: 'ks_silverstone',
    name: 'Silverstone',
    dlc: false,
    layouts: [
      { id: 'gp', name: 'GP', lengthM: 5891 },
      { id: 'international', name: 'International', lengthM: 2979 },
      { id: 'national', name: 'National', lengthM: 2636 },
    ],
  },
  { folder: 'ks_silverstone1967', name: 'Silverstone 1967', dlc: false, layouts: [{ id: '', name: 'Full' }] },
  { folder: 'trento-bondone', name: 'Trento-Bondone', dlc: false, layouts: [{ id: '', name: 'Hillclimb', lengthM: 17200 }] },
  {
    folder: 'ks_nordschleife',
    name: 'Nürburgring Nordschleife',
    dlc: true,
    layouts: [
      { id: 'nordschleife', name: 'Nordschleife', lengthM: 20832 },
      { id: 'touristenfahrten', name: 'Tourist', lengthM: 19100 },
      { id: 'endurance', name: 'Endurance', lengthM: 25378 },
    ],
  },
  {
    folder: 'ks_barcelona',
    name: 'Barcelona-Catalunya',
    dlc: true,
    layouts: [
      { id: 'layout_gp', name: 'GP', lengthM: 4655 },
      { id: 'layout_moto', name: 'MotoGP', lengthM: 4655 },
    ],
  },
  {
    folder: 'ks_brands_hatch',
    name: 'Brands Hatch',
    dlc: true,
    layouts: [
      { id: 'gp', name: 'GP', lengthM: 3908 },
      { id: 'indy', name: 'Indy', lengthM: 1929 },
    ],
  },
  {
    folder: 'ks_red_bull_ring',
    name: 'Red Bull Ring',
    dlc: true,
    layouts: [
      { id: 'layout_gp', name: 'GP', lengthM: 4318 },
      { id: 'layout_national', name: 'National', lengthM: 2336 },
    ],
  },
];

export function findTrack(folder: string) {
  return TRACKS.find((track) => track.folder === folder) ?? null;
}

export function trackLabel(folder: string, layout: string) {
  const track = findTrack(folder);
  if (!track) return layout ? `${folder} / ${layout}` : folder;
  const found = track.layouts.find((item) => item.id === layout);
  const layoutName = found?.name ?? (layout || track.layouts[0]?.name || '');
  return layoutName && track.layouts.length > 1 ? `${track.name} · ${layoutName}` : track.name;
}

export function catalogLength(folder: string, layout: string) {
  const track = findTrack(folder);
  if (!track) return null;
  const found = track.layouts.find((item) => item.id === layout) ?? (layout === '' ? track.layouts[0] : null);
  return found?.lengthM ?? null;
}
