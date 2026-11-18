import type { Sample } from './metrics';

export type ParsedUpload = {
  source: 'assetto-corsa' | 'wheel' | 'upload';
  startedAt: string | null;
  track: string;
  layout: string;
  car: string;
  trackLengthM: number | null;
  player: string;
  laps: Array<{ index: number; timeMs: number | null; valid: boolean; samples: Sample[]; corners?: unknown }>;
};

const SCHEMA = 'racing-wheel-pro.session/v1';

export function parseUpload(text: string): ParsedUpload {
  const trimmed = text.trim();
  if (!trimmed) throw new Error('The file is empty');
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) return parseJson(trimmed);
  return parseTable(trimmed);
}

function parseJson(text: string): ParsedUpload {
  const raw = JSON.parse(text) as Record<string, unknown>;
  if (raw.schema && raw.schema !== SCHEMA) {
    throw new Error(`Unknown log schema: ${String(raw.schema)}`);
  }
  const lapsRaw = Array.isArray(raw.laps) ? raw.laps : [];
  if (lapsRaw.length === 0 && Array.isArray(raw.samples)) {
    lapsRaw.push({ index: 1, samples: raw.samples, valid: true, timeMs: raw.timeMs ?? null });
  }
  const laps = lapsRaw.map((lap, index) => normalizeLap(lap as Record<string, unknown>, index));
  if (laps.length === 0) throw new Error('The log has no laps');
  return {
    source: sourceOf(raw.source),
    startedAt: typeof raw.startedAt === 'string' ? raw.startedAt : null,
    track: textField(raw.track, 'unknown'),
    layout: textField(raw.layout, ''),
    car: textField(raw.car, 'unknown'),
    trackLengthM: numberOrNull(raw.trackLengthM),
    player: textField(raw.player, ''),
    laps,
  };
}

function normalizeLap(raw: Record<string, unknown>, index: number) {
  const samples = normalizeSamples(raw.samples);
  if (samples.length === 0) throw new Error(`Lap ${index + 1} has no samples`);
  return {
    index: numberOrNull(raw.index) ?? index + 1,
    timeMs: numberOrNull(raw.timeMs),
    valid: raw.valid !== false,
    samples,
    corners: raw.corners,
  };
}

export function normalizeSamples(value: unknown): Sample[] {
  if (!Array.isArray(value)) return [];
  const out: Sample[] = [];
  for (const item of value) {
    if (Array.isArray(item)) {
      const [tMs, pos, speed, gas, brake, steer, rpm, gear] = item;
      out.push({
        tMs: num(tMs),
        pos: num(pos),
        speed: num(speed),
        gas: num(gas),
        brake: num(brake),
        steer: num(steer),
        rpm: num(rpm),
        gear: num(gear),
      });
    } else if (item && typeof item === 'object') {
      const row = item as Record<string, unknown>;
      out.push({
        tMs: num(row.tMs ?? row.t),
        pos: num(row.pos ?? row.normalized ?? -1),
        speed: num(row.speed ?? row.speedKmh),
        gas: num(row.gas ?? row.throttle),
        brake: num(row.brake),
        steer: num(row.steer ?? row.steerDeg),
        rpm: num(row.rpm),
        gear: num(row.gear),
      });
    }
  }
  return out;
}

function parseTable(text: string): ParsedUpload {
  const lines = text.split(/\r?\n/).filter((line) => line.trim().length > 0 && !line.trim().startsWith('#'));
  let headerAt = -1;
  let delimiter = '\t';
  for (let index = 0; index < lines.length; index++) {
    const tabs = (lines[index].match(/\t/g) || []).length;
    const commas = (lines[index].match(/,/g) || []).length;
    const delim = tabs >= commas ? '\t' : ',';
    const cells = splitRow(lines[index], delim).map(normHeader);
    if (cells.some((cell) => cell.includes('speed')) && cells.some((cell) => cell.includes('brake') || cell.includes('throttle') || cell === 'gas')) {
      headerAt = index;
      delimiter = delim;
      break;
    }
  }
  if (headerAt < 0) throw new Error('No telemetry header found. Expected our JSON log or a CSV with Speed and Brake columns.');
  const headers = splitRow(lines[headerAt], delimiter).map(normHeader);
  const col = (...names: string[]) => headers.findIndex((header) => names.some((name) => header === name || header.includes(name)));
  const iTime = col('time', 'timestamp');
  const iSpeed = col('speed');
  const iGas = col('throttle', 'gas', 'accel');
  const iBrake = col('brake');
  const iSteer = col('steering', 'steer');
  const iRpm = col('rpm');
  const iGear = col('gear');
  const iPos = col('normalized', 'splineposition', 'lapdistpct');
  const iDist = col('distance', 'lapdistance');
  const iLap = col('lap');
  const rows: Array<Sample & { lap: number; dist: number }> = [];
  for (const line of lines.slice(headerAt + 1)) {
    const cells = splitRow(line, delimiter);
    if (cells.length < 2) continue;
    const at = (index: number) => (index >= 0 ? Number(cells[index]) : NaN);
    rows.push({
      tMs: num(at(iTime)),
      pos: iPos >= 0 ? num(at(iPos)) : -1,
      speed: num(at(iSpeed)),
      gas: num(at(iGas)),
      brake: num(at(iBrake)),
      steer: num(at(iSteer)),
      rpm: num(at(iRpm)),
      gear: num(at(iGear)),
      lap: iLap >= 0 && Number.isFinite(Number(cells[iLap])) ? Number(cells[iLap]) : 1,
      dist: iDist >= 0 && iDist !== iPos ? num(at(iDist)) : NaN,
    });
  }
  if (rows.length === 0) throw new Error('The CSV has a header but no samples');
  scaleTable(rows);
  const byLap = new Map<number, Sample[]>();
  for (const row of rows) {
    const list = byLap.get(row.lap) ?? [];
    list.push({ tMs: row.tMs, pos: row.pos, speed: row.speed, gas: row.gas, brake: row.brake, steer: row.steer, rpm: row.rpm, gear: row.gear });
    byLap.set(row.lap, list);
  }
  const laps = [...byLap.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([lap, samples], index) => ({
      index: Number.isFinite(lap) ? lap : index + 1,
      timeMs: samples.length > 1 ? Math.round(samples[samples.length - 1].tMs - samples[0].tMs) : null,
      valid: true,
      samples,
    }));
  const meta = readMeta(lines.slice(0, headerAt));
  return {
    source: 'upload',
    startedAt: null,
    track: meta.track || 'unknown',
    layout: meta.layout || '',
    car: meta.car || 'unknown',
    trackLengthM: meta.lengthM,
    player: '',
    laps,
  };
}

function scaleTable(rows: Array<Sample & { lap: number; dist: number }>) {
  const maxSpeed = Math.max(...rows.map((row) => row.speed));
  const speedScale = maxSpeed > 0 && maxSpeed < 90 ? 3.6 : 1;
  const maxPedal = Math.max(...rows.map((row) => Math.max(row.gas, row.brake)));
  const pedalScale = maxPedal > 1.5 ? 100 : 1;
  const maxSteer = Math.max(...rows.map((row) => Math.abs(row.steer)));
  const steerScale = maxSteer > 0 && maxSteer <= 1.5 ? 90 : 1;
  const maxTime = Math.max(...rows.map((row) => row.tMs));
  const timeScale = maxTime > 0 && maxTime < 20000 && rows.some((row) => !Number.isInteger(row.tMs)) ? 1000 : 1;
  const maxByLap = new Map<number, number>();
  for (const row of rows) {
    if (!Number.isFinite(row.dist)) continue;
    maxByLap.set(row.lap, Math.max(maxByLap.get(row.lap) ?? 0, row.dist));
  }
  for (const row of rows) {
    row.tMs *= timeScale;
    row.speed *= speedScale;
    row.gas = clamp01(row.gas / pedalScale);
    row.brake = clamp01(row.brake / pedalScale);
    row.steer *= steerScale;
    const maxDist = maxByLap.get(row.lap) ?? 0;
    if (row.pos < 0 && maxDist > 50 && Number.isFinite(row.dist)) row.pos = clamp01(row.dist / maxDist);
  }
}

function readMeta(lines: string[]) {
  const meta = { track: '', layout: '', car: '', lengthM: null as number | null };
  for (const line of lines) {
    const match = line.match(/^\s*([^:,\t]+)\s*[:=]\s*(.+)$/);
    if (!match) continue;
    const key = match[1].trim().toLowerCase();
    const value = match[2].trim();
    if (key.includes('track') && !key.includes('config')) meta.track = value;
    else if (key.includes('config') || key.includes('layout')) meta.layout = value;
    else if (key.includes('car') || key.includes('vehicle')) meta.car = value;
    else if (key.includes('length')) meta.lengthM = Number(value) || null;
  }
  return meta;
}

function splitRow(line: string, delimiter: string) {
  if (delimiter !== ',') return line.split(delimiter).map((cell) => cell.trim());
  return line.split(',').map((cell) => cell.trim().replace(/^"|"$/g, ''));
}

function normHeader(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function sourceOf(value: unknown): ParsedUpload['source'] {
  if (value === 'assetto-corsa' || value === 'wheel' || value === 'upload') return value;
  return 'upload';
}

function textField(value: unknown, fallback: string) {
  if (typeof value !== 'string') return fallback;
  const trimmed = value.trim();
  return trimmed ? trimmed.slice(0, 120) : fallback;
}

function numberOrNull(value: unknown) {
  const n = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN;
  return Number.isFinite(n) ? n : null;
}

function num(value: unknown) {
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : 0;
}

function clamp01(value: number) {
  if (!Number.isFinite(value)) return 0;
  return Math.min(1, Math.max(0, value));
}

export { SCHEMA };
