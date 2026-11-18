using System.IO.Compression;
using System.Text.Json;
using RacingWheelPro.Pc;
using Xunit;

namespace RacingWheelPro.PC.Tests;

public class MetricTests
{
    [Fact]
    public void OneBrakedCornerMatchesTheSharedFixture()
    {
        var samples = new List<Sample>
        {
            new() { TMs = 0, Pos = 0.1, Speed = 200, Gas = 1, Brake = 0, Steer = 0, Rpm = 7000, Gear = 4 },
            new() { TMs = 200, Pos = 0.2, Speed = 200, Gas = 1, Brake = 0, Steer = 1, Rpm = 7000, Gear = 4 },
            new() { TMs = 400, Pos = 0.3, Speed = 180, Gas = 0, Brake = 0.2, Steer = 10, Rpm = 6500, Gear = 4 },
            new() { TMs = 500, Pos = 0.33, Speed = 140, Gas = 0, Brake = 0.8, Steer = 25, Rpm = 6000, Gear = 4 },
            new() { TMs = 600, Pos = 0.36, Speed = 100, Gas = 0, Brake = 0.4, Steer = 30, Rpm = 5200, Gear = 3 },
            new() { TMs = 700, Pos = 0.39, Speed = 80, Gas = 0, Brake = 0.1, Steer = 28, Rpm = 4800, Gear = 3 },
            new() { TMs = 750, Pos = 0.4, Speed = 82, Gas = 0, Brake = 0, Steer = 22, Rpm = 4700, Gear = 3 },
            new() { TMs = 800, Pos = 0.42, Speed = 85, Gas = 0.2, Brake = 0, Steer = 20, Rpm = 5000, Gear = 3 },
            new() { TMs = 900, Pos = 0.48, Speed = 120, Gas = 1, Brake = 0, Steer = 8, Rpm = 6200, Gear = 3 },
            new() { TMs = 1000, Pos = 0.55, Speed = 160, Gas = 1, Brake = 0, Steer = 2, Rpm = 7000, Gear = 4 },
        };
        var corners = CornerMath.Detect(samples, 1000);
        Assert.Single(corners);
        Assert.Equal("T1", corners[0].Name);
        Assert.Equal(0.8, corners[0].PeakBrake, 3);
        Assert.Equal(80, corners[0].MinSpeedKmh);
        Assert.Equal(300, corners[0].BrakePointM);
        Assert.True(corners[0].TrailBrakeMs > 0);
        Assert.True(corners[0].ThrottlePickupM > corners[0].BrakePointM);
        Assert.True(corners[0].CoastingMs > 0);
    }

    [Fact]
    public void LapCutterUsesCompletedLapsAndRejectsPit()
    {
        var frames = new List<AcFrame>();
        for (var i = 0; i < 10; i++)
        {
            frames.Add(new AcFrame { TMs = i * 1000, Pos = i / 10.0, Speed = 150, Gas = 1, CompletedLaps = 0, LastTimeMs = 0 });
        }
        frames.Add(new AcFrame { TMs = 10000, Pos = 0.01, Speed = 80, Gas = 0.2, CompletedLaps = 1, LastTimeMs = 100000 });
        var laps = CornerMath.SegmentLaps(frames);
        Assert.Single(laps);
        Assert.Equal(100000, laps[0].TimeMs);
        Assert.True(laps[0].Valid);
        frames[3].IsInPit = true;
        Assert.False(CornerMath.SegmentLaps(frames)[0].Valid);
    }

    [Fact]
    public void StoreWritesGzippedJsonAndCsv()
    {
        var root = Path.Combine(Path.GetTempPath(), "rwp-" + Guid.NewGuid().ToString("N"));
        var store = new SessionStore(root);
        for (var i = 0; i < 40; i++)
        {
            store.OnAc(new AcSnapshot
            {
                Track = "monza",
                CarModel = "ks_ferrari_488_gt3",
                TrackConfiguration = "",
                TrackLengthM = 5793,
                NormalizedPosition = i / 40f,
                SpeedKmh = 160,
                Gas = i == 20 ? 0 : 1,
                Brake = i == 20 ? 0.8f : 0,
                SteerAngle = i == 20 ? 25 : 0,
                Rpm = 7000,
                Gear = 4,
                CompletedLaps = 0,
                PlayerName = "Ada",
            });
        }
        store.OnAc(new AcSnapshot
        {
            Track = "monza",
            CarModel = "ks_ferrari_488_gt3",
            TrackLengthM = 5793,
            NormalizedPosition = 0.01f,
            SpeedKmh = 80,
            Gas = 0.3f,
            CompletedLaps = 1,
            LastTimeMs = 108000,
            PlayerName = "Ada",
        });
        store.OnAcLost();
        var logs = store.List();
        Assert.Single(logs);
        Assert.Equal("monza", logs[0].Track);
        Assert.Equal("ks_ferrari_488_gt3", logs[0].Car);
        Assert.True(File.Exists(Path.ChangeExtension(logs[0].Path, null) is string path && path.EndsWith(".json") ? logs[0].Path : logs[0].Path));
        Assert.True(File.Exists(logs[0].Path.Replace(".json.gz", ".csv.gz")));
        using var input = File.OpenRead(logs[0].Path);
        using var gzip = new GZipStream(input, CompressionMode.Decompress);
        var doc = JsonSerializer.Deserialize<SessionDocument>(gzip, SessionStore.Json);
        Assert.NotNull(doc);
        Assert.Equal("racing-wheel-pro.session/v1", doc!.Schema);
        Assert.True(doc.Laps.Count >= 1);
        Assert.Equal(108000, doc.Laps[0].TimeMs);
    }
}
