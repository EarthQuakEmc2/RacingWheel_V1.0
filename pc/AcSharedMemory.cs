using System.IO.MemoryMappedFiles;

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
        _physicsView = OpenView(_physics, AcFields.PhysicsBytes, 64);
        _graphicsView = OpenView(_graphics, AcFields.GraphicsBytes, AcFields.GraphicsFallbackBytes);
        _staticView = OpenView(_static, AcFields.StaticBytes, AcFields.StaticBytes);
        return true;
    }

    public bool TryRead(out AcSnapshot snapshot)
    {
        snapshot = default;
        if (!IsOpen || _physicsView == null || _graphicsView == null || _staticView == null) return false;
        try
        {
            snapshot = AcFields.Decode(ReadAll(_physicsView), ReadAll(_graphicsView), ReadAll(_staticView));
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

    private static MemoryMappedViewAccessor OpenView(MemoryMappedFile file, int want, int fallback)
    {
#pragma warning disable CA1416
        try
        {
            return file.CreateViewAccessor(0, want, MemoryMappedFileAccess.Read);
        }
        catch (ArgumentException)
        {
            return file.CreateViewAccessor(0, fallback, MemoryMappedFileAccess.Read);
        }
        catch (UnauthorizedAccessException)
        {
            return file.CreateViewAccessor(0, fallback, MemoryMappedFileAccess.Read);
        }
#pragma warning restore CA1416
    }

    private static byte[] ReadAll(MemoryMappedViewAccessor view)
    {
        var length = (int)Math.Min(view.Capacity, 8192);
        var bytes = new byte[length];
        view.ReadArray(0, bytes, 0, length);
        return bytes;
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
    public float Fuel;
    public float MaxFuel;
    public float Tc;
    public float Abs;
    public float PerformanceMeter;
    public int Position;
    /// <summary>Driver TC setting, or 255 when the graphics page did not include it.</summary>
    public int TcLevel;
    /// <summary>Driver ABS setting, or 255 when it was not reported.</summary>
    public int AbsLevel;
}
