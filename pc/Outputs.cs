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
        IOutput result = chosen;
        if (keyboard)
        {
            result = new KeyboardOverlay(chosen, pad != null && mode != "vjoy");
        }
        // ViGEm has no button 31. When the wheel is the Xbox pad, a second vJoy
        // device carries only the two paddle buttons so the game can bind them.
        if (pad != null && joy == null)
        {
            var gear = VjoyGearDevice.TryCreate(vjoyId, log);
            if (gear != null) result = new GearOnSecondDevice(result, gear);
        }
        return result;
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
    private readonly GearMap.Assignment _gear;
    public string Name => "vJoy device " + _id;
    public int Caps => Proto.CapVjoy;
    public event Action<byte, byte>? Rumble { add { } remove { } }

    private VjoyOutput(uint id, GearMap.Assignment gear)
    {
        _id = id;
        _gear = gear;
    }

    public static VjoyOutput? TryCreate(int id, Action<string> log)
    {
        var acquired = VjoySession.Acquire(id, log, sharesWheelButtons: true);
        if (acquired == null) return null;
        return new VjoyOutput(acquired.Value.Id, acquired.Value.Map);
    }

    public void Apply(StatePacket state)
    {
        VjoyNative.SetAxis(Axis11(state.Steer), _id, HidAxis.X);
        VjoyNative.SetAxis(Axis01(state.Throttle), _id, HidAxis.Y);
        VjoyNative.SetAxis(Axis01(state.Brake), _id, HidAxis.Z);
        VjoyNative.SetAxis(Axis01(state.Clutch), _id, HidAxis.Rx);
        VjoyNative.SetAxis(Axis01(Knob01(state, 0)), _id, HidAxis.Ry);
        VjoyNative.SetAxis(Axis01(Knob01(state, 1)), _id, HidAxis.Rz);
        VjoyNative.SetAxis(Axis01(Knob01(state, 2)), _id, HidAxis.Slider);
        VjoyNative.SetAxis(Axis01(Knob01(state, 3)), _id, HidAxis.Dial);
        VjoySession.WriteButtons(_id, GearMap.Compose(state.Buttons, state.Gear, _gear));
    }

    public void Dispose()
    {
        try { VjoyNative.RelinquishVJD(_id); } catch { /* driver already stopped */ }
    }

    private static int Axis11(float v) => (int)Math.Round((Math.Clamp(v, -1f, 1f) * 0.5f + 0.5f) * 32766f) + 1;
    private static int Axis01(float v) => (int)Math.Round(Math.Clamp(v, 0f, 1f) * 32766f) + 1;
    private static float Knob01(StatePacket state, int slot) =>
        slot < state.Knobs.Length ? Math.Clamp(state.Knobs[slot] / 11f, 0f, 1f) : 0f;
}

/// <summary>Axis usages shared by the wheel device and the paddle-only device.</summary>
internal static class HidAxis
{
    public const uint X = 0x30;
    public const uint Y = 0x31;
    public const uint Z = 0x32;
    public const uint Rx = 0x33;
    public const uint Ry = 0x34;
    public const uint Rz = 0x35;
    public const uint Slider = 0x36;
    public const uint Dial = 0x37;
    public static readonly uint[] All = { X, Y, Z, Rx, Ry, Rz, Slider, Dial };
}

internal static class VjoySession
{
    public readonly record struct Open(uint Id, GearMap.Assignment Map);

    public static Open? Acquire(int id, Action<string> log, bool sharesWheelButtons)
    {
        try
        {
            if (!VjoyNative.vJoyEnabled())
            {
                log(sharesWheelButtons
                    ? "vJoy is installed but the driver is disabled."
                    : "vJoy is not enabled, so the paddles stay on Page Up and Page Down. Enable vJoy and set device 1 to 32 buttons for joystick buttons.");
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
            var count = VjoyNative.GetVJDButtonNumber(rId);
            var map = GearMap.Resolve(count);
            if (map.Up == 0)
            {
                log(GearMap.AcquireMessage(rId, map, sharesWheelButtons));
                VjoyNative.RelinquishVJD(rId);
                return null;
            }
            log(GearMap.AcquireMessage(rId, map, sharesWheelButtons));
            return new Open(rId, map);
        }
        catch (DllNotFoundException)
        {
            log(sharesWheelButtons
                ? "vJoyInterface.dll was not found. Install vJoy, or leave output on ViGEm."
                : "vJoyInterface.dll was not found. Page Up and Page Down still shift. Install vJoy and set 32 buttons so Assetto Corsa can bind joystick buttons.");
            return null;
        }
        catch (Exception ex)
        {
            log("vJoy failed: " + ex.Message);
            return null;
        }
    }

    public static void WriteButtons(uint id, bool[] states)
    {
        for (var i = 0; i < states.Length; i++)
            VjoyNative.SetBtn(states[i], id, (uint)(i + 1));
    }

    /// <summary>vJoy's 15-bit center. An untouched axis sits here instead of full left.</summary>
    public static void CenterAxes(uint id)
    {
        foreach (var axis in HidAxis.All)
            VjoyNative.SetAxis(16384, id, axis);
    }
}

/// <summary>
/// Paddle buttons on their own vJoy device. Axes stay centered so this device
/// does not steer the car. Only the two gear buttons change.
/// </summary>
internal sealed class VjoyGearDevice : IDisposable
{
    private readonly uint _id;
    private readonly GearMap.Assignment _gear;
    public string Label { get; }

    private VjoyGearDevice(uint id, GearMap.Assignment gear)
    {
        _id = id;
        _gear = gear;
        Label = "vJoy gear " + gear.Up + "/" + gear.Down;
    }

    public static VjoyGearDevice? TryCreate(int id, Action<string> log)
    {
        var acquired = VjoySession.Acquire(id, log, sharesWheelButtons: false);
        if (acquired == null) return null;
        VjoySession.CenterAxes(acquired.Value.Id);
        VjoySession.WriteButtons(acquired.Value.Id, GearMap.Compose(0, 0, acquired.Value.Map));
        return new VjoyGearDevice(acquired.Value.Id, acquired.Value.Map);
    }

    public void ApplyGear(int gear) =>
        VjoySession.WriteButtons(_id, GearMap.Compose(0, gear, _gear));

    public void Dispose()
    {
        try
        {
            VjoySession.WriteButtons(_id, GearMap.Compose(0, 0, _gear));
            VjoyNative.RelinquishVJD(_id);
        }
        catch { /* driver already stopped */ }
    }
}

internal sealed class GearOnSecondDevice : IOutput
{
    private readonly IOutput _inner;
    private readonly VjoyGearDevice _gear;
    public string Name => _inner.Name + " + " + _gear.Label;
    public int Caps => _inner.Caps | Proto.CapVjoy;
    public event Action<byte, byte>? Rumble
    {
        add => _inner.Rumble += value;
        remove => _inner.Rumble -= value;
    }

    public GearOnSecondDevice(IOutput inner, VjoyGearDevice gear)
    {
        _inner = inner;
        _gear = gear;
    }

    public void Apply(StatePacket state)
    {
        _inner.Apply(state);
        _gear.ApplyGear(state.Gear);
    }

    public void Dispose()
    {
        _gear.Dispose();
        _inner.Dispose();
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

    [DllImport("vJoyInterface.dll", CallingConvention = CallingConvention.Cdecl)]
    public static extern int GetVJDButtonNumber(uint rID);
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
        var input = new KeyboardLayout.INPUT
        {
            type = 1,
            U = new KeyboardLayout.InputUnion
            {
                ki = new KeyboardLayout.KEYBDINPUT
                {
                    wVk = (ushort)vk,
                    dwFlags = down ? 0u : 2u,
                },
            },
        };
        SendInput(1, new[] { input }, KeyboardLayout.InputStride);
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, KeyboardLayout.INPUT[] inputs, int cbSize);
}
