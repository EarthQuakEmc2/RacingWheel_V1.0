using System.Text.Json.Serialization;

namespace RacingWheelPro.Pc;

/// <summary>
/// Per-corner metrics. Thresholds match worker/src/metrics.ts.
/// </summary>
public static class CornerMath
{
    public const double BrakeOn = 0.08;
    public const double BrakeOff = 0.05;
    public const double SteerStraightDeg = 8;
    public const double SteerZoneDeg = 12;

    public static List<CornerMetric> Detect(IReadOnlyList<Sample> samples, double? trackLengthM)
    {
        var count = samples.Count;
        var zones = new List<(int I0, int I1)>();
        if (count < 4) return new List<CornerMetric>();
        var used = new bool[count];
        var index = 0;
        while (index < count)
        {
            if (samples[index].Brake < BrakeOn)
            {
                index++;
                continue;
            }
            var onset = index;
            var cursor = index;
            while (cursor < count && samples[cursor].Brake >= BrakeOff) cursor++;
            var release = Math.Max(onset, cursor - 1);
            var held = samples[release].TMs - samples[onset].TMs;
            if (held < 80 && release - onset < 2)
            {
                index = cursor;
                continue;
            }
            var start = onset;
            var lookback = samples[onset].TMs - 400;
            while (start > 0 && samples[start - 1].TMs >= lookback) start--;
            var end = release;
            while (end < count - 1)
            {
                var sample = samples[end];
                var next = samples[end + 1];
                if (sample.Gas >= 0.7 && Math.Abs(sample.Steer) < SteerStraightDeg && next.Gas >= 0.7) break;
                if (sample.TMs - samples[release].TMs > 4500) break;
                if (end > release + 1 && next.Brake >= BrakeOn && sample.Brake < BrakeOn) break;
                end++;
            }
            zones.Add((start, end));
            for (var mark = start; mark <= end; mark++) used[mark] = true;
            index = end + 1;
        }

        index = 0;
        while (index < count)
        {
            if (used[index] || Math.Abs(samples[index].Steer) < SteerZoneDeg)
            {
                index++;
                continue;
            }
            var start = index;
            var cursor = index;
            while (cursor < count && Math.Abs(samples[cursor].Steer) >= SteerStraightDeg) cursor++;
            var end = Math.Max(start, cursor - 1);
            if (samples[end].TMs - samples[start].TMs >= 200)
            {
                zones.Add((start, end));
                for (var mark = start; mark <= end; mark++) used[mark] = true;
            }
            index = end + 1;
        }

        zones.Sort((a, b) => a.I0.CompareTo(b.I0));
        var merged = new List<(int I0, int I1)>();
        foreach (var zone in zones)
        {
            if (merged.Count > 0 && zone.I0 <= merged[^1].I1 + 1)
            {
                var last = merged[^1];
                merged[^1] = (last.I0, Math.Max(last.I1, zone.I1));
            }
            else merged.Add(zone);
        }
        var corners = new List<CornerMetric>();
        for (var i = 0; i < merged.Count; i++) corners.Add(Measure(samples, merged[i].I0, merged[i].I1, i + 1, trackLengthM));
        return corners;
    }

    public static CornerMetric Measure(IReadOnlyList<Sample> samples, int i0, int i1, int index, double? trackLengthM)
    {
        double peak = 0;
        var peakAt = i0;
        var minSpeed = double.PositiveInfinity;
        var minAt = i0;
        var hasSpeed = false;
        for (var cursor = i0; cursor <= i1; cursor++)
        {
            var sample = samples[cursor];
            if (sample.Brake > peak)
            {
                peak = sample.Brake;
                peakAt = cursor;
            }
            if (sample.Speed > 1) hasSpeed = true;
            if (sample.Speed < minSpeed)
            {
                minSpeed = sample.Speed;
                minAt = cursor;
            }
        }
        var brakeAt = -1;
        for (var cursor = i0; cursor <= i1; cursor++)
        {
            if (samples[cursor].Brake >= BrakeOff)
            {
                brakeAt = cursor;
                break;
            }
        }
        double trail = 0;
        if (peak >= BrakeOff)
        {
            var cursor = peakAt;
            double steerSum = 0;
            var steerCount = 0;
            while (cursor <= i1 && samples[cursor].Brake >= BrakeOff)
            {
                steerSum += Math.Abs(samples[cursor].Steer);
                steerCount++;
                cursor++;
            }
            var mean = steerCount == 0 ? 0 : steerSum / steerCount;
            if (mean > 4)
            {
                var endIndex = Math.Min(cursor, i1);
                trail = Math.Max(0, samples[endIndex].TMs - samples[peakAt].TMs);
            }
        }
        var pickupAt = -1;
        var searchFrom = hasSpeed ? minAt : i0;
        for (var cursor = searchFrom; cursor <= i1; cursor++)
        {
            if (samples[cursor].Gas >= 0.15)
            {
                pickupAt = cursor;
                break;
            }
        }
        var exitStart = i0 + (int)Math.Floor((i1 - i0) * 0.7);
        var full = 0;
        var exitCount = 0;
        double exitRpm = 0;
        for (var cursor = exitStart; cursor <= i1; cursor++)
        {
            exitCount++;
            exitRpm += samples[cursor].Rpm;
            if (samples[cursor].Gas >= 0.95) full++;
        }
        double absRate = 0;
        var rateCount = 0;
        var reversals = 0;
        double previous = 0;
        var havePrevious = false;
        for (var cursor = i0 + 1; cursor <= i1; cursor++)
        {
            var dt = (samples[cursor].TMs - samples[cursor - 1].TMs) / 1000.0;
            if (dt <= 0) continue;
            var rate = (samples[cursor].Steer - samples[cursor - 1].Steer) / dt;
            absRate += Math.Abs(rate);
            rateCount++;
            if (havePrevious && Math.Abs(rate) > 15 && Math.Abs(previous) > 15 && Math.Sign(rate) != Math.Sign(previous)) reversals++;
            previous = rate;
            havePrevious = true;
        }
        var meanRate = rateCount == 0 ? 0 : absRate / rateCount;
        var smoothness = Math.Clamp(100 - meanRate * 0.35, 0, 100);
        double coast = 0;
        for (var cursor = i0 + 1; cursor <= i1; cursor++)
        {
            var sample = samples[cursor];
            if (sample.Gas < 0.05 && sample.Brake < 0.05) coast += sample.TMs - samples[cursor - 1].TMs;
        }
        double? Pos(int at) => samples[at].Pos >= 0 ? samples[at].Pos : null;
        double? Metres(double? pos) => pos is double value && trackLengthM is double length && length > 0 ? Math.Round(value * length, 1) : null;
        var brakePos = brakeAt >= 0 ? Pos(brakeAt) : null;
        var pickupPos = pickupAt >= 0 ? Pos(pickupAt) : null;
        var apex = hasSpeed ? Pos(minAt) : Pos((i0 + i1) / 2);
        return new CornerMetric
        {
            Index = index,
            Name = "T" + index,
            StartNorm = RoundOrNull(Pos(i0), 4),
            EndNorm = RoundOrNull(Pos(i1), 4),
            ApexNorm = RoundOrNull(apex, 4),
            BrakePointM = Metres(brakePos),
            BrakePointNorm = RoundOrNull(brakePos, 4),
            PeakBrake = Math.Round(peak, 3),
            TrailBrakeMs = (int)Math.Round(trail),
            MinSpeedKmh = hasSpeed ? Math.Round(minSpeed, 1) : null,
            ThrottlePickupM = Metres(pickupPos),
            ThrottlePickupNorm = RoundOrNull(pickupPos, 4),
            ExitFullThrottlePct = Math.Round(exitCount == 0 ? 0 : 100.0 * full / exitCount, 1),
            SteeringSmoothness = Math.Round(smoothness, 1),
            CoastingMs = (int)Math.Round(coast),
            SteerReversals = reversals,
            ExitRpm = (int)Math.Round(exitCount == 0 ? 0 : exitRpm / exitCount),
            ExitGear = (int)Math.Round(samples[i1].Gear),
        };
    }

    private static double? RoundOrNull(double? value, int places) => value is double number ? Math.Round(number, places) : null;

    public static List<LapDraft> SegmentLaps(IReadOnlyList<AcFrame> frames)
    {
        var laps = new List<LapDraft>();
        if (frames.Count == 0) return laps;
        var current = new List<AcFrame>();
        var seen = frames[0].CompletedLaps;
        foreach (var frame in frames)
        {
            if (frame.CompletedLaps > seen && current.Count > 0)
            {
                laps.Add(Close(laps.Count + 1, current, frame.LastTimeMs));
                current = new List<AcFrame> { frame };
                seen = frame.CompletedLaps;
            }
            else current.Add(frame);
        }
        return laps;
    }

    private static LapDraft Close(int index, List<AcFrame> frames, double lastTimeMs)
    {
        var inPit = frames.Any(frame => frame.IsInPit);
        double? span = null;
        var positions = frames.Where(frame => frame.Pos >= 0).Select(frame => frame.Pos).ToList();
        if (positions.Count >= 2) span = positions.Max() - positions.Min();
        var valid = !inPit && lastTimeMs >= 20000 && frames.Count >= 8 && (span == null || span > 0.6);
        return new LapDraft
        {
            Index = index,
            TimeMs = (int)Math.Round(lastTimeMs),
            Valid = valid,
            Samples = frames.Select(frame => frame.ToSample()).ToList(),
        };
    }
}

public sealed class Sample
{
    [JsonPropertyName("tMs")] public double TMs { get; set; }
    [JsonPropertyName("pos")] public double Pos { get; set; }
    [JsonPropertyName("speed")] public double Speed { get; set; }
    [JsonPropertyName("gas")] public double Gas { get; set; }
    [JsonPropertyName("brake")] public double Brake { get; set; }
    [JsonPropertyName("steer")] public double Steer { get; set; }
    [JsonPropertyName("rpm")] public double Rpm { get; set; }
    [JsonPropertyName("gear")] public double Gear { get; set; }

    public double[] ToArray() => new[] { TMs, Pos, Speed, Gas, Brake, Steer, Rpm, Gear };
}

public sealed class AcFrame
{
    public double TMs { get; init; }
    public double Pos { get; init; }
    public double Speed { get; init; }
    public double Gas { get; init; }
    public double Brake { get; init; }
    public double Steer { get; init; }
    public double Rpm { get; init; }
    public double Gear { get; init; }
    public int CompletedLaps { get; set; }
    public bool IsInPit { get; set; }
    public double LastTimeMs { get; set; }
    public Sample ToSample() => new()
    {
        TMs = TMs, Pos = Pos, Speed = Speed, Gas = Gas, Brake = Brake, Steer = Steer, Rpm = Rpm, Gear = Gear,
    };
}

public sealed class CornerMetric
{
    public int Index { get; set; }
    public string Name { get; set; } = "";
    public double? StartNorm { get; set; }
    public double? EndNorm { get; set; }
    public double? ApexNorm { get; set; }
    public double? BrakePointM { get; set; }
    public double? BrakePointNorm { get; set; }
    public double PeakBrake { get; set; }
    public int TrailBrakeMs { get; set; }
    public double? MinSpeedKmh { get; set; }
    public double? ThrottlePickupM { get; set; }
    public double? ThrottlePickupNorm { get; set; }
    public double ExitFullThrottlePct { get; set; }
    public double SteeringSmoothness { get; set; }
    public int CoastingMs { get; set; }
    public int SteerReversals { get; set; }
    public int ExitRpm { get; set; }
    public int ExitGear { get; set; }
}

public sealed class LapDraft
{
    public int Index { get; set; }
    public int TimeMs { get; set; }
    public bool Valid { get; set; }
    public List<Sample> Samples { get; set; } = new();
    public List<CornerMetric> Corners { get; set; } = new();
}
