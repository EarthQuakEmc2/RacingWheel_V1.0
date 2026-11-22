using System.Reflection;
using System.Text.Json;

namespace RacingWheelPro.Pc;

/// <summary>
/// The public Telegram channel. The only copy of the address is community.json
/// at the repo root, embedded into this exe at build time.
/// </summary>
public static class Community
{
    public static string Telegram { get; } = Read();

    private static string Read()
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("community.json")
            ?? throw new InvalidOperationException("community.json was not embedded. It lives at the repo root.");
        using var doc = JsonDocument.Parse(stream);
        var url = doc.RootElement.GetProperty("telegram").GetString();
        if (string.IsNullOrWhiteSpace(url))
            throw new InvalidOperationException("community.json has an empty telegram value.");
        return url;
    }
}
