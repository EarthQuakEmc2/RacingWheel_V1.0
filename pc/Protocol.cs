using System.Buffers.Binary;
using System.Text;

namespace RacingWheelPro.Pc;

/// <summary>
/// Little-endian RWP1 packets. Layout is documented in the repo README and
/// mirrored by the Android Packets.kt encoder.
/// </summary>
public static class Proto
{
    public const int Version = 1;
    public const int DiscoveryPort = 41510;
    public const int UdpPort = 41511;
    public const int TcpPort = 41512;
    public const int StateLen = 36;
    public const byte TcpHello = 1;
    public const byte TcpState = 2;
    public const byte TcpPing = 3;
    public const byte TcpPong = 4;
    public const byte TcpBye = 5;
    public const byte CapVigem = 1;
    public const byte CapVjoy = 2;
    public const byte CapKeyboard = 4;

    /// <summary>Bit 0 of the gear byte. Left paddle, shift up.</summary>
    public const int GearUp = 1;

    /// <summary>Bit 1 of the gear byte. Right paddle, shift down.</summary>
    public const int GearDown = 2;

    public const uint VjoyGearUp = 1;
    public const uint VjoyGearDown = 2;

    /// <summary>VK_PRIOR, Page Up.</summary>
    public const int GearUpKey = 0x21;

    /// <summary>VK_NEXT, Page Down.</summary>
    public const int GearDownKey = 0x22;

    public const int TelemetryLen = 40;
    public const byte TelemetryLive = 1;
}

public sealed class StatePacket
{
    public int Seq { get; init; }
    public float Steer { get; init; }
    public float Throttle { get; init; }
    public float Brake { get; init; }
    public float Clutch { get; init; }
    public uint Buttons { get; init; }
    public byte[] Knobs { get; init; } = new byte[8];
    public uint ClientTimeMs { get; init; }
    public int Flags { get; init; }

    /// <summary>Bit 0 = shift up (left zone), bit 1 = shift down (right zone).</summary>
    public int Gear { get; init; }

    public IEnumerable<int> PressedButtons()
    {
        for (var i = 0; i < 32; i++)
        {
            if ((Buttons & (1u << i)) != 0) yield return i + 1;
        }
    }
}

public sealed class DashReading
{
    public bool Live { get; init; }
    public int Seq { get; init; }
    public int Rpm { get; init; }
    public int MaxRpm { get; init; }
    public float SpeedKmh { get; init; }
    public int Gear { get; init; }
    public int Tc { get; init; }
    public int Abs { get; init; }
    public int FuelPct { get; init; }
    public int Position { get; init; }
    public int CurrentLapMs { get; init; }
    public int DeltaMs { get; init; }
    public int Laps { get; init; }
}

public sealed class HostHello
{
    public string Name { get; init; } = "";
    public string Wheel { get; init; } = "";
    public string Profile { get; init; } = "";
}

public static class Packets
{
    public static StatePacket? ParseState(ReadOnlySpan<byte> bytes)
    {
        if (bytes.Length < Proto.StateLen) return null;
        if (bytes[0] != (byte)'R' || bytes[1] != (byte)'W' || bytes[2] != (byte)'P' || bytes[3] != (byte)'S') return null;
        if (bytes[4] != Proto.Version) return null;
        var knobs = new byte[8];
        bytes.Slice(20, 8).CopyTo(knobs);
        var steer = BinaryPrimitives.ReadInt16LittleEndian(bytes.Slice(8, 2));
        return new StatePacket
        {
            Flags = bytes[5],
            Seq = BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(6, 2)),
            Steer = steer / 32767f,
            Throttle = BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(10, 2)) / 65535f,
            Brake = BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(12, 2)) / 65535f,
            Clutch = BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(14, 2)) / 65535f,
            Buttons = BinaryPrimitives.ReadUInt32LittleEndian(bytes.Slice(16, 4)),
            Knobs = knobs,
            ClientTimeMs = BinaryPrimitives.ReadUInt32LittleEndian(bytes.Slice(28, 4)),
            Gear = bytes[32],
        };
    }

    public static bool IsFind(ReadOnlySpan<byte> bytes) =>
        bytes.Length >= 4 && bytes[0] == (byte)'R' && bytes[1] == (byte)'W' && bytes[2] == (byte)'P' && bytes[3] == (byte)'F';

    public static byte[] HostBeacon(int tcp, int udp, int caps, string name)
    {
        var raw = Encoding.UTF8.GetBytes(name);
        var n = Math.Min(48, raw.Length);
        var buf = new byte[11 + n];
        buf[0] = (byte)'R'; buf[1] = (byte)'W'; buf[2] = (byte)'P'; buf[3] = (byte)'H';
        buf[4] = Proto.Version;
        buf[5] = (byte)caps;
        BinaryPrimitives.WriteUInt16LittleEndian(buf.AsSpan(6), (ushort)tcp);
        BinaryPrimitives.WriteUInt16LittleEndian(buf.AsSpan(8), (ushort)udp);
        buf[10] = (byte)n;
        raw.AsSpan(0, n).CopyTo(buf.AsSpan(11));
        return buf;
    }

    /// <summary>
    /// Compact dash packet, RWPT, 40 bytes. Sent to the phone about 25 times a second.
    /// Gear is the number on the wheel: −1 reverse, 0 neutral, 1 and up.
    /// Assetto Corsa stores reverse as 0, so this subtracts one.
    /// </summary>
    public static byte[] Telemetry(AcSnapshot snap, int seq)
    {
        var buf = new byte[Proto.TelemetryLen];
        buf[0] = (byte)'R'; buf[1] = (byte)'W'; buf[2] = (byte)'P'; buf[3] = (byte)'T';
        buf[4] = Proto.Version;
        var live = snap.Status != 0 || snap.SpeedKmh > 1f || snap.Rpm > 0;
        buf[5] = live ? Proto.TelemetryLive : (byte)0;
        BinaryPrimitives.WriteUInt16LittleEndian(buf.AsSpan(6), (ushort)(seq & 0xFFFF));
        BinaryPrimitives.WriteUInt16LittleEndian(buf.AsSpan(8), (ushort)Math.Clamp(snap.Rpm, 0, 65535));
        BinaryPrimitives.WriteUInt16LittleEndian(buf.AsSpan(10), (ushort)Math.Clamp(snap.MaxRpm, 0, 65535));
        var speed10 = (int)Math.Clamp(snap.SpeedKmh * 10f, 0f, 65535f);
        BinaryPrimitives.WriteUInt16LittleEndian(buf.AsSpan(12), (ushort)speed10);
        var gear = Math.Clamp(snap.Gear - 1, -1, 12);
        buf[14] = unchecked((byte)gear);
        buf[15] = Scale01(snap.Gas);
        buf[16] = Scale01(snap.Brake);
        buf[17] = (byte)(snap.TcLevel is >= 0 and <= 20 ? snap.TcLevel : 255);
        buf[18] = (byte)(snap.AbsLevel is >= 0 and <= 20 ? snap.AbsLevel : 255);
        buf[19] = FuelPercent(snap);
        var position = snap.Position is >= 1 and <= 255 ? snap.Position : 0;
        buf[20] = (byte)position;
        buf[21] = (byte)Math.Clamp(snap.Status, 0, 255);
        BinaryPrimitives.WriteInt32LittleEndian(buf.AsSpan(22), snap.CurrentTimeMs);
        BinaryPrimitives.WriteInt32LittleEndian(buf.AsSpan(26), snap.LastTimeMs);
        BinaryPrimitives.WriteInt32LittleEndian(buf.AsSpan(30), snap.BestTimeMs);
        BinaryPrimitives.WriteInt16LittleEndian(buf.AsSpan(34), DeltaMs(snap.PerformanceMeter));
        BinaryPrimitives.WriteUInt16LittleEndian(buf.AsSpan(36), (ushort)Math.Clamp(snap.CompletedLaps, 0, 65535));
        buf[38] = Scale01(snap.Tc);
        buf[39] = Scale01(snap.Abs);
        return buf;
    }

    public static DashReading? ParseTelemetry(ReadOnlySpan<byte> bytes)
    {
        if (bytes.Length < Proto.TelemetryLen) return null;
        if (bytes[0] != (byte)'R' || bytes[1] != (byte)'W' || bytes[2] != (byte)'P' || bytes[3] != (byte)'T') return null;
        if (bytes[4] != Proto.Version) return null;
        return new DashReading
        {
            Live = (bytes[5] & Proto.TelemetryLive) != 0,
            Seq = BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(6, 2)),
            Rpm = BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(8, 2)),
            MaxRpm = BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(10, 2)),
            SpeedKmh = BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(12, 2)) / 10f,
            Gear = unchecked((sbyte)bytes[14]),
            Tc = bytes[17],
            Abs = bytes[18],
            FuelPct = bytes[19],
            Position = bytes[20],
            CurrentLapMs = BinaryPrimitives.ReadInt32LittleEndian(bytes.Slice(22, 4)),
            DeltaMs = BinaryPrimitives.ReadInt16LittleEndian(bytes.Slice(34, 2)),
            Laps = BinaryPrimitives.ReadUInt16LittleEndian(bytes.Slice(36, 2)),
        };
    }

    private static byte Scale01(float value)
    {
        if (float.IsNaN(value) || float.IsInfinity(value)) return 0;
        return (byte)Math.Clamp((int)Math.Round(value * 255f), 0, 255);
    }

    private static byte FuelPercent(AcSnapshot snap)
    {
        if (snap.MaxFuel <= 0.5f || snap.Fuel < 0f || snap.Fuel > snap.MaxFuel * 1.5f) return 255;
        return (byte)Math.Clamp((int)Math.Round(snap.Fuel / snap.MaxFuel * 100f), 0, 100);
    }

    private static short DeltaMs(float seconds)
    {
        if (float.IsNaN(seconds) || float.IsInfinity(seconds)) return 0;
        var ms = (int)Math.Round(seconds * 1000f);
        return (short)Math.Clamp(ms, short.MinValue, short.MaxValue);
    }

    public static byte[] Rumble(byte large, byte small) =>
        new byte[] { (byte)'R', (byte)'W', (byte)'P', (byte)'R', large, small };

    public static byte[] Pong(uint clientTime, uint serverTime)
    {
        var payload = new byte[8];
        BinaryPrimitives.WriteUInt32LittleEndian(payload, clientTime);
        BinaryPrimitives.WriteUInt32LittleEndian(payload.AsSpan(4), serverTime);
        return Frame(Proto.TcpPong, payload);
    }

    public static byte[] Frame(byte type, ReadOnlySpan<byte> payload)
    {
        var buf = new byte[3 + payload.Length];
        buf[0] = type;
        BinaryPrimitives.WriteUInt16LittleEndian(buf.AsSpan(1), (ushort)payload.Length);
        payload.CopyTo(buf.AsSpan(3));
        return buf;
    }

    public static bool TryReadFrame(List<byte> buffer, out byte type, out byte[] payload)
    {
        type = 0;
        payload = Array.Empty<byte>();
        if (buffer.Count < 3) return false;
        var len = buffer[1] | (buffer[2] << 8);
        if (len < 0 || len > 8192)
        {
            buffer.RemoveAt(0);
            return false;
        }
        if (buffer.Count < 3 + len) return false;
        type = buffer[0];
        payload = buffer.GetRange(3, len).ToArray();
        buffer.RemoveRange(0, 3 + len);
        return true;
    }

    public static HostHello ParseHello(ReadOnlySpan<byte> json)
    {
        var text = Encoding.UTF8.GetString(json);
        return new HostHello
        {
            Name = Pick(text, "name"),
            Wheel = Pick(text, "wheel"),
            Profile = Pick(text, "profile"),
        };
    }

    private static string Pick(string json, string key)
    {
        var token = "\"" + key + "\"";
        var at = json.IndexOf(token, StringComparison.Ordinal);
        if (at < 0) return "";
        var colon = json.IndexOf(':', at + token.Length);
        if (colon < 0) return "";
        var q1 = json.IndexOf('"', colon + 1);
        if (q1 < 0) return "";
        var q2 = json.IndexOf('"', q1 + 1);
        if (q2 < 0) return "";
        return json.Substring(q1 + 1, q2 - q1 - 1);
    }
}
