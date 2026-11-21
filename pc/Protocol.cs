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

    public const uint VjoyGearUp = 31;
    public const uint VjoyGearDown = 32;

    /// <summary>VK_PRIOR, Page Up.</summary>
    public const int GearUpKey = 0x21;

    /// <summary>VK_NEXT, Page Down.</summary>
    public const int GearDownKey = 0x22;
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
