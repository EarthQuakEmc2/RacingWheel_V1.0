namespace RacingWheelPro.Pc;

/// <summary>
/// Receiver trace. The windowless exe writes here instead of a console.
/// Files live in %APPDATA%/RacingWheelPro/logs and roll after 512 KB, keeping three older copies.
/// </summary>
public static class FileLog
{
    const int Keep = 3;
    const long Limit = 512 * 1024;
    static readonly object Gate = new();
    static StreamWriter? _writer;
    static string _path = "";

    public static string DirectoryPath { get; private set; } = "";

    public static void Open()
    {
        var root = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        if (string.IsNullOrEmpty(root)) root = Path.GetTempPath();
        DirectoryPath = Path.Combine(root, "RacingWheelPro", "logs");
        Directory.CreateDirectory(DirectoryPath);
        _path = Path.Combine(DirectoryPath, "receiver.log");
        lock (Gate)
        {
            RollIfNeeded(force: false);
            _writer = OpenWriter();
        }
    }

    public static void Info(string message) => Write("INFO", message);

    public static void Error(string message) => Write("ERROR", message);

    static void Write(string level, string message)
    {
        lock (Gate)
        {
            if (_writer == null) return;
            RollIfNeeded(force: true);
            _writer.WriteLine($"{DateTime.Now:yyyy-MM-dd HH:mm:ss} {level} {message}");
        }
    }

    static void RollIfNeeded(bool force)
    {
        if (!File.Exists(_path)) return;
        if (new FileInfo(_path).Length < Limit) return;
        _writer?.Dispose();
        _writer = null;
        var oldest = Path.Combine(DirectoryPath, "receiver." + Keep + ".log");
        if (File.Exists(oldest)) File.Delete(oldest);
        for (var i = Keep - 1; i >= 1; i--)
        {
            var src = Path.Combine(DirectoryPath, "receiver." + i + ".log");
            var dst = Path.Combine(DirectoryPath, "receiver." + (i + 1) + ".log");
            if (File.Exists(src)) File.Move(src, dst, true);
        }
        File.Move(_path, Path.Combine(DirectoryPath, "receiver.1.log"), true);
        if (force) _writer = OpenWriter();
    }

    static StreamWriter OpenWriter() =>
        new(new FileStream(_path, FileMode.Append, FileAccess.Write, FileShare.Read)) { AutoFlush = true };
}
