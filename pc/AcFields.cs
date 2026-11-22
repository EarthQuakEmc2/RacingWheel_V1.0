using System.Buffers.Binary;
using System.Text;

namespace RacingWheelPro.Pc;

/// <summary>
/// Assetto Corsa shared-memory offsets. The layout is the pack(4) SDK.
/// Values the receiver already used stay at the same offsets: gas 4, gear 16,
/// completed laps 132, normalized position 248, max rpm 412, track length 520.
/// Extra fields for the wheel screen: fuel, TC, ABS, the live delta, race
/// position, and the TC/ABS setup numbers on the graphics page.
/// </summary>
public static class AcFields
{
    public const int PhysicsBytes = 320;
    public const int GraphicsBytes = 1296;
    public const int GraphicsFallbackBytes = 288;
    public const int StaticBytes = 620;

    public const int Gas = 4;
    public const int Brake = 8;
    public const int Fuel = 12;
    public const int Gear = 16;
    public const int Rpm = 20;
    public const int Steer = 24;
    public const int Speed = 28;
    public const int Tc = 204;
    public const int Abs = 252;
    public const int PerformanceMeter = 312;

    public const int Status = 4;
    public const int CompletedLaps = 132;
    public const int Position = 136;
    public const int CurrentTime = 140;
    public const int LastTime = 144;
    public const int BestTime = 148;
    public const int IsInPit = 160;
    public const int NormalizedPosition = 248;
    public const int GraphicsTc = 1268;
    public const int GraphicsAbs = 1280;

    public const int CarModel = 68;
    public const int Track = 134;
    public const int PlayerName = 200;
    public const int MaxRpm = 412;
    public const int MaxFuel = 416;
    public const int TrackLength = 520;
    public const int TrackConfiguration = 524;

    public static AcSnapshot Decode(byte[] physics, byte[] graphics, byte[] page)
    {
        var tcLevel = graphics.Length >= GraphicsTc + 4 ? Setting(I(graphics, GraphicsTc)) : 255;
        var absLevel = graphics.Length >= GraphicsAbs + 4 ? Setting(I(graphics, GraphicsAbs)) : 255;
        return new AcSnapshot
        {
            Gas = F(physics, Gas),
            Brake = F(physics, Brake),
            Fuel = F(physics, Fuel),
            Gear = I(physics, Gear),
            Rpm = I(physics, Rpm),
            SteerAngle = F(physics, Steer),
            SpeedKmh = F(physics, Speed),
            Tc = F(physics, Tc),
            Abs = F(physics, Abs),
            PerformanceMeter = F(physics, PerformanceMeter),
            Status = I(graphics, Status),
            CompletedLaps = I(graphics, CompletedLaps),
            Position = I(graphics, Position),
            CurrentTimeMs = I(graphics, CurrentTime),
            LastTimeMs = I(graphics, LastTime),
            BestTimeMs = I(graphics, BestTime),
            IsInPit = I(graphics, IsInPit) != 0,
            NormalizedPosition = F(graphics, NormalizedPosition),
            TcLevel = tcLevel,
            AbsLevel = absLevel,
            CarModel = Wide(page, CarModel, 33),
            Track = Wide(page, Track, 33),
            PlayerName = Wide(page, PlayerName, 33),
            MaxRpm = I(page, MaxRpm),
            MaxFuel = F(page, MaxFuel),
            TrackLengthM = F(page, TrackLength),
            TrackConfiguration = Wide(page, TrackConfiguration, 33),
        };
    }

    /// <summary>A driver setting is a small integer. Anything else is "not reported".</summary>
    public static int Setting(int value) => value >= 0 && value <= 20 ? value : 255;

    private static float F(byte[] bytes, int offset)
    {
        if (offset < 0 || offset + 4 > bytes.Length) return 0f;
        return BinaryPrimitives.ReadSingleLittleEndian(bytes.AsSpan(offset, 4));
    }

    private static int I(byte[] bytes, int offset)
    {
        if (offset < 0 || offset + 4 > bytes.Length) return 0;
        return BinaryPrimitives.ReadInt32LittleEndian(bytes.AsSpan(offset, 4));
    }

    private static string Wide(byte[] bytes, int offset, int chars)
    {
        var size = chars * 2;
        if (offset < 0 || offset + size > bytes.Length) return "";
        var text = Encoding.Unicode.GetString(bytes, offset, size);
        var end = text.IndexOf('\0');
        if (end >= 0) text = text[..end];
        return text.Trim();
    }
}
