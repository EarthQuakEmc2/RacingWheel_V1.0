using System.Runtime.InteropServices;

namespace RacingWheelPro.Pc;

/// <summary>
/// Win32 INPUT is a 4-byte type plus a union as large as MOUSEINPUT.
/// On 64-bit that is 40 bytes. A keyboard-only struct is 32 bytes, and
/// SendInput then rejects the call, so Page Up and Page Down never arrive.
/// </summary>
public static class KeyboardLayout
{
    public static int InputStride => Marshal.SizeOf<INPUT>();

    [StructLayout(LayoutKind.Sequential)]
    internal struct INPUT
    {
        public uint type;
        public InputUnion U;
    }

    [StructLayout(LayoutKind.Explicit)]
    internal struct InputUnion
    {
        [FieldOffset(0)] public KEYBDINPUT ki;
        [FieldOffset(0)] public MOUSEINPUT mi;
    }

    [StructLayout(LayoutKind.Sequential)]
    internal struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    internal struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }
}
