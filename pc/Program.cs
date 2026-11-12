using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;

namespace RacingWheelPro.Pc;

internal static class Program
{
    public static int Main(string[] args)
    {
        var opt = Options.Parse(args);
        if (opt.SelfTest) return SelfTest();
        using var receiver = new Receiver(opt);
        return receiver.Run();
    }

    private static int SelfTest()
    {
        var packet = BuildSample();
        var parsed = Packets.ParseState(packet);
        if (parsed == null)
        {
            Console.Error.WriteLine("self-test: parser returned null");
            return 1;
        }
        if (parsed.Seq != 7 || Math.Abs(parsed.Steer - (-12000 / 32767f)) > 0.0001f)
        {
            Console.Error.WriteLine("self-test: steer/seq mismatch");
            return 1;
        }
        if (Math.Abs(parsed.Throttle - 1f) > 0.0001f || Math.Abs(parsed.Brake - (1000 / 65535f)) > 0.0001f)
        {
            Console.Error.WriteLine("self-test: pedal mismatch");
            return 1;
        }
        if (parsed.Buttons != 5 || parsed.Knobs[0] != 3 || (sbyte)parsed.Knobs[1] != -2 || parsed.Flags != 1)
        {
            Console.Error.WriteLine("self-test: buttons/knobs mismatch");
            return 1;
        }
        if (!Packets.IsFind(Packets.HostBeacon(1, 2, 0, "x").Take(0).ToArray()) &&
            !Packets.IsFind(new byte[] { (byte)'R', (byte)'W', (byte)'P', (byte)'F' }))
        {
            Console.Error.WriteLine("self-test: find magic");
            return 1;
        }
        var hello = Packets.ParseHello("""{"name":"Pixel","wheel":"MOZA FSR","profile":"FSR"}"""u8);
        if (hello.Wheel != "MOZA FSR" || hello.Name != "Pixel")
        {
            Console.Error.WriteLine("self-test: hello json");
            return 1;
        }
        Console.WriteLine("self-test ok");
        return 0;
    }

    private static byte[] BuildSample()
    {
        var buf = new byte[36];
        buf[0] = (byte)'R'; buf[1] = (byte)'W'; buf[2] = (byte)'P'; buf[3] = (byte)'S';
        buf[4] = 1;
        buf[5] = 1;
        buf[6] = 7; buf[7] = 0;
        WriteI16(buf, 8, -12000);
        WriteU16(buf, 10, 65535);
        WriteU16(buf, 12, 1000);
        WriteU16(buf, 14, 0);
        buf[16] = 5;
        buf[20] = 3;
        buf[21] = unchecked((byte)-2);
        WriteU32(buf, 28, 123456789);
        return buf;
    }

    private static void WriteI16(byte[] b, int o, short v)
    {
        b[o] = (byte)(v & 0xFF);
        b[o + 1] = (byte)((v >> 8) & 0xFF);
    }

    private static void WriteU16(byte[] b, int o, int v) => WriteI16(b, o, (short)(v & 0xFFFF));

    private static void WriteU32(byte[] b, int o, uint v)
    {
        b[o] = (byte)(v & 0xFF);
        b[o + 1] = (byte)((v >> 8) & 0xFF);
        b[o + 2] = (byte)((v >> 16) & 0xFF);
        b[o + 3] = (byte)((v >> 24) & 0xFF);
    }
}

internal sealed class Options
{
    public bool SelfTest { get; init; }
    public bool Json { get; init; }
    public bool Simulate { get; init; }
    public bool Keyboard { get; init; } = true;
    public string Output { get; init; } = "auto";
    public int Discovery { get; init; } = Proto.DiscoveryPort;
    public int Udp { get; init; } = Proto.UdpPort;
    public int Tcp { get; init; } = Proto.TcpPort;
    public int VjoyId { get; init; } = 1;

    public static Options Parse(string[] args)
    {
        bool Flag(string name) => args.Any(a => a.Equals(name, StringComparison.OrdinalIgnoreCase));
        string Value(string name, string fallback)
        {
            for (var i = 0; i < args.Length - 1; i++)
            {
                if (args[i].Equals(name, StringComparison.OrdinalIgnoreCase)) return args[i + 1];
            }
            return fallback;
        }
        return new Options
        {
            SelfTest = Flag("--self-test"),
            Json = Flag("--json"),
            Simulate = Flag("--simulate"),
            Keyboard = !Flag("--no-keyboard"),
            Output = Value("--output", "auto"),
            Discovery = int.Parse(Value("--discovery", Proto.DiscoveryPort.ToString())),
            Udp = int.Parse(Value("--udp", Proto.UdpPort.ToString())),
            Tcp = int.Parse(Value("--tcp", Proto.TcpPort.ToString())),
            VjoyId = int.Parse(Value("--vjoy-id", "1")),
        };
    }
}

internal sealed class Receiver : IDisposable
{
    private readonly Options _opt;
    private readonly IOutput _output;
    private readonly UdpClient _discovery;
    private readonly UdpClient _telemetry;
    private readonly TcpListener _tcp;
    private readonly object _gate = new();
    private StatePacket _latest = new();
    private IPEndPoint? _phone;
    private string _peer = "";
    private int _packets;
    private int _shown;
    private DateTime _lastRx = DateTime.MinValue;
    private volatile bool _run = true;

    public Receiver(Options opt)
    {
        _opt = opt;
        _output = OutputFactory.Create(opt.Output, opt.Keyboard, opt.VjoyId, m => Console.Error.WriteLine(m));
        _discovery = new UdpClient(opt.Discovery);
        _telemetry = new UdpClient(opt.Udp);
        _tcp = new TcpListener(IPAddress.Any, opt.Tcp);
        _tcp.Start();
        _output.Rumble += (large, small) =>
        {
            IPEndPoint? dest;
            lock (_gate) dest = _phone;
            if (dest is not IPEndPoint ep) return;
            try { _telemetry.Send(Packets.Rumble(large, small), ep); } catch { /* phone already gone */ }
        };
    }

    public int Run()
    {
        Console.Error.WriteLine("Racing Wheel Pro receiver");
        Console.Error.WriteLine($"  discovery UDP {_opt.Discovery}   telemetry UDP {_opt.Udp}   TCP {_opt.Tcp}");
        Console.Error.WriteLine($"  output: {_output.Name}");
        Console.Error.WriteLine("  Waiting for the phone. Ctrl+C stops.");
        Console.CancelKeyPress += (_, e) => { e.Cancel = true; _run = false; };
        var beacon = Packets.HostBeacon(_opt.Tcp, _opt.Udp, _output.Caps, Environment.MachineName);
        Task.Run(AcceptLoop);
        Task.Run(SimulateLoop);
        var nextBeacon = DateTime.UtcNow;
        while (_run)
        {
            var now = DateTime.UtcNow;
            if (now >= nextBeacon)
            {
                try
                {
                    _discovery.Send(beacon, beacon.Length, new IPEndPoint(IPAddress.Broadcast, _opt.Discovery));
                }
                catch { /* some stacks reject broadcast until a NIC is up */ }
                nextBeacon = now.AddMilliseconds(500);
            }
            Drain(_discovery, replyFind: true, beacon);
            Drain(_telemetry, replyFind: true, beacon);
            if (!_opt.Json && (DateTime.UtcNow - _lastPrint).TotalMilliseconds > 100)
            {
                PrintDash();
                _lastPrint = DateTime.UtcNow;
            }
            Thread.Sleep(4);
        }
        return 0;
    }

    private DateTime _lastPrint = DateTime.MinValue;

    private void Drain(UdpClient client, bool replyFind, byte[] beacon)
    {
        try
        {
            while (client.Available > 0)
            {
                IPEndPoint remote = new(IPAddress.Any, 0);
                var data = client.Receive(ref remote);
                if (Packets.IsFind(data))
                {
                    if (replyFind) client.Send(beacon, beacon.Length, remote);
                    continue;
                }
                var state = Packets.ParseState(data);
                if (state == null) continue;
                Accept(state, remote);
            }
        }
        catch (SocketException) { /* non-blocking race */ }
    }

    private void Accept(StatePacket state, IPEndPoint? from)
    {
        lock (_gate)
        {
            _latest = state;
            _packets++;
            if (from != null) _phone = from;
            _lastRx = DateTime.UtcNow;
        }
        _output.Apply(state);
        if (_opt.Json)
        {
            var line = JsonSerializer.Serialize(new
            {
                seq = state.Seq,
                steer = Math.Round(state.Steer, 4),
                throttle = Math.Round(state.Throttle, 4),
                brake = Math.Round(state.Brake, 4),
                clutch = Math.Round(state.Clutch, 4),
                buttons = state.PressedButtons().ToArray(),
                knobs = state.Knobs.Select(b => (int)(sbyte)b).ToArray(),
                flags = state.Flags,
                from = from?.Address.ToString() ?? "sim",
            });
            Console.WriteLine(line);
        }
    }

    private void AcceptLoop()
    {
        while (_run)
        {
            TcpClient? client = null;
            try
            {
                if (!_tcp.Pending())
                {
                    Thread.Sleep(30);
                    continue;
                }
                client = _tcp.AcceptTcpClient();
                client.NoDelay = true;
                _peer = client.Client.RemoteEndPoint?.ToString() ?? "";
                Console.Error.WriteLine("TCP " + _peer);
                using var stream = client.GetStream();
                stream.ReadTimeout = 200;
                var buffer = new List<byte>(256);
                var tmp = new byte[1024];
                while (_run && client.Connected)
                {
                    try
                    {
                        var n = stream.Read(tmp, 0, tmp.Length);
                        if (n <= 0) break;
                        buffer.AddRange(tmp.AsSpan(0, n).ToArray());
                    }
                    catch (IOException) { /* timeout */ }
                    while (Packets.TryReadFrame(buffer, out var type, out var payload))
                    {
                        if (type == Proto.TcpHello)
                        {
                            var hello = Packets.ParseHello(payload);
                            Console.Error.WriteLine($"Hello {hello.Name} / {hello.Wheel} / {hello.Profile}");
                        }
                        else if (type == Proto.TcpState)
                        {
                            var state = Packets.ParseState(payload);
                            if (state != null) Accept(state, null);
                        }
                        else if (type == Proto.TcpPing && payload.Length >= 4)
                        {
                            var t = System.Buffers.Binary.BinaryPrimitives.ReadUInt32LittleEndian(payload);
                            var pong = Packets.Pong(t, (uint)Environment.TickCount);
                            stream.Write(pong);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                if (_run) Console.Error.WriteLine("TCP ended: " + ex.Message);
            }
            finally
            {
                client?.Close();
            }
        }
    }

    private void SimulateLoop()
    {
        if (!_opt.Simulate) return;
        var t0 = DateTime.UtcNow;
        while (_run)
        {
            var t = (float)(DateTime.UtcNow - t0).TotalSeconds;
            var steer = MathF.Sin(t * 1.4f);
            var throttle = Math.Clamp(MathF.Sin(t * 2.2f) * 0.5f + 0.5f, 0f, 1f);
            var buttons = (uint)(1u << ((int)t % 8));
            var knobs = new byte[8];
            knobs[0] = (byte)((int)(t * 3) % 12);
            var state = new StatePacket
            {
                Seq = (int)(t * 100),
                Steer = steer,
                Throttle = throttle,
                Brake = 1f - throttle,
                Clutch = 0f,
                Buttons = buttons,
                Knobs = knobs,
                Flags = 1,
            };
            var phoneIsLive = false;
            lock (_gate) phoneIsLive = (DateTime.UtcNow - _lastRx).TotalMilliseconds < 200;
            if (!phoneIsLive) Accept(state, null);
            Thread.Sleep(16);
        }
    }

    private void PrintDash()
    {
        StatePacket s;
        int count;
        string peer;
        lock (_gate)
        {
            s = _latest;
            count = _packets;
            peer = _phone?.Address.ToString() ?? (_opt.Simulate ? "simulate" : "—");
        }
        var hz = count - _shown;
        _shown = count;
        var age = _lastRx == DateTime.MinValue ? -1 : (int)(DateTime.UtcNow - _lastRx).TotalMilliseconds;
        var buttons = string.Join(",", s.PressedButtons());
        if (buttons.Length == 0) buttons = "-";
        Console.Write(
            $"\r{peer,-16} steer {s.Steer,6:0.00}  thr {s.Throttle,4:0.00}  brk {s.Brake,4:0.00}  clt {s.Clutch,4:0.00}  btn {buttons,-12} {hz,3} Hz  age {(age < 0 ? "  -" : age + "ms"),5}   ");
    }

    public void Dispose()
    {
        _run = false;
        _output.Dispose();
        _discovery.Dispose();
        _telemetry.Dispose();
        _tcp.Stop();
    }
}
