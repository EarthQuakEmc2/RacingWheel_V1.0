using System.Buffers.Binary;
using RacingWheelPro.Pc;
using Xunit;

namespace RacingWheelPro.PC.Tests;

public class TelemetryTests
{
    [Fact]
    public void DashPacketUsesWheelGearAndKeepsTheDeltaSign()
    {
        var snap = new AcSnapshot
        {
            Status = 2,
            Gear = 1,
            Rpm = 4100,
            MaxRpm = 8000,
            SpeedKmh = 96.2f,
            Gas = 0.5f,
            Brake = 0.25f,
            Fuel = 10f,
            MaxFuel = 50f,
            TcLevel = 6,
            AbsLevel = 255,
            Position = 1,
            CurrentTimeMs = 12000,
            PerformanceMeter = 1.25f,
            CompletedLaps = 0,
            Tc = 1f,
            Abs = 0f,
        };
        var back = Packets.ParseTelemetry(Packets.Telemetry(snap, 4));
        Assert.NotNull(back);
        Assert.True(back!.Live);
        Assert.Equal(0, back.Gear);
        Assert.Equal(4100, back.Rpm);
        Assert.Equal(96.2f, back.SpeedKmh, 1);
        Assert.Equal(6, back.Tc);
        Assert.Equal(255, back.Abs);
        Assert.Equal(20, back.FuelPct);
        Assert.Equal(1, back.Position);
        Assert.Equal(1250, back.DeltaMs);
        Assert.Equal(40, Packets.Telemetry(snap, 4).Length);
    }

    [Fact]
    public void SharedMemoryOffsetsMatchTheFieldsTheCoachAlreadyReads()
    {
        var physics = new byte[AcFields.PhysicsBytes];
        var graphics = new byte[AcFields.GraphicsFallbackBytes];
        var page = new byte[AcFields.StaticBytes];
        BinaryPrimitives.WriteSingleLittleEndian(physics.AsSpan(AcFields.Brake), 0.8f);
        BinaryPrimitives.WriteInt32LittleEndian(physics.AsSpan(AcFields.Rpm), 6200);
        BinaryPrimitives.WriteSingleLittleEndian(physics.AsSpan(AcFields.PerformanceMeter), -0.2f);
        BinaryPrimitives.WriteInt32LittleEndian(graphics.AsSpan(AcFields.CompletedLaps), 3);
        BinaryPrimitives.WriteSingleLittleEndian(graphics.AsSpan(AcFields.NormalizedPosition), 0.4f);
        BinaryPrimitives.WriteInt32LittleEndian(page.AsSpan(AcFields.MaxRpm), 7500);
        BinaryPrimitives.WriteSingleLittleEndian(page.AsSpan(AcFields.MaxFuel), 60f);
        BinaryPrimitives.WriteSingleLittleEndian(page.AsSpan(AcFields.TrackLength), 5793f);
        var snap = AcFields.Decode(physics, graphics, page);
        Assert.Equal(0.8f, snap.Brake, 3);
        Assert.Equal(6200, snap.Rpm);
        Assert.Equal(-0.2f, snap.PerformanceMeter, 3);
        Assert.Equal(3, snap.CompletedLaps);
        Assert.Equal(0.4f, snap.NormalizedPosition, 3);
        Assert.Equal(7500, snap.MaxRpm);
        Assert.Equal(60f, snap.MaxFuel, 3);
        Assert.Equal(5793f, snap.TrackLengthM, 3);
        Assert.Equal(255, snap.TcLevel);
        Assert.Equal(255, snap.AbsLevel);
    }

    [Fact]
    public void TcAndAbsSettingsStayInRange()
    {
        Assert.Equal(8, AcFields.Setting(8));
        Assert.Equal(255, AcFields.Setting(-1));
        Assert.Equal(255, AcFields.Setting(4000));
        var graphics = new byte[AcFields.GraphicsBytes];
        BinaryPrimitives.WriteInt32LittleEndian(graphics.AsSpan(AcFields.GraphicsTc), 3);
        BinaryPrimitives.WriteInt32LittleEndian(graphics.AsSpan(AcFields.GraphicsAbs), 1);
        var snap = AcFields.Decode(new byte[64], graphics, new byte[8]);
        Assert.Equal(3, snap.TcLevel);
        Assert.Equal(1, snap.AbsLevel);
    }
}
