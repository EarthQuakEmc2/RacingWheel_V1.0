/**
 * Lap cutting and per-corner metrics.
 * Thresholds are shared with pc/Metrics.cs. Change both together.
 *
 * Distances are metres along the lap (normalized position × track length).
 * A positive brake-later delta means this lap braked further down the road
 * than the reference lap.
 */

export const BRAKE_ON = 0.08;
export const BRAKE_OFF = 0.05;
export const STEER_STRAIGHT_DEG = 8;
export const STEER_ZONE_DEG = 12;

export type Sample = {
  tMs: number;
  /** 0..1 along the lap, or a negative number when the source has no track position. */
  pos: number;
  speed: number;
  gas: number;
  brake: number;
  /** Degrees. Positive is one direction, negative the other. */
  steer: number;
  rpm: number;
  gear: number;
};

export type AcFrame = Sample & {
  completedLaps: number;
  isInPit: boolean;
  lastTimeMs: number;
};

export type CornerMetric = {
  index: number;
  name: string;
  startNorm: number | null;
  endNorm: number | null;
  apexNorm: number | null;
  brakePointM: number | null;
  brakePointNorm: number | null;
  peakBrake: number;
  trailBrakeMs: number;
  minSpeedKmh: number | null;
  throttlePickupM: number | null;
  throttlePickupNorm: number | null;
  exitFullThrottlePct: number;
  steeringSmoothness: number;
  coastingMs: number;
  steerReversals: number;
  exitRpm: number;
  exitGear: number;
};

export type SegmentedLap = {
  index: number;
  timeMs: number;
  valid: boolean;
  samples: Sample[];
};

export type CornerDelta = {
  name: string;
  brakeLaterM: number | null;
  peakBrakeDelta: number | null;
  throttleLaterM: number | null;
  minSpeedDeltaKmh: number | null;
  coastingExtraMs: number | null;
  smoothnessDelta: number | null;
  shortShift: boolean;
};

const round = (n: number, places: number) => {
  const p = 10 ** places;
  return Math.round(n * p) / p;
};

export function segmentLaps(frames: AcFrame[]): SegmentedLap[] {
  if (frames.length === 0) return [];
  const laps: SegmentedLap[] = [];
  let current: AcFrame[] = [];
  let seen = frames[0].completedLaps;
  for (const frame of frames) {
    if (frame.completedLaps > seen && current.length > 0) {
      laps.push(closeLap(laps.length + 1, current, frame.lastTimeMs));
      current = [frame];
      seen = frame.completedLaps;
    } else {
      current.push(frame);
    }
  }
  return laps;
}

function closeLap(index: number, frames: AcFrame[], lastTimeMs: number): SegmentedLap {
  const inPit = frames.some((frame) => frame.isInPit);
  const span = positionSpan(frames);
  const valid = !inPit && lastTimeMs >= 20_000 && frames.length >= 8 && (span === null || span > 0.6);
  return {
    index,
    timeMs: lastTimeMs,
    valid,
    samples: frames.map(stripFrame),
  };
}

function positionSpan(frames: AcFrame[]): number | null {
  const pos = frames.map((frame) => frame.pos).filter((value) => value >= 0);
  if (pos.length < 2) return null;
  return Math.max(...pos) - Math.min(...pos);
}

function stripFrame(frame: AcFrame): Sample {
  return {
    tMs: frame.tMs,
    pos: frame.pos,
    speed: frame.speed,
    gas: frame.gas,
    brake: frame.brake,
    steer: frame.steer,
    rpm: frame.rpm,
    gear: frame.gear,
  };
}

export function detectCorners(samples: Sample[], trackLengthM: number | null): CornerMetric[] {
  const count = samples.length;
  if (count < 4) return [];
  const used = new Array<boolean>(count).fill(false);
  const zones: Array<{ i0: number; i1: number }> = [];

  const mark = (start: number, end: number) => {
    for (let index = start; index <= end; index++) used[index] = true;
  };

  let index = 0;
  while (index < count) {
    if (samples[index].brake < BRAKE_ON) {
      index += 1;
      continue;
    }
    const onset = index;
    let cursor = index;
    while (cursor < count && samples[cursor].brake >= BRAKE_OFF) cursor += 1;
    const release = Math.max(onset, cursor - 1);
    const heldMs = samples[release].tMs - samples[onset].tMs;
    if (heldMs < 80 && release - onset < 2) {
      index = cursor;
      continue;
    }
    let start = onset;
    const lookback = samples[onset].tMs - 400;
    while (start > 0 && samples[start - 1].tMs >= lookback) start -= 1;
    let end = release;
    while (end < count - 1) {
      const sample = samples[end];
      const next = samples[end + 1];
      if (sample.gas >= 0.7 && Math.abs(sample.steer) < STEER_STRAIGHT_DEG && next.gas >= 0.7) break;
      if (sample.tMs - samples[release].tMs > 4500) break;
      if (end > release + 1 && next.brake >= BRAKE_ON && sample.brake < BRAKE_ON) break;
      end += 1;
    }
    zones.push({ i0: start, i1: end });
    mark(start, end);
    index = end + 1;
  }

  index = 0;
  while (index < count) {
    if (used[index] || Math.abs(samples[index].steer) < STEER_ZONE_DEG) {
      index += 1;
      continue;
    }
    const start = index;
    let cursor = index;
    while (cursor < count && Math.abs(samples[cursor].steer) >= STEER_STRAIGHT_DEG) cursor += 1;
    const end = Math.max(start, cursor - 1);
    if (samples[end].tMs - samples[start].tMs >= 200) {
      zones.push({ i0: start, i1: end });
      mark(start, end);
    }
    index = end + 1;
  }

  zones.sort((a, b) => a.i0 - b.i0);
  const merged: Array<{ i0: number; i1: number }> = [];
  for (const zone of zones) {
    const last = merged[merged.length - 1];
    if (last && zone.i0 <= last.i1 + 1) last.i1 = Math.max(last.i1, zone.i1);
    else merged.push({ i0: zone.i0, i1: zone.i1 });
  }
  return merged.map((zone, cornerIndex) => metricsFor(samples, zone.i0, zone.i1, cornerIndex + 1, trackLengthM));
}

export function metricsFor(
  samples: Sample[],
  i0: number,
  i1: number,
  index: number,
  trackLengthM: number | null,
): CornerMetric {
  let peakBrake = 0;
  let peakAt = i0;
  let minSpeed = Number.POSITIVE_INFINITY;
  let minAt = i0;
  let hasSpeed = false;
  for (let cursor = i0; cursor <= i1; cursor++) {
    const sample = samples[cursor];
    if (sample.brake > peakBrake) {
      peakBrake = sample.brake;
      peakAt = cursor;
    }
    if (sample.speed > 1) hasSpeed = true;
    if (sample.speed < minSpeed) {
      minSpeed = sample.speed;
      minAt = cursor;
    }
  }

  let brakeAt = -1;
  for (let cursor = i0; cursor <= i1; cursor++) {
    if (samples[cursor].brake >= BRAKE_OFF) {
      brakeAt = cursor;
      break;
    }
  }

  let trailBrakeMs = 0;
  if (peakBrake >= BRAKE_OFF) {
    let cursor = peakAt;
    let steerSum = 0;
    let steerCount = 0;
    while (cursor <= i1 && samples[cursor].brake >= BRAKE_OFF) {
      steerSum += Math.abs(samples[cursor].steer);
      steerCount += 1;
      cursor += 1;
    }
    const meanSteer = steerCount ? steerSum / steerCount : 0;
    if (meanSteer > 4) {
      const endIndex = Math.min(cursor, i1);
      trailBrakeMs = Math.max(0, samples[endIndex].tMs - samples[peakAt].tMs);
    }
  }

  let pickupAt = -1;
  const searchFrom = hasSpeed ? minAt : i0;
  for (let cursor = searchFrom; cursor <= i1; cursor++) {
    if (samples[cursor].gas >= 0.15) {
      pickupAt = cursor;
      break;
    }
  }

  const exitStart = i0 + Math.floor((i1 - i0) * 0.7);
  let full = 0;
  let exitCount = 0;
  let exitRpmSum = 0;
  for (let cursor = exitStart; cursor <= i1; cursor++) {
    exitCount += 1;
    exitRpmSum += samples[cursor].rpm;
    if (samples[cursor].gas >= 0.95) full += 1;
  }
  const exitFullThrottlePct = exitCount ? (100 * full) / exitCount : 0;
  const exitRpm = exitCount ? exitRpmSum / exitCount : 0;

  let absRate = 0;
  let rateCount = 0;
  let reversals = 0;
  let previousRate = 0;
  let havePrevious = false;
  for (let cursor = i0 + 1; cursor <= i1; cursor++) {
    const dt = (samples[cursor].tMs - samples[cursor - 1].tMs) / 1000;
    if (dt <= 0) continue;
    const rate = (samples[cursor].steer - samples[cursor - 1].steer) / dt;
    absRate += Math.abs(rate);
    rateCount += 1;
    if (havePrevious && Math.abs(rate) > 15 && Math.abs(previousRate) > 15 && Math.sign(rate) !== Math.sign(previousRate)) {
      reversals += 1;
    }
    previousRate = rate;
    havePrevious = true;
  }
  const meanRate = rateCount ? absRate / rateCount : 0;
  const steeringSmoothness = clamp(100 - meanRate * 0.35, 0, 100);

  let coastingMs = 0;
  for (let cursor = i0 + 1; cursor <= i1; cursor++) {
    const sample = samples[cursor];
    if (sample.gas < 0.05 && sample.brake < 0.05) coastingMs += sample.tMs - samples[cursor - 1].tMs;
  }

  const position = (at: number) => (samples[at].pos >= 0 ? samples[at].pos : null);
  const metres = (pos: number | null) => (pos != null && trackLengthM != null && trackLengthM > 0 ? round(pos * trackLengthM, 1) : null);
  const brakePos = brakeAt >= 0 ? position(brakeAt) : null;
  const pickupPos = pickupAt >= 0 ? position(pickupAt) : null;
  const apexPos = hasSpeed ? position(minAt) : position(Math.floor((i0 + i1) / 2));

  return {
    index,
    name: `T${index}`,
    startNorm: roundOrNull(position(i0), 4),
    endNorm: roundOrNull(position(i1), 4),
    apexNorm: roundOrNull(apexPos, 4),
    brakePointM: metres(brakePos),
    brakePointNorm: roundOrNull(brakePos, 4),
    peakBrake: round(peakBrake, 3),
    trailBrakeMs: Math.round(trailBrakeMs),
    minSpeedKmh: hasSpeed ? round(minSpeed, 1) : null,
    throttlePickupM: metres(pickupPos),
    throttlePickupNorm: roundOrNull(pickupPos, 4),
    exitFullThrottlePct: round(exitFullThrottlePct, 1),
    steeringSmoothness: round(steeringSmoothness, 1),
    coastingMs: Math.round(coastingMs),
    steerReversals: reversals,
    exitRpm: Math.round(exitRpm),
    exitGear: samples[i1].gear,
  };
}

function roundOrNull(value: number | null, places: number) {
  return value == null ? null : round(value, places);
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

export function decimate(samples: Sample[], maxPoints: number): Sample[] {
  if (samples.length <= maxPoints) return samples.slice();
  const step = samples.length / maxPoints;
  const out: Sample[] = [];
  for (let index = 0; index < maxPoints; index++) {
    out.push(samples[Math.min(samples.length - 1, Math.floor(index * step))]);
  }
  out[out.length - 1] = samples[samples.length - 1];
  return out;
}

export function compareCorners(current: CornerMetric[], reference: CornerMetric[]): CornerDelta[] {
  return current.map((corner, index) => {
    const match = matchCorner(corner, index, reference);
    if (!match) {
      return emptyDelta(corner.name);
    }
    const brakeLaterM = both(corner.brakePointM, match.brakePointM);
    const throttleLaterM = both(corner.throttlePickupM, match.throttlePickupM);
    const shortShift = corner.exitGear > match.exitGear && match.exitRpm > 0 && corner.exitRpm < match.exitRpm * 0.85;
    return {
      name: corner.name,
      brakeLaterM: brakeLaterM == null ? null : round(brakeLaterM, 1),
      peakBrakeDelta: round(corner.peakBrake - match.peakBrake, 3),
      throttleLaterM: throttleLaterM == null ? null : round(throttleLaterM, 1),
      minSpeedDeltaKmh:
        corner.minSpeedKmh != null && match.minSpeedKmh != null ? round(corner.minSpeedKmh - match.minSpeedKmh, 1) : null,
      coastingExtraMs: corner.coastingMs - match.coastingMs,
      smoothnessDelta: round(corner.steeringSmoothness - match.steeringSmoothness, 1),
      shortShift,
    };
  });
}

function both(current: number | null, reference: number | null) {
  if (current == null || reference == null) return null;
  return current - reference;
}

function matchCorner(corner: CornerMetric, index: number, reference: CornerMetric[]) {
  if (corner.apexNorm == null) return reference[index] ?? null;
  let best: CornerMetric | null = null;
  let bestDist = 0.05;
  for (const candidate of reference) {
    if (candidate.apexNorm == null) continue;
    const dist = Math.abs(candidate.apexNorm - corner.apexNorm);
    if (dist <= bestDist) {
      best = candidate;
      bestDist = dist;
    }
  }
  return best ?? reference[index] ?? null;
}

function emptyDelta(name: string): CornerDelta {
  return {
    name,
    brakeLaterM: null,
    peakBrakeDelta: null,
    throttleLaterM: null,
    minSpeedDeltaKmh: null,
    coastingExtraMs: null,
    smoothnessDelta: null,
    shortShift: false,
  };
}

/** Deterministic lines. The model is asked to write in this voice; tests lock the wording. */
export function adviceForCorner(delta: CornerDelta): string[] {
  const lines: string[] = [];
  const later = delta.brakeLaterM;
  const peak = delta.peakBrakeDelta;
  if (later != null && later >= 10 && peak != null && peak >= 0.1) {
    lines.push(`Brake ${Math.round(later)} m earlier but ${Math.round(peak * 100)}% softer into ${delta.name}`);
  } else if (later != null && later >= 10) {
    lines.push(`Brake ${Math.round(later)} m earlier into ${delta.name}`);
  } else if (later != null && later <= -10) {
    lines.push(`Brake ${Math.round(Math.abs(later))} m later into ${delta.name}`);
  } else if (peak != null && peak <= -0.15) {
    lines.push(`Brake harder into ${delta.name}; peak brake is ${Math.round(Math.abs(peak) * 100)}% under your best`);
  } else if (peak != null && peak >= 0.15) {
    lines.push(`Brake ${Math.round(peak * 100)}% softer into ${delta.name}`);
  }
  if (delta.throttleLaterM != null && delta.throttleLaterM >= 8) {
    lines.push(`Pick up throttle earlier on the exit of ${delta.name}`);
  }
  if (delta.shortShift) lines.push(`Keep revs high on exit of ${delta.name}`);
  if (delta.coastingExtraMs != null && delta.coastingExtraMs >= 150) {
    lines.push(`Less coasting through ${delta.name}; blend off the brake as you roll on throttle`);
  }
  if (delta.smoothnessDelta != null && delta.smoothnessDelta <= -15) {
    lines.push(`Smoother steering through ${delta.name}; fewer mid-corner corrections`);
  }
  if (delta.minSpeedDeltaKmh != null && delta.minSpeedDeltaKmh <= -8 && (later == null || later < 10)) {
    lines.push(`Carry more speed through ${delta.name}; minimum speed is ${Math.round(Math.abs(delta.minSpeedDeltaKmh))} km/h under your best`);
  }
  return lines;
}

export function adviceForLap(current: CornerMetric[], reference: CornerMetric[]): string[] {
  return compareCorners(current, reference).flatMap(adviceForCorner);
}

export type CoachContext = {
  track: string;
  layout: string;
  car: string;
  trackLengthM: number | null;
  subject: { index: number; timeMs: number | null; corners: CornerMetric[] };
  reference: { index: number; timeMs: number | null; corners: CornerMetric[] } | null;
};

export function coachBrief(context: CoachContext): { rules: string[]; prompt: string } {
  const rules = context.reference ? adviceForLap(context.subject.corners, context.reference.corners) : absoluteHints(context.subject.corners);
  const payload = {
    track: context.track,
    layout: context.layout,
    car: context.car,
    trackLengthM: context.trackLengthM,
    subjectLap: context.subject,
    bestLap: context.reference,
    measuredObservations: rules,
  };
  const prompt = [
    "You are the driver's coach for one Assetto Corsa session.",
    'Use only the corner metrics in the JSON. Do not invent a brake point, a kerb, or a gear change that is not supported by those numbers.',
    'Write 3 to 8 short lines the driver can apply on the next lap. Name the corner (T1, T2, …).',
    'Voice examples: "Brake 15 m earlier but 20% softer into T1", "Pick up throttle earlier on the exit of T2", "Keep revs high on exit of T3".',
    'If measuredObservations is empty, say the lap is close to the reference and mention one thing to protect.',
    'No preamble and no markdown heading.',
    JSON.stringify(payload),
  ].join('\n');
  return { rules, prompt };
}

function absoluteHints(corners: CornerMetric[]): string[] {
  const lines: string[] = [];
  for (const corner of corners) {
    if (corner.coastingMs >= 300) lines.push(`Less coasting through ${corner.name}; blend off the brake as you roll on throttle`);
    if (corner.steeringSmoothness < 45) lines.push(`Smoother steering through ${corner.name}; fewer mid-corner corrections`);
    if (corner.peakBrake >= 0.5 && corner.trailBrakeMs === 0) {
      lines.push(`Trail off the brake into the apex of ${corner.name} instead of releasing it all at once`);
    }
  }
  return lines;
}

export function chooseCoachLaps<T extends { index: number; timeMs: number | null; valid: boolean; corners: CornerMetric[] }>(laps: T[]) {
  const valid = laps.filter((lap) => lap.valid && lap.timeMs != null && lap.timeMs > 0);
  if (valid.length === 0) return null;
  const best = valid.reduce((left, right) => (right.timeMs! < left.timeMs! ? right : left));
  const later = [...valid].reverse().find((lap) => lap.index !== best.index) ?? best;
  return { subject: later, reference: later.index === best.index ? null : best };
}
