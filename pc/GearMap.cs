namespace RacingWheelPro.Pc;

/// <summary>
/// Where a paddle press lands on a vJoy device.
/// Buttons 31 and 32 are used when the device actually has them. A device
/// configured with fewer buttons cannot see 31 and 32, so the last two that
/// exist are used instead and the receiver says so at startup.
/// </summary>
public static class GearMap
{
    public const int PreferredUp = 31;
    public const int PreferredDown = 32;

    public readonly record struct Assignment(int Up, int Down, int ButtonCount, bool Preferred);

    public static Assignment Resolve(int buttonCount)
    {
        if (buttonCount >= PreferredDown)
            return new Assignment(PreferredUp, PreferredDown, buttonCount, true);
        if (buttonCount >= 2)
            return new Assignment(buttonCount - 1, buttonCount, buttonCount, false);
        return new Assignment(0, 0, Math.Max(0, buttonCount), false);
    }

    /// <summary>
    /// Final button states, index 0 = button 1. Gear is OR'd onto its own
    /// buttons and clears when the bit clears. Other buttons stay as the wheel sent them.
    /// </summary>
    public static bool[] Compose(uint wheelButtons, int gear, Assignment map)
    {
        var n = Math.Max(0, map.ButtonCount);
        var states = new bool[n];
        var limit = Math.Min(n, 32);
        for (var i = 0; i < limit; i++)
            states[i] = (wheelButtons & (1u << i)) != 0;
        if (Held(map.Up, n, gear, Proto.GearUp)) states[map.Up - 1] = true;
        if (Held(map.Down, n, gear, Proto.GearDown)) states[map.Down - 1] = true;
        return states;
    }

    public static string AcquireMessage(uint id, Assignment map, bool sharesWheelButtons)
    {
        if (map.Up == 0)
        {
            return "vJoy device " + id + " reports " + map.ButtonCount
                + " buttons. Gear needs two. In vJoyConf set Buttons to 32, apply, and restart the receiver.";
        }
        if (map.Preferred)
        {
            return "vJoy device " + id + " has " + map.ButtonCount
                + " buttons. Gear up is button 31 and gear down is button 32.";
        }
        var overlap = sharesWheelButtons
            ? " Those numbers are also wheel buttons until the device has 32."
            : " This device carries only the paddles, so those two buttons are the shifters.";
        return "vJoy device " + id + " has " + map.ButtonCount
            + " buttons, so gear up is button " + map.Up + " and gear down is button " + map.Down + "."
            + overlap
            + " In vJoyConf set Buttons to 32, apply, and restart the receiver to use buttons 31 and 32.";
    }

    private static bool Held(int button, int count, int gear, int bit) =>
        button >= 1 && button <= count && (gear & bit) != 0;
}
