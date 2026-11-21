using System.Globalization;
using System.Text;
using System.Text.Json;

namespace RacingWheelPro.Pc;

/// <summary>
/// Assetto Corsa does not put the multiplayer server in shared memory.
/// The launcher writes it to Documents\Assetto Corsa\cfg\race.ini under [REMOTE]
/// before the session starts. That file is the server name and address the
/// phone uses to join the voice room.
/// </summary>
public sealed class AcServerHint
{
    public bool Active { get; init; }
    public string ServerName { get; init; } = "";
    public string ServerIp { get; init; } = "";
    public int ServerPort { get; init; }
    public string Room { get; init; } = "";
    public string Player { get; init; } = "";

    public string ToJson()
    {
        return JsonSerializer.Serialize(new
        {
            active = Active,
            name = ServerName,
            ip = ServerIp,
            port = ServerPort,
            room = Room,
            player = Player,
        });
    }

    public byte[] ToFrame() => Packets.Frame(Proto.TcpServer, Encoding.UTF8.GetBytes(ToJson()));
}

public static class AcRaceIni
{
    public static string DefaultPath() =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "Assetto Corsa", "cfg", "race.ini");

    public static AcServerHint Read(string livePlayer = "", string? path = null)
    {
        var file = path ?? DefaultPath();
        try
        {
            if (!File.Exists(file)) return Parse("", livePlayer);
            return Parse(File.ReadAllText(file), livePlayer);
        }
        catch (IOException)
        {
            return Parse("", livePlayer);
        }
        catch (UnauthorizedAccessException)
        {
            return Parse("", livePlayer);
        }
    }

    /// <summary>Same slug as the Android module and the worker. ASCII letters and digits only.</summary>
    public static string Slug(string raw)
    {
        var mapped = new StringBuilder(raw.Trim().Length);
        foreach (var ch in raw.Trim())
        {
            var keep = ch is >= 'a' and <= 'z' or >= 'A' and <= 'Z' or >= '0' and <= '9';
            mapped.Append(keep ? ch : '-');
        }
        var collapsed = Collapse(mapped.ToString()).Trim('-');
        var upper = new StringBuilder(collapsed.Length);
        foreach (var ch in collapsed)
            upper.Append(ch is >= 'a' and <= 'z' ? (char)(ch - 32) : ch);
        var text = upper.ToString();
        if (text.Length > 24) text = text[..24].TrimEnd('-');
        return text;
    }

    public static string RoomFor(string name, string ip, int port)
    {
        var fromName = Slug(name);
        if (fromName.Length >= 4) return fromName;
        if (IsIpv4(ip) && port is >= 1 and <= 65535)
        {
            var fromIp = Slug($"IP-{ip}-{port}");
            if (fromIp.Length >= 4) return fromIp;
        }
        return "";
    }

    public static AcServerHint Parse(string text, string livePlayer = "")
    {
        var section = "";
        var map = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var raw in text.Replace("\r", "").Split('\n'))
        {
            var line = raw.Trim();
            if (line.Length == 0 || line[0] == ';' || line[0] == '#') continue;
            if (line.StartsWith('[') && line.EndsWith(']'))
            {
                section = line[1..^1].Trim();
                continue;
            }
            if (!section.Equals("REMOTE", StringComparison.OrdinalIgnoreCase)) continue;
            var eq = line.IndexOf('=');
            if (eq <= 0) continue;
            map[line[..eq].Trim()] = line[(eq + 1)..].Trim();
        }
        map.TryGetValue("ACTIVE", out var activeRaw);
        map.TryGetValue("SERVER_NAME", out var name);
        map.TryGetValue("SERVER_IP", out var ip);
        map.TryGetValue("SERVER_PORT", out var portRaw);
        map.TryGetValue("NAME", out var iniPlayer);
        var active = activeRaw == "1";
        var port = int.TryParse(portRaw, NumberStyles.Integer, CultureInfo.InvariantCulture, out var parsed) ? parsed : 0;
        var player = string.IsNullOrWhiteSpace(livePlayer) ? (iniPlayer ?? "") : livePlayer.Trim();
        if (player.Length > 40) player = player[..40];
        return new AcServerHint
        {
            Active = active,
            ServerName = name ?? "",
            ServerIp = ip ?? "",
            ServerPort = port,
            Room = active ? RoomFor(name ?? "", ip ?? "", port) : "",
            Player = player.Trim(),
        };
    }

    private static string Collapse(string text)
    {
        var outText = new StringBuilder(text.Length);
        var dash = false;
        foreach (var ch in text)
        {
            if (ch == '-')
            {
                if (dash) continue;
                dash = true;
            }
            else dash = false;
            outText.Append(ch);
        }
        return outText.ToString();
    }

    private static bool IsIpv4(string raw)
    {
        var parts = raw.Split('.');
        if (parts.Length != 4) return false;
        foreach (var part in parts)
        {
            if (part.Length is < 1 or > 3) return false;
            if (!int.TryParse(part, NumberStyles.Integer, CultureInfo.InvariantCulture, out var n)) return false;
            if (n is < 0 or > 255) return false;
        }
        return true;
    }
}
