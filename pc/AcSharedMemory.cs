using System.IO.MemoryMappedFiles;
using System.Text;

namespace RacingWheelPro.Pc;

/// <summary>
/// Assetto Corsa shared memory: Local\acpmf_physics, Local\acpmf_graphics, Local\acpmf_static.
/// Field offsets follow the pack(4) SDK layout (graphics normalizedCarPosition at 248,
/// static trackSplineLength at 520). The graphics name is also tried without the final s.
/// </summary>
public sealed class AcSharedMemory : IDisposable
{
    private MemoryMappedFile? _physics;
    private MemoryMappedFile? _graphics;
    private MemoryMappedFile? _static;
    private MemoryMappedViewAccessor? _physicsView;
    private MemoryMappedViewAccessor? _graphicsView;
    private MemoryMappedViewAccessor? _staticView;

    public bool IsOpen => _physicsView != null && _graphicsView != null && _staticView != null;

    public bool TryOpen()
    {
        if (IsOpen) return true;
        if (!OperatingSystem.IsWindows()) return false;
        Dispose();
        _physics = Open("Local\\acpmf_physics", "acpmf_physics");
        _graphics = Open("Local\\acpmf_graphics", "Local\\acpmf_graphic", "acpmf_graphics", "acpmf_graphic");
        _static = Open("Local\\acpmf_static", "acpmf_static");
        if (_physics == null || _graphics == null || _static == null)
        {
            Dispose();
            return false;
        }
        _physicsView = _physics.CreateViewAccessor(0, 64, MemoryMappedFileAccess.Read);
        _graphicsView = _graphics.CreateViewAccessor(0, 288, MemoryMappedFileAccess.Read);
        _staticView = _static.CreateViewAccessor(0, 620, MemoryMappedFileAccess.Read);
        return true;
    }

    public bool TryRead(out AcSnapshot snapshot)
    {
        snapshot = default;
        if (!IsOpen || _physicsView == null || _graphicsView == null || _staticView == null) return false;
        try
        {
            var physics = _physicsView;
            var graphics = _graphicsView;
            var page = _staticView;
            snapshot = new AcSnapshot
            {
                Gas = physics.ReadSingle(4),
                Brake = physics.ReadSingle(8),
                Gear = physics.ReadInt32(16),
                Rpm = physics.ReadInt32(20),
                SteerAngle = physics.ReadSingle(24),
                SpeedKmh = physics.ReadSingle(28),
                Status = graphics.ReadInt32(4),
                CompletedLaps = graphics.ReadInt32(132),
                CurrentTimeMs = graphics.ReadInt32(140),
                LastTimeMs = graphics.ReadInt32(144),
                BestTimeMs = graphics.ReadInt32(148),
                IsInPit = graphics.ReadInt32(160) != 0,
                NormalizedPosition = graphics.ReadSingle(248),
                CarModel = ReadWide(page, 68, 33),
                Track = ReadWide(page, 134, 33),
                PlayerName = ReadWide(page, 200, 33),
                MaxRpm = page.ReadInt32(412),
                TrackLengthM = page.ReadSingle(520),
                TrackConfiguration = ReadWide(page, 524, 33),
            };
            return true;
        }
        catch (Exception)
        {
            Dispose();
            return false;
        }
    }

    public void Dispose()
    {
        _physicsView?.Dispose();
        _graphicsView?.Dispose();
        _staticView?.Dispose();
        _physics?.Dispose();
        _graphics?.Dispose();
        _static?.Dispose();
        _physicsView = null;
        _graphicsView = null;
        _staticView = null;
        _physics = null;
        _graphics = null;
        _static = null;
    }

    private static MemoryMappedFile? Open(params string[] names)
    {
        foreach (var name in names)
        {
            try
            {
#pragma warning disable CA1416
                return MemoryMappedFile.OpenExisting(name, MemoryMappedFileRights.Read);
#pragma warning restore CA1416
            }
            catch (FileNotFoundException) { }
            catch (UnauthorizedAccessException) { }
            catch (IOException) { }
        }
        return null;
    }

    private static string ReadWide(MemoryMappedViewAccessor view, long offset, int chars)
    {
        var bytes = new byte[chars * 2];
        view.ReadArray(offset, bytes, 0, bytes.Length);
        var text = Encoding.Unicode.GetString(bytes);
        var end = text.IndexOf('\0');
        if (end >= 0) text = text[..end];
        return text.Trim();
    }
}

public struct AcSnapshot
{
    public float Gas;
    public float Brake;
    public int Gear;
    public int Rpm;
    public float SteerAngle;
    public float SpeedKmh;
    public int Status;
    public int CompletedLaps;
    public int CurrentTimeMs;
    public int LastTimeMs;
    public int BestTimeMs;
    public bool IsInPit;
    public float NormalizedPosition;
    public string CarModel;
    public string Track;
    public string PlayerName;
    public int MaxRpm;
    public float TrackLengthM;
    public string TrackConfiguration;
}
