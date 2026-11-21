using System.Reflection;
using System.Runtime.InteropServices;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace RacingWheelPro.Pc;

public interface IOutput : IDisposable
{
    string Name { get; }
    int Caps { get; }
    void Apply(StatePacket state);
    event Action<byte, byte>? Rumble;
}

public static class OutputFactory
{
    public static IOutput Create(string mode, bool keyboard, int vjoyId, Action<string> log)
    {
        var wantVigem = mode is "auto" or "vigem";
        var wantVjoy = mode is "vjoy" || (mode == "auto");
        IOutput? pad = null;
        if (wantVigem && OperatingSystem.IsWindows())
        {
            pad = VigemOutput.TryCreate(log);
            if (pad != null && mode == "vigem") wantVjoy = false;
        }
        if (pad != null && mode == "auto") wantVjoy = false;
        IOutput? joy = null;
        if (wantVjoy && (mode == "vjoy" || pad == null))
        {
            joy = VjoyOutput.TryCreate(vjoyId, log);
        }
        var chosen = pad ?? joy ?? new MonitorOutput();
        if (keyboard)
        {
            return new KeyboardOverlay(chosen, pad != null && mode != "vjoy");
        }
        return chosen;
    }
}

/// <summary>
/// Xbox 360 pad: steer on the left stick, throttle on RT, brake on LT,
/// clutch on the right stick Y, first two knobs on the right stick when clutch is idle.
/// Buttons 1–14 are the face, shoulders, menu and d-pad.
/// </summary>
public sealed class VigemOutput : IOutput
{
    private readonly ViGEmClient _client;
    private readonly IXbox360Controller _pad;
    private readonly Action? _submit;
    public string Name { get; }
    public int Caps => Proto.CapVigem;
    public event Action<byte, byte>? Rumble;

    private VigemOutput(ViGEmClient client, IXbox360Controller pad)
    {
        _client = client;
        _pad = pad;
        var type = pad.GetType();
        var auto = type.GetProperty("AutoSubmitReport");
        var submit = type.GetMethod("SubmitReport");
        if (auto != null && submit != null)
        {
            auto.SetValue(pad, false);
            _submit = () => submit.Invoke(pad, null);
        }
        Name = "ViGEm Xbox 360";
        pad.FeedbackReceived += (_, args) => Rumble?.Invoke(args.LargeMotor, args.SmallMotor);
        pad.Connect();
    }

    public static VigemOutput? TryCreate(Action<string> log)
    {
        try
        {
            var client = new ViGEmClient();
            var pad = client.CreateXbox360Controller();
            log("ViGEm Xbox 360 controller created. Games should see an Xbox pad.");
            return new VigemOutput(client, pad);
        }
        catch (Exception ex)
        {
            log("ViGEm unavailable (" + ex.GetType().Name + ": " + ex.Message + "). Install ViGEmBus and try again.");
            return null;
        }
    }

    public void Apply(StatePacket state)
    {
        _pad.SetAxisValue(Xbox360Axis.LeftThumbX, ScaleStick(state.Steer));
        _pad.SetAxisValue(Xbox360Axis.LeftThumbY, 0);
        _pad.SetSliderValue(Xbox360Slider.RightTrigger, ScaleTrigger(state.Throttle));
        _pad.SetSliderValue(Xbox360Slider.LeftTrigger, ScaleTrigger(state.Brake));
        var clutch = state.Clutch;
        var knob0 = Knob(state, 0);
        var knob1 = Knob(state, 1);
        _pad.SetAxisValue(Xbox360Axis.RightThumbX, ScaleStick(knob0));
        _pad.SetAxisValue(Xbox360Axis.RightThumbY, clutch > 0.01f ? ScaleStick(clutch * 2f - 1f) : ScaleStick(knob1));
        for (var i = 0; i < Buttons.Length; i++)
        {
            var down = (state.Buttons & (1u << i)) != 0;
            _pad.SetButtonState(Buttons[i], down);
        }
        _submit?.Invoke();
    }

    public void Dispose()
    {
        try { _pad.Disconnect(); } catch { /* already gone */ }
        _client.Dispose();
    }

    private static short ScaleStick(float minusOneToOne)
    {
        var v = Math.Clamp(minusOneToOne, -1f, 1f);
        return (short)Math.Round(v * 32767f);
    }

    private static byte ScaleTrigger(float zeroToOne) =>
        (byte)Math.Round(Math.Clamp(zeroToOne, 0f, 1f) * 255f);

    private static float Knob(StatePacket state, int slot)
    {
        if (slot >= state.Knobs.Length) return 0f;
        return Math.Clamp(state.Knobs[slot] / 11f, 0f, 1f) * 2f - 1f;
    }

    private static readonly Xbox360Button[] Buttons =
    {
        Xbox360Button.A, Xbox360Button.B, Xbox360Button.X, Xbox360Button.Y,
        Xbox360Button.LeftShoulder, Xbox360Button.RightShoulder,
        Xbox360Button.Back, Xbox360Button.Start,
        Xbox360Button.LeftThumb, Xbox360Button.RightThumb,
        Xbox360Button.Up, Xbox360Button.Down, Xbox360Button.Left, Xbox360Button.Right,
    };
}

public sealed class VjoyOutput : IOutput
{
    private readonly uint _id;
    public string Name => "vJoy device " + _id;
    public int Caps => Proto.CapVjoy;
    public event Action<byte, byte>? Rumble { add { } remove { } }

    private VjoyOutput(uint id) => _id = id;

    public static VjoyOutput? TryCreate(int id, Action<string> log)
    {
        try
        {
            if (!VjoyNative.vJoyEnabled())
            {
                log("vJoy is installed but the driver is disabled.");
                return null;
            }
            var rId = (uint)Math.Clamp(id, 1, 16);
            var stat = VjoyNative.GetVJDStatus(rId);
            if (stat != VjoyNative.VjdStat.Free && stat != VjoyNative.VjdStat.Own)
            {
                log("vJoy device " + rId + " is not free (status " + stat + ").");
                return null;
            }
            if (!VjoyNative.AcquireVJD(rId))
            {
                log("vJoy device " + rId + " could not be acquired.");
                return null;
            }
            log("vJoy device " + rId + " acquired. Configure it for 8 axes and 32 buttons.");
            return new VjoyOutput(rId);
        }
        catch (DllNotFoundException)
        {
            log("vJoyInterface.dll was not found. Install vJoy, or leave output on ViGEm.");
            return null;
        }
        catch (Exception ex)
        {
            log("vJoy failed: " + ex.Message);
            return null;
        }
    }

    public void Apply(StatePacket state)
    {
        VjoyNative.SetAxis(Axis11(state.Steer), _id, Hid.X);
        VjoyNative.SetAxis(Axis01(state.Throttle), _id, Hid.Y);
        VjoyNative.SetAxis(Axis01(state.Brake), _id, Hid.Z);
        VjoyNative.SetAxis(Axis01(state.Clutch), _id, Hid.Rx);
        VjoyNative.SetAxis(Axis01(Knob01(state, 0)), _id, Hid.Ry);
        VjoyNative.SetAxis(Axis01(Knob01(state, 1)), _id, Hid.Rz);
        VjoyNative.SetAxis(Axis01(Knob01(state, 2)), _id, Hid.Slider);
        VjoyNative.SetAxis(Axis01(Knob01(state, 3)), _id, Hid.Dial);
        for (uint b = 1; b <= 32; b++)
        {
            var down = (state.Buttons & (1u << (int)(b - 1))) != 0;
            VjoyNative.SetBtn(down, _id, b);
        }
        if ((state.Gear & Proto.GearUp) != 0) VjoyNative.SetBtn(true, _id, Proto.VjoyGearUp);
        if ((state.Gear & Proto.GearDown) != 0) VjoyNative.SetBtn(true, _id, Proto.VjoyGearDown);
    }

    public void Dispose()
    {
        try { VjoyNative.RelinquishVJD(_id); } catch { /* driver already stopped */ }
    }

    private static int Axis11(float v) => (int)Math.Round((Math.Clamp(v, -1f, 1f) * 0.5f + 0.5f) * 32766f) + 1;
    private static int Axis01(float v) => (int)Math.Round(Math.Clamp(v, 0f, 1f) * 32766f) + 1;
    private static float Knob01(StatePacket state, int slot) =>
        slot < state.Knobs.Length ? Math.Clamp(state.Knobs[slot] / 11f, 0f, 1f) : 0f;

    private static class Hid
    {
        public const uint X = 0x30;
        public const uint Y = 0x31;
        public const uint Z = 0x32;
        public const uint Rx = 0x33;
        public const uint Ry = 0x34;
        public const uint Rz = 0x35;
        public const uint Slider = 0x36;
        public const uint Dial = 0x37;
    }
}

internal static class VjoyNative
{
    public enum VjdStat { Own = 0, Free = 1, Busy = 2, Miss = 3, Unknown = 4 }

    [DllImport("vJoyInterface.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern bool vJoyEnabled();

    [DllImport("vJoyInterface.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern VjdStat GetVJDStatus(uint rID);

    [DllImport("vJoyInterface.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern bool AcquireVJD(uint rID);

    [DllImport("vJoyInterface.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern void RelinquishVJD(uint rID);

    [DllImport("vJoyInterface.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern bool SetAxis(int value, uint rID, uint axis);

    [DllImport("vJoyInterface.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern bool SetBtn(bool value, uint rID, uint nBtn);
}

public sealed class MonitorOutput : IOutput
{
    public string Name => "monitor only";
    public int Caps => 0;
    public event Action<byte, byte>? Rumble { add { } remove { } }
    public void Apply(StatePacket state) { }
    public void Dispose() { }
}

/// <summary>
/// Keys for buttons the virtual pad does not carry. With a ViGEm pad, buttons 15–32
/// become F1–F12 then 1–6. Without a pad, every button is a key.
/// </summary>
public sealed class KeyboardOverlay : IOutput
{
    private readonly IOutput _inner;
    private readonly bool _extrasOnly;
    private readonly HashSet<int> _down = new();
    public string Name => _inner.Name + " + keyboard";
    public int Caps => _inner.Caps | Proto.CapKeyboard;
    public event Action<byte, byte>? Rumble
    {
        add => _inner.Rumble += value;
        remove => _inner.Rumble -= value;
    }

    public KeyboardOverlay(IOutput inner, bool extrasOnly)
    {
        _inner = inner;
        _extrasOnly = extrasOnly;
    }

    public void Apply(StatePacket state)
    {
        _inner.Apply(state);
        if (!OperatingSystem.IsWindows()) return;
        var want = new HashSet<int>();
        for (var button = 1; button <= 32; button++)
        {
            if (_extrasOnly && button <= 14) continue;
            if ((state.Buttons & (1u << (button - 1))) == 0) continue;
            want.Add(KeyFor(button));
        }
        if ((state.Gear & Proto.GearUp) != 0) want.Add(Proto.GearUpKey);
        if ((state.Gear & Proto.GearDown) != 0) want.Add(Proto.GearDownKey);
        foreach (var vk in _down.ToArray())
        {
            if (!want.Contains(vk))
            {
                KeySender.Key(vk, false);
                _down.Remove(vk);
            }
        }
        foreach (var vk in want)
        {
            if (_down.Add(vk)) KeySender.Key(vk, true);
        }
    }

    public void Dispose()
    {
        if (OperatingSystem.IsWindows())
        {
            foreach (var vk in _down) KeySender.Key(vk, false);
        }
        _down.Clear();
        _inner.Dispose();
    }

    private int KeyFor(int button)
    {
        // With a pad, button 15 is F1. Without one, button 1 is F1. Then digits, then A.
        var index = _extrasOnly ? button - 15 : button - 1;
        if (index < 0) index = 0;
        if (index < 12) return 0x70 + index;
        if (index < 22) return 0x31 + (index - 12);
        return 0x41 + Math.Min(25, index - 22);
    }
}

internal static class KeySender
{
    public static void Key(int vk, bool down)
    {
        var input = new INPUT
        {
            type = 1,
            U = new INPUTUNION
            {
                ki = new KEYBDINPUT
                {
                    wVk = (ushort)vk,
                    dwFlags = down ? 0u : 2u,
                },
            },
        };
        SendInput(1, new[] { input }, Marshal.SizeOf<INPUT>());
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] inputs, int cbSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public INPUTUNION U;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct INPUTUNION
    {
        [FieldOffset(0)] public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }
}
