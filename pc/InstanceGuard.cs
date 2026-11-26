using System.Diagnostics;

namespace RacingWheelPro.Pc;

/// <summary>
/// One receiver at a time. A named mutex catches a second new build.
/// The process list also catches an older RacingWheelPro-PC.exe that does not take the mutex.
/// </summary>
static class InstanceGuard
{
    const string MutexName = @"Local\RacingWheelPro.Receiver";
    static Mutex? _held;

    public static bool Claim()
    {
        var others = FindOtherReceivers();
        Mutex? mutex = null;
        var owned = false;
        var heldByOther = false;
        try
        {
            mutex = new Mutex(true, MutexName, out var created);
            owned = created;
        }
        catch (UnauthorizedAccessException)
        {
            heldByOther = true;
        }
        catch (WaitHandleCannotBeOpenedException)
        {
            heldByOther = true;
        }

        if (!owned && mutex != null)
        {
            try
            {
                owned = mutex.WaitOne(0);
            }
            catch (AbandonedMutexException)
            {
                owned = true;
            }
        }

        if (owned && others.Count == 0 && !heldByOther)
        {
            _held = mutex;
            return true;
        }

        Console.WriteLine(Prompt(others.Select(p => p.Id)));
        var yes = AcceptsClose(ReadAnswer());
        if (!yes)
        {
            Release(mutex, owned);
            DisposeAll(others);
            Console.WriteLine("Left the other receiver running.");
            ConsolePause.Wait();
            return false;
        }

        foreach (var process in others)
        {
            try
            {
                process.Kill(entireProcessTree: true);
                process.WaitForExit(3000);
            }
            catch { /* already gone, or this user cannot end it */ }
        }
        DisposeAll(others);

        if (!owned && mutex != null)
        {
            try
            {
                owned = mutex.WaitOne(TimeSpan.FromSeconds(3));
            }
            catch (AbandonedMutexException)
            {
                owned = true;
            }
        }
        if (!owned)
        {
            mutex?.Dispose();
            Console.WriteLine("Could not close the other receiver.");
            ConsolePause.Wait();
            return false;
        }
        _held = mutex;
        return true;
    }

    public static string Prompt(IEnumerable<int> pids)
    {
        var listed = pids.ToList();
        var pid = listed.Count == 0 ? "unknown" : string.Join(", ", listed);
        return "Another Racing Wheel Pro receiver is already running (PID " + pid + "). Close it and continue? [Y/n]";
    }

    public static bool AcceptsClose(string? answer)
    {
        var text = (answer ?? "").Trim();
        return text.Length == 0
            || text.Equals("y", StringComparison.OrdinalIgnoreCase)
            || text.Equals("yes", StringComparison.OrdinalIgnoreCase);
    }

    static bool IsReceiverProcess(Process process)
    {
        var name = process.ProcessName;
        if (name.StartsWith("RacingWheelPro-PC", StringComparison.OrdinalIgnoreCase)) return true;
        // Linux reports only the first 15 characters of the process name.
        if (!name.StartsWith("RacingWheel", StringComparison.OrdinalIgnoreCase)) return false;
        try
        {
            var file = Path.GetFileName(process.MainModule?.FileName ?? "");
            return file.StartsWith("RacingWheelPro-PC", StringComparison.OrdinalIgnoreCase);
        }
        catch
        {
            return false;
        }
    }

    static List<Process> FindOtherReceivers()
    {
        var self = Environment.ProcessId;
        var found = new List<Process>();
        foreach (var process in Process.GetProcesses())
        {
            if (process.Id == self)
            {
                process.Dispose();
                continue;
            }
            var match = false;
            try
            {
                match = IsReceiverProcess(process);
            }
            catch
            {
                process.Dispose();
                continue;
            }
            if (match) found.Add(process);
            else process.Dispose();
        }
        return found;
    }

    static string ReadAnswer()
    {
        try
        {
            var line = Console.ReadLine();
            if (line == null) return "n";
            return line;
        }
        catch
        {
            return "n";
        }
    }

    static void Release(Mutex? mutex, bool owned)
    {
        if (owned)
        {
            try { mutex?.ReleaseMutex(); } catch { /* not the owner */ }
        }
        mutex?.Dispose();
    }

    static void DisposeAll(List<Process> processes)
    {
        foreach (var process in processes)
        {
            try { process.Dispose(); } catch { /* already disposed */ }
        }
    }
}

static class ConsolePause
{
    public static void Wait()
    {
        Console.WriteLine("Press any key to close");
        try
        {
            if (Console.IsInputRedirected) return;
            Console.ReadKey(intercept: true);
        }
        catch (InvalidOperationException) { /* no console key available */ }
        catch (IOException) { /* window already going away */ }
    }
}
