using RacingWheelPro.Pc;
using Xunit;

namespace RacingWheelPro.PC.Tests;

public class GearMapTests
{
    [Fact]
    public void PaddlesAreVjoyButtonsOneAndTwo()
    {
        var map = GearMap.Resolve(32);
        Assert.True(map.Preferred);
        Assert.Equal(1, map.Up);
        Assert.Equal(2, map.Down);

        var up = GearMap.Compose(0, Proto.GearUp, map);
        Assert.Equal(32, up.Length);
        Assert.Single(Indices(up));
        Assert.True(up[0]);
        Assert.False(up[1]);

        var down = GearMap.Compose(0, Proto.GearDown, map);
        Assert.Single(Indices(down));
        Assert.True(down[1]);
        Assert.False(down[0]);
    }

    [Fact]
    public void GearReleaseClearsThePaddleAndLeavesTheWheelButton()
    {
        var map = GearMap.Resolve(32);
        var held = GearMap.Compose(1u << 2, Proto.GearUp, map);
        Assert.Equal(new[] { 0, 2 }, Indices(held));

        var released = GearMap.Compose(1u << 2, 0, map);
        Assert.Equal(new[] { 2 }, Indices(released));
    }

    [Fact]
    public void EightButtonDeviceStillUsesButtonsOneAndTwo()
    {
        var map = GearMap.Resolve(8);
        Assert.True(map.Preferred);
        Assert.Equal(1, map.Up);
        Assert.Equal(2, map.Down);

        var up = GearMap.Compose(0, Proto.GearUp, map);
        Assert.Equal(8, up.Length);
        Assert.Single(Indices(up));
        Assert.True(up[0]);

        var message = GearMap.AcquireMessage(1, map, sharesWheelButtons: true);
        Assert.Contains("button 1", message);
        Assert.Contains("button 2", message);
    }

    [Fact]
    public void OneButtonDeviceCannotPlaceAPair()
    {
        var map = GearMap.Resolve(1);
        Assert.Equal(0, map.Up);
        Assert.Equal(0, map.Down);
        var states = GearMap.Compose(0, Proto.GearUp | Proto.GearDown, map);
        Assert.All(states, Assert.False);
    }

    [Fact]
    public void TelegramLinkIsTheCommunityChannel()
    {
        Assert.Equal("https://t.me/racingwheelpro", Community.Telegram);
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, "community.json")))
            dir = dir.Parent;
        Assert.NotNull(dir);
        var text = File.ReadAllText(Path.Combine(dir!.FullName, "community.json"));
        Assert.DoesNotContain("TODO", text);
        Assert.Contains(Community.Telegram, text);
    }

    [Fact]
    public void KeyboardInputStrideMatchesTheSixtyFourBitStruct()
    {
        if (IntPtr.Size != 8) return;
        Assert.Equal(40, KeyboardLayout.InputStride);
    }

    private static int[] Indices(bool[] states)
    {
        var found = new List<int>();
        for (var i = 0; i < states.Length; i++)
        {
            if (states[i]) found.Add(i);
        }
        return found.ToArray();
    }
}
