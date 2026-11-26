namespace RacingWheelPro.Pc;

/// <summary>
/// Receiver trace. Files live in %APPDATA%/RacingWheelPro/logs and roll after 512 KB, keeping three older copies.
/// Opening the log must never take the receiver down. An older build may still hold receiver.log.
/// </summary>
public static class FileLog
{
    const int Keep = 3;
    const long Limit = 512 * 1024;
    static readonly object Gate = new();
    static StreamWriter? _writer;
    static string _path = "";

    public static string DirectoryPath { get; private set; } = "";
    public static string FilePath { get; private set; } = "";

    public static void Open(string? directory = null)
    {
        try
        {
            string dir;
            if (!string.IsNullOrWhiteSpace(directory))
            {
                dir = directory;
            }
            else
            {
                var root = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                if (string.IsNullOrEmpty(root)) root = Path.GetTempPath();
                dir = Path.Combine(root, "RacingWheelPro", "logs");
            }
            Directory.CreateDirectory(dir);
            DirectoryPath = dir;
            lock (Gate)
            {
                try { _writer?.Dispose(); } catch { /* already closed */ }
                _writer = null;
                FilePath = "";
                var primary = Path.Combine(dir, "receiver.log");
                _path = primary;
                RollIfNeeded(force: false);
                var writer = TryOpen(primary);
                if (writer == null)
                {
                    var fallback = Path.Combine(dir, "receiver-" + Environment.ProcessId + ".log");
                    writer = TryOpen(fallback);
                }
                _writer = writer;
                FilePath = writer == null ? "" : _path;
            }
        }
        catch { /* logging must not stop the receiver */ }
    }

    public static void Info(string message) => Write("INFO", message);

    public static void Error(string message) => Write("ERROR", message);

    static void Write(string level, string message)
    {
        lock (Gate)
        {
            try
            {
                if (_writer == null) return;
                RollIfNeeded(force: true);
                if (_writer == null) return;
                _writer.WriteLine($"{DateTime.Now:yyyy-MM-dd HH:mm:ss} {level} {message}");
            }
            catch { /* a full disk or a locked roll must not crash the receiver */ }
        }
    }

    static void RollIfNeeded(bool force)
    {
        try
        {
            if (string.IsNullOrEmpty(_path) || !File.Exists(_path)) return;
            if (new FileInfo(_path).Length < Limit) return;
            try { _writer?.Dispose(); } catch { /* already closed */ }
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
            if (force) _writer = TryOpen(_path);
        }
        catch { /* the other process can keep the file; we keep the current writer */ }
    }

    static StreamWriter? TryOpen(string path)
    {
        try
        {
            var stream = new FileStream(path, FileMode.Append, FileAccess.Write, FileShare.ReadWrite | FileShare.Delete);
            _path = path;
            return new StreamWriter(stream) { AutoFlush = true };
        }
        catch
        {
            return null;
        }
    }
}
