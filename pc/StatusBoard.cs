using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Runtime.InteropServices;

namespace RacingWheelPro.Pc;

/// <summary>
/// A fixed block of lines at the top of the console. Each update overwrites
/// the same rows, so the window does not scroll.
/// </summary>
static class StatusBoard
{
    internal static readonly string AppVersion =
        Assembly.GetExecutingAssembly().GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion
        ?? "0.6.3 Beta 1002";
    const int Rows = 6;
    static bool _opened;

    public static void Open()
    {
        try
        {
            if (!Console.IsOutputRedirected)
            {
                Console.Clear();
                Console.CursorVisible = false;
            }
        }
        catch { /* no console yet */ }
        _opened = true;
        Draw(new string[Rows]);
    }

    public static void Draw(string[] lines)
    {
        if (!_opened) return;
        try
        {
            if (Console.IsOutputRedirected) return;
            var width = Math.Max(40, Console.WindowWidth);
            Console.SetCursorPosition(0, 0);
            for (var i = 0; i < Rows; i++)
            {
                var text = i < lines.Length ? lines[i] ?? "" : "";
                if (text.Length >= width) text = text[..(width - 1)];
                Console.Write(text.PadRight(width - 1));
                Console.WriteLine();
            }
        }
        catch (IOException) { /* console went away */ }
        catch (ArgumentOutOfRangeException) { /* window resized under us */ }
    }

    public static string LanAddress()
    {
        try
        {
            foreach (var address in Dns.GetHostAddresses(Dns.GetHostName()))
            {
                if (address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(address))
                    return address.ToString();
            }
        }
        catch { /* name lookup failed */ }
        return "0.0.0.0";
    }
}

/// <summary>Ctrl+C and closing the console window both stop the receiver.</summary>
static class ConsoleLifetime
{
    enum Ctrl : uint
    {
        C = 0,
        Break = 1,
        Close = 2,
        Logoff = 5,
        Shutdown = 6,
    }

    delegate bool Handler(Ctrl signal);

    static Handler? _keep;

    public static void Hook(Action stop)
    {
        Console.CancelKeyPress += (_, e) =>
        {
            e.Cancel = true;
            stop();
        };
        if (!OperatingSystem.IsWindows()) return;
        _keep = _ =>
        {
            stop();
            return true;
        };
        SetConsoleCtrlHandler(_keep, true);
    }

    [DllImport("kernel32.dll")]
    static extern bool SetConsoleCtrlHandler(Handler? handler, bool add);
}
