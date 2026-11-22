using RacingWheelPro.Pc;
using Xunit;

namespace RacingWheelPro.PC.Tests;

public class GearMapTests
{
    [Fact]
    public void ThirtyTwoButtonsUseThirtyOneAndThirtyTwo()
    {
        var map = GearMap.Resolve(32);
        Assert.True(map.Preferred);
        Assert.Equal(31, map.Up);
        Assert.Equal(32, map.Down);

        var up = GearMap.Compose(0, Proto.GearUp, map);
        Assert.Equal(32, up.Length);
        Assert.Single(Indices(up));
        Assert.True(up[30]);
        Assert.False(up[31]);

        var down = GearMap.Compose(0, Proto.GearDown, map);
        Assert.Single(Indices(down));
        Assert.True(down[31]);
        Assert.False(down[30]);
    }

    [Fact]
    public void GearReleaseClearsThePaddleAndLeavesTheWheelButton()
    {
        var map = GearMap.Resolve(32);
        var held = GearMap.Compose(1u, Proto.GearUp, map);
        Assert.Equal(new[] { 0, 30 }, Indices(held));

        var released = GearMap.Compose(1u, 0, map);
        Assert.Equal(new[] { 0 }, Indices(released));
    }

    [Fact]
    public void ShortDeviceUsesTheLastTwoButtonsAndNoHigherIndex()
    {
        var map = GearMap.Resolve(8);
        Assert.False(map.Preferred);
        Assert.Equal(7, map.Up);
        Assert.Equal(8, map.Down);

        var up = GearMap.Compose(0, Proto.GearUp, map);
        Assert.Equal(8, up.Length);
        Assert.Single(Indices(up));
        Assert.True(up[6]);

        var message = GearMap.AcquireMessage(1, map, sharesWheelButtons: true);
        Assert.Contains("button 7", message);
        Assert.Contains("button 8", message);
        Assert.Contains("32", message);
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
    public void TelegramLinkIsTheRepoPlaceholder()
    {
        Assert.Equal("https://t.me/RacingWheelPro", Community.Telegram);
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, "community.json")))
            dir = dir.Parent;
        Assert.NotNull(dir);
        var text = File.ReadAllText(Path.Combine(dir!.FullName, "community.json"));
        Assert.Contains("TODO", text);
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
