using System.Runtime.InteropServices;

namespace RacingWheelPro.Pc;

/// <summary>
/// A tray icon so the windowless receiver can be quit. Windows only.
/// </summary>
internal static class TrayIcon
{
    const int WmApp = 0x8000;
    const int WmCommand = 0x0111;
    const int WmRButtonUp = 0x0205;
    const int WmLButtonDblClk = 0x0203;
    const uint NimAdd = 0;
    const uint NimDelete = 2;
    const uint NifMessage = 0x1;
    const uint NifIcon = 0x2;
    const uint NifTip = 0x4;
    const uint MfString = 0;
    const uint TpmRightButton = 0x0002;
    const uint TpmReturnCmd = 0x0100;

    public static void Start(Action onExit)
    {
        if (!OperatingSystem.IsWindows()) return;
        try
        {
            var thread = new Thread(() => Run(onExit)) { IsBackground = true, Name = "tray" };
            thread.SetApartmentState(ApartmentState.STA);
            thread.Start();
        }
        catch (Exception ex)
        {
            FileLog.Error("Tray icon did not start: " + ex.Message);
        }
    }

    static void Run(Action onExit)
    {
        var proc = new WndProc((h, msg, w, l) =>
        {
            if (msg == WmApp && (l.ToInt32() == WmRButtonUp || l.ToInt32() == WmLButtonDblClk))
            {
                var menu = CreatePopupMenu();
                AppendMenu(menu, MfString, 1, "Exit Racing Wheel Pro");
                GetCursorPos(out var pt);
                SetForegroundWindow(h);
                var cmd = TrackPopupMenu(menu, TpmRightButton | TpmReturnCmd, pt.X, pt.Y, 0, h, IntPtr.Zero);
                DestroyMenu(menu);
                if (cmd == 1 || l.ToInt32() == WmLButtonDblClk)
                {
                    onExit();
                    PostQuitMessage(0);
                }
                return IntPtr.Zero;
            }
            if (msg == WmCommand && (w.ToInt32() & 0xFFFF) == 1)
            {
                onExit();
                PostQuitMessage(0);
                return IntPtr.Zero;
            }
            return DefWindowProc(h, msg, w, l);
        });
        var cls = new WndClass { lpfnWndProc = proc, lpszClassName = "RacingWheelProTray" };
        RegisterClass(ref cls);
        var hwnd = CreateWindowEx(0, cls.lpszClassName, "", 0, 0, 0, 0, 0, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);
        var icon = LoadIcon(IntPtr.Zero, (IntPtr)32512);
        var data = new NotifyIconData
        {
            cbSize = Marshal.SizeOf<NotifyIconData>(),
            hWnd = hwnd,
            uID = 1,
            uFlags = NifMessage | NifIcon | NifTip,
            uCallbackMessage = WmApp,
            hIcon = icon,
            szTip = "Racing Wheel Pro",
        };
        Shell_NotifyIcon(NimAdd, ref data);
        while (GetMessage(out var message, IntPtr.Zero, 0, 0) > 0)
        {
            TranslateMessage(ref message);
            DispatchMessage(ref message);
        }
        Shell_NotifyIcon(NimDelete, ref data);
        if (hwnd != IntPtr.Zero) DestroyWindow(hwnd);
    }

    delegate IntPtr WndProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    struct WndClass
    {
        public uint style;
        public WndProc? lpfnWndProc;
        public int cbClsExtra;
        public int cbWndExtra;
        public IntPtr hInstance;
        public IntPtr hIcon;
        public IntPtr hCursor;
        public IntPtr hbrBackground;
        public string? lpszMenuName;
        public string lpszClassName;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    struct NotifyIconData
    {
        public int cbSize;
        public IntPtr hWnd;
        public uint uID;
        public uint uFlags;
        public uint uCallbackMessage;
        public IntPtr hIcon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
        public string szTip;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct Point { public int X; public int Y; }

    [StructLayout(LayoutKind.Sequential)]
    struct Msg
    {
        public IntPtr hWnd;
        public uint message;
        public IntPtr wParam;
        public IntPtr lParam;
        public uint time;
        public Point pt;
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    static extern ushort RegisterClass(ref WndClass lpWndClass);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    static extern IntPtr CreateWindowEx(int ex, string cls, string name, int style, int x, int y, int w, int h, IntPtr parent, IntPtr menu, IntPtr inst, IntPtr param);

    [DllImport("user32.dll")]
    static extern IntPtr DefWindowProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll")]
    static extern bool DestroyWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    static extern int GetMessage(out Msg msg, IntPtr hWnd, uint min, uint max);

    [DllImport("user32.dll")]
    static extern bool TranslateMessage(ref Msg msg);

    [DllImport("user32.dll")]
    static extern IntPtr DispatchMessage(ref Msg msg);

    [DllImport("user32.dll")]
    static extern void PostQuitMessage(int code);

    [DllImport("user32.dll")]
    static extern bool GetCursorPos(out Point point);

    [DllImport("user32.dll")]
    static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    static extern IntPtr CreatePopupMenu();

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    static extern bool AppendMenu(IntPtr menu, uint flags, uint id, string text);

    [DllImport("user32.dll")]
    static extern uint TrackPopupMenu(IntPtr menu, uint flags, int x, int y, int reserved, IntPtr hwnd, IntPtr rect);

    [DllImport("user32.dll")]
    static extern bool DestroyMenu(IntPtr menu);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    static extern IntPtr LoadIcon(IntPtr instance, IntPtr icon);

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    static extern bool Shell_NotifyIcon(uint message, ref NotifyIconData data);
}
