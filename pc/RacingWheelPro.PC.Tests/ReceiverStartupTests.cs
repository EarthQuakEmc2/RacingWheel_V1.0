using RacingWheelPro.Pc;
using Xunit;

namespace RacingWheelPro.PC.Tests;

public class ReceiverStartupTests
{
    [Fact]
    public void LockedLogFallsBackToAPidFile()
    {
        var dir = Path.Combine(Path.GetTempPath(), "rwp-log-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(dir);
        var primary = Path.Combine(dir, "receiver.log");
        File.WriteAllText(primary, "held");
        // Windows rejects a second writer when the first handle shares only Read.
        // Linux does not enforce that share mode, so the file is made unwritable instead.
        FileStream? block = null;
        try
        {
            if (OperatingSystem.IsWindows())
                block = new FileStream(primary, FileMode.Open, FileAccess.ReadWrite, FileShare.Read);
            else
                File.SetUnixFileMode(primary, UnixFileMode.None);
            FileLog.Open(dir);
            Assert.Equal(Path.Combine(dir, "receiver-" + Environment.ProcessId + ".log"), FileLog.FilePath);
            FileLog.Info("still up");
            Assert.True(new FileInfo(FileLog.FilePath).Length > 0);
        }
        finally
        {
            block?.Dispose();
            if (!OperatingSystem.IsWindows())
            {
                try { File.SetUnixFileMode(primary, UnixFileMode.UserRead | UnixFileMode.UserWrite); }
                catch { /* the temp file can stay */ }
            }
        }
    }

    [Fact]
    public void SharedLogLetsASecondWriterAppend()
    {
        var dir = Path.Combine(Path.GetTempPath(), "rwp-log-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(dir);
        FileLog.Open(dir);
        using var second = new FileStream(FileLog.FilePath, FileMode.Append, FileAccess.Write, FileShare.ReadWrite | FileShare.Delete);
        second.WriteByte((byte)'x');
        second.Flush();
        Assert.True(new FileInfo(FileLog.FilePath).Length > 0);
    }

    [Fact]
    public void SecondInstancePromptNamesThePid()
    {
        Assert.Equal(
            "Another Racing Wheel Pro receiver is already running (PID 42). Close it and continue? [Y/n]",
            InstanceGuard.Prompt(new[] { 42 }));
        Assert.Equal(
            "Another Racing Wheel Pro receiver is already running (PID unknown). Close it and continue? [Y/n]",
            InstanceGuard.Prompt(Array.Empty<int>()));
        Assert.True(InstanceGuard.AcceptsClose(""));
        Assert.True(InstanceGuard.AcceptsClose("Y"));
        Assert.False(InstanceGuard.AcceptsClose("n"));
    }
}
