using RacingWheelPro.Pc;
using Xunit;

namespace RacingWheelPro.PC.Tests;

public class AcRaceIniTests
{
    private const string Sample = """
        [RACE]
        TRACK=monza
        [REMOTE]
        ACTIVE=1
        SERVER_IP=192.168.1.10
        SERVER_PORT=9600
        SERVER_NAME=Official Monza #1
        NAME=Alex
        """;

    [Fact]
    public void ActiveServerBecomesTheSharedRoomCode()
    {
        var hint = AcRaceIni.Parse(Sample);
        Assert.True(hint.Active);
        Assert.Equal("OFFICIAL-MONZA-1", hint.Room);
        Assert.Equal("Official Monza #1", hint.ServerName);
        Assert.Equal(9600, hint.ServerPort);
        Assert.Equal("Alex", hint.Player);
        Assert.Contains("\"room\":\"OFFICIAL-MONZA-1\"", hint.ToJson());
        Assert.Equal(Proto.TcpServer, hint.ToFrame()[0]);
    }

    [Fact]
    public void ShortNameFallsBackToTheAddress()
    {
        Assert.Equal("IP-192-168-1-10-9600", AcRaceIni.RoomFor("", "192.168.1.10", 9600));
        Assert.Equal("IP-192-168-1-10-9600", AcRaceIni.RoomFor("AC", "192.168.1.10", 9600));
        Assert.Equal("SPA-24H", AcRaceIni.RoomFor("  spa   24h ", "", 0));
        Assert.Equal("", AcRaceIni.RoomFor("", "not-an-ip", 9600));
        var slug = AcRaceIni.Slug("This is a very long server name indeed");
        Assert.Equal(24, slug.Length);
        Assert.False(slug.EndsWith('-'));
    }

    [Fact]
    public void SinglePlayerClearsTheRoomAndLivePlayerWins()
    {
        var text = Sample.Replace("ACTIVE=1", "ACTIVE=0");
        var hint = AcRaceIni.Parse(text, "LiveDriver");
        Assert.False(hint.Active);
        Assert.Equal("", hint.Room);
        Assert.Equal("LiveDriver", hint.Player);
    }
}
