using System.Diagnostics;
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
        FileLog.Open();
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
        if (parsed.Gear != (Proto.GearUp | Proto.GearDown))
        {
            Console.Error.WriteLine("self-test: gear mismatch");
            return 1;
        }
        var gearUp = GearMap.Compose(0, Proto.GearUp, GearMap.Resolve(32));
        var gearDown = GearMap.Compose(0, Proto.GearDown, GearMap.Resolve(8));
        if (gearUp.Count(b => b) != 1 || !gearUp[0] || gearDown.Length != 8 || gearDown.Count(b => b) != 1 || !gearDown[1])
        {
            Console.Error.WriteLine("self-test: gear buttons");
            return 1;
        }
        if (IntPtr.Size == 8 && KeyboardLayout.InputStride != 40)
        {
            Console.Error.WriteLine("self-test: keyboard input size " + KeyboardLayout.InputStride);
            return 1;
        }
        if (!Community.Telegram.StartsWith("https://t.me/", StringComparison.Ordinal))
        {
            Console.Error.WriteLine("self-test: telegram link");
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
        var corners = CornerMath.Detect(new List<Sample>
        {
            new() { TMs = 0, Pos = 0.1, Speed = 200, Gas = 1, Brake = 0, Steer = 0, Rpm = 7000, Gear = 4 },
            new() { TMs = 200, Pos = 0.2, Speed = 200, Gas = 1, Brake = 0, Steer = 1, Rpm = 7000, Gear = 4 },
            new() { TMs = 400, Pos = 0.3, Speed = 180, Gas = 0, Brake = 0.2, Steer = 10, Rpm = 6500, Gear = 4 },
            new() { TMs = 500, Pos = 0.33, Speed = 140, Gas = 0, Brake = 0.8, Steer = 25, Rpm = 6000, Gear = 4 },
            new() { TMs = 600, Pos = 0.36, Speed = 100, Gas = 0, Brake = 0.4, Steer = 30, Rpm = 5200, Gear = 3 },
            new() { TMs = 700, Pos = 0.39, Speed = 80, Gas = 0, Brake = 0.1, Steer = 28, Rpm = 4800, Gear = 3 },
            new() { TMs = 750, Pos = 0.4, Speed = 82, Gas = 0, Brake = 0, Steer = 22, Rpm = 4700, Gear = 3 },
            new() { TMs = 800, Pos = 0.42, Speed = 85, Gas = 0.2, Brake = 0, Steer = 20, Rpm = 5000, Gear = 3 },
            new() { TMs = 900, Pos = 0.48, Speed = 120, Gas = 1, Brake = 0, Steer = 8, Rpm = 6200, Gear = 3 },
            new() { TMs = 1000, Pos = 0.55, Speed = 160, Gas = 1, Brake = 0, Steer = 2, Rpm = 7000, Gear = 4 },
        }, 1000);
        if (corners.Count != 1 || Math.Abs(corners[0].PeakBrake - 0.8) > 0.001 || corners[0].BrakePointM != 300 || corners[0].MinSpeedKmh != 80)
        {
            Console.Error.WriteLine("self-test: corner metrics");
            return 1;
        }
        var frames = new List<AcFrame>();
        for (var i = 0; i < 10; i++) frames.Add(new AcFrame { TMs = i * 1000, Pos = i / 10.0, Speed = 150, Gas = 1, CompletedLaps = 0, LastTimeMs = 0 });
        frames.Add(new AcFrame { TMs = 10000, Pos = 0.01, Speed = 80, Gas = 0.2, CompletedLaps = 1, LastTimeMs = 100000 });
        var laps = CornerMath.SegmentLaps(frames);
        if (laps.Count != 1 || laps[0].TimeMs != 100000 || !laps[0].Valid)
        {
            Console.Error.WriteLine("self-test: lap cut");
            return 1;
        }
        var dashSnap = new AcSnapshot
        {
            Status = 2,
            Gear = 6,
            Rpm = 7400,
            MaxRpm = 8500,
            SpeedKmh = 187.4f,
            Gas = 0.8f,
            Brake = 0f,
            Fuel = 22f,
            MaxFuel = 40f,
            TcLevel = 4,
            AbsLevel = 2,
            Position = 3,
            CurrentTimeMs = 86321,
            PerformanceMeter = -0.42f,
            CompletedLaps = 2,
            Tc = 0.25f,
            Abs = 0f,
        };
        var dash = Packets.Telemetry(dashSnap, 9);
        var back = Packets.ParseTelemetry(dash);
        if (back == null || !back.Live || back.Gear != 5 || back.Rpm != 7400 || Math.Abs(back.SpeedKmh - 187.4f) > 0.15f)
        {
            Console.Error.WriteLine("self-test: dash packet");
            return 1;
        }
        if (back.Tc != 4 || back.Abs != 2 || back.FuelPct != 55 || back.Position != 3 || back.DeltaMs != -420)
        {
            Console.Error.WriteLine("self-test: dash fields");
            return 1;
        }
        var physics = new byte[AcFields.PhysicsBytes];
        var graphics = new byte[AcFields.GraphicsFallbackBytes];
        var page = new byte[AcFields.StaticBytes];
        System.Buffers.Binary.BinaryPrimitives.WriteSingleLittleEndian(physics.AsSpan(AcFields.Gas), 0.5f);
        System.Buffers.Binary.BinaryPrimitives.WriteInt32LittleEndian(physics.AsSpan(AcFields.Gear), 3);
        System.Buffers.Binary.BinaryPrimitives.WriteInt32LittleEndian(page.AsSpan(AcFields.MaxRpm), 9000);
        System.Buffers.Binary.BinaryPrimitives.WriteInt32LittleEndian(graphics.AsSpan(AcFields.Position), 7);
        System.Buffers.Binary.BinaryPrimitives.WriteSingleLittleEndian(graphics.AsSpan(AcFields.NormalizedPosition), 0.25f);
        var decoded = AcFields.Decode(physics, graphics, page);
        if (Math.Abs(decoded.Gas - 0.5f) > 0.001f || decoded.Gear != 3 || decoded.MaxRpm != 9000 || decoded.Position != 7)
        {
            Console.Error.WriteLine("self-test: shared memory offsets");
            return 1;
        }
        if (Math.Abs(decoded.NormalizedPosition - 0.25f) > 0.001f || decoded.TcLevel != 255)
        {
            Console.Error.WriteLine("self-test: shared memory extras");
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
        buf[32] = (byte)(Proto.GearUp | Proto.GearDown);
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
    public int UiPort { get; init; } = 41513;
    public bool NoUi { get; init; }
    public string ApiBase { get; init; } = "";
    public string LogDir { get; init; } = "";

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
            UiPort = int.Parse(Value("--ui-port", "41513")),
            NoUi = Flag("--no-ui"),
            ApiBase = Value("--api", ""),
            LogDir = Value("--log-dir", ""),
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
    private int _inputHz;
    private int _outputHz;
    private int _hzMark;
    private DateTime _hzWindow = DateTime.UtcNow;
    private DateTime _lastRx = DateTime.MinValue;
    private volatile bool _run = true;
    private readonly SessionStore _store;
    private readonly AccountLink _account;
    private LocalUi? _ui;
    private bool _acOpen;
    private DateTime _acSeen = DateTime.MinValue;
    private string _uiUrl = "";
    private int _dashSeq;
    private int _dashPackets;
    private int _dashHz;
    private DateTime _lastDash = DateTime.MinValue;
    private DateTime _dashWindow = DateTime.UtcNow;

    public Receiver(Options opt)
    {
        _opt = opt;
        _output = OutputFactory.Create(opt.Output, opt.Keyboard, opt.VjoyId, FileLog.Info);
        _discovery = new UdpClient(opt.Discovery);
        _telemetry = new UdpClient(opt.Udp);
        _tcp = new TcpListener(IPAddress.Any, opt.Tcp);
        _tcp.Start();
        var root = string.IsNullOrWhiteSpace(opt.LogDir) ? SessionStore.DefaultRoot() : opt.LogDir;
        _store = new SessionStore(root);
        _account = new AccountLink(root);
        if (!string.IsNullOrWhiteSpace(opt.ApiBase))
        {
            _account.Settings.ApiBase = opt.ApiBase.TrimEnd('/');
            _account.Save();
        }
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
        FileLog.Info("Racing Wheel Pro receiver " + StatusBoard.AppVersion);
        FileLog.Info($"discovery UDP {_opt.Discovery}   telemetry UDP {_opt.Udp}   TCP {_opt.Tcp}");
        FileLog.Info("output: " + _output.Name);
        FileLog.Info("log: " + FileLog.DirectoryPath);
        if (!_opt.NoUi) _uiUrl = StartUi();
        if (_uiUrl.Length > 0) FileLog.Info("Logs UI  " + _uiUrl);
        ConsoleLifetime.Hook(() => _run = false);
        StatusBoard.Open();
        var beacon = Packets.HostBeacon(_opt.Tcp, _opt.Udp, _output.Caps, Environment.MachineName);
        Task.Run(AcceptLoop);
        Task.Run(() => TelemetryLoop(beacon));
        Task.Run(OutputLoop);
        Task.Run(AcLoop);
        Task.Run(SimulateLoop);
        Task.Run(CloudLoop);
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
            if ((DateTime.UtcNow - _lastPrint).TotalMilliseconds > 400)
            {
                PaintStatus();
                _lastPrint = DateTime.UtcNow;
            }
            Thread.Sleep(20);
        }
        _store.FlushOpen();
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
            var elapsed = (DateTime.UtcNow - _hzWindow).TotalSeconds;
            if (elapsed >= 1)
            {
                _inputHz = (int)Math.Round((_packets - _hzMark) / elapsed);
                _hzMark = _packets;
                _hzWindow = DateTime.UtcNow;
            }
        }
        var acLive = false;
        lock (_gate) acLive = _acOpen && (DateTime.UtcNow - _acSeen).TotalSeconds < 2;
        _store.OnWheel(state, acLive);
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
                shiftUp = (state.Gear & Proto.GearUp) != 0,
                shiftDown = (state.Gear & Proto.GearDown) != 0,
                from = from?.Address.ToString() ?? "sim",
            });
            FileLog.Info(line);
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
                FileLog.Info("TCP " + _peer);
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
                            FileLog.Info($"Hello {hello.Name} / {hello.Wheel} / {hello.Profile}");
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
                if (_run) FileLog.Error("TCP ended: " + ex.Message);
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

    private void PaintStatus()
    {
        string peer;
        bool phone;
        lock (_gate)
        {
            var ageMs = _lastRx == DateTime.MinValue ? int.MaxValue : (DateTime.UtcNow - _lastRx).TotalMilliseconds;
            phone = ageMs < 1500;
            peer = _phone?.Address.ToString() ?? "";
        }
        var lines = new[]
        {
            "Racing Wheel Pro  " + StatusBoard.AppVersion,
            "Listening  " + StatusBoard.LanAddress() + "   UDP " + _opt.Udp + "   TCP " + _opt.Tcp,
            phone ? "Phone  connected  " + peer : "Phone  not connected",
            "Output  " + _output.Name,
            "Log  " + FileLog.DirectoryPath,
            "Ctrl+C or close this window to exit",
        };
        StatusBoard.Draw(lines);
    }

    /// <summary>About 25 Hz. The phone drops the screen if packets stop for a moment.</summary>
    private void SendDash(AcSnapshot snap)
    {
        var now = DateTime.UtcNow;
        if ((now - _lastDash).TotalMilliseconds < 40) return;
        IPEndPoint? dest;
        lock (_gate) dest = _phone;
        if (dest is not IPEndPoint ep) return;
        _lastDash = now;
        var packet = Packets.Telemetry(snap, _dashSeq++);
        try
        {
            _telemetry.Send(packet, packet.Length, ep);
            _dashPackets++;
        }
        catch { /* phone already gone */ }
        if ((now - _dashWindow).TotalSeconds >= 1)
        {
            _dashHz = _dashPackets;
            _dashPackets = 0;
            _dashWindow = now;
        }
    }

    private void TelemetryLoop(byte[] beacon)
    {
        while (_run)
        {
            try
            {
                IPEndPoint remote = new(IPAddress.Any, 0);
                var data = _telemetry.Receive(ref remote);
                if (Packets.IsFind(data))
                {
                    try { _telemetry.Send(beacon, beacon.Length, remote); } catch { /* phone already gone */ }
                    continue;
                }
                var state = Packets.ParseState(data);
                if (state != null) Accept(state, remote);
            }
            catch (SocketException) { if (!_run) break; }
            catch (ObjectDisposedException) { break; }
        }
    }

    private void OutputLoop()
    {
        var clock = Stopwatch.StartNew();
        long next = 0;
        var sent = 0;
        var window = DateTime.UtcNow;
        while (_run)
        {
            StatePacket state;
            lock (_gate) state = _latest;
            try { _output.Apply(state); } catch { /* driver dropped */ }
            sent++;
            if ((DateTime.UtcNow - window).TotalSeconds >= 1)
            {
                lock (_gate) _outputHz = sent;
                sent = 0;
                window = DateTime.UtcNow;
            }
            next += 10;
            var wait = next - clock.ElapsedMilliseconds;
            if (wait > 1) Thread.Sleep((int)Math.Min(wait, 20));
            else if (wait < -40) next = clock.ElapsedMilliseconds;
        }
    }

    private void AcLoop()
    {
        using var ac = new AcSharedMemory();
        var clock = Stopwatch.StartNew();
        long next = 0;
        while (_run)
        {
            if (!ac.IsOpen) ac.TryOpen();
            if (ac.TryRead(out var snap) && !string.IsNullOrWhiteSpace(snap.Track))
            {
                _store.OnAc(snap);
                lock (_gate)
                {
                    _acOpen = true;
                    _acSeen = DateTime.UtcNow;
                }
                SendDash(snap);
            }
            else if (ac.TryRead(out var loose) && (loose.Rpm > 0 || loose.SpeedKmh > 1f))
            {
                SendDash(loose);
            }
            else if ((DateTime.UtcNow - _acSeen).TotalSeconds > 2)
            {
                var wasOpen = false;
                lock (_gate)
                {
                    wasOpen = _acOpen;
                    _acOpen = false;
                }
                if (wasOpen) _store.OnAcLost();
            }
            next += 10;
            var wait = next - clock.ElapsedMilliseconds;
            if (wait > 1) Thread.Sleep((int)Math.Min(wait, 30));
            else if (wait < -40) next = clock.ElapsedMilliseconds;
        }
    }

    private async Task CloudLoop()
    {
        while (_run)
        {
            try { await _account.Poll(); }
            catch (Exception ex) { _account.LastError = ex.Message; }
            var saved = _store.TakeSaved();
            if (saved != null)
            {
                try { await _account.Upload(saved); }
                catch (Exception ex) { _account.LastError = ex.Message; }
            }
            await Task.Delay(3000);
        }
    }

    private string StartUi()
    {
        for (var port = _opt.UiPort; port < _opt.UiPort + 5; port++)
        {
            try
            {
                _ui = new LocalUi(port, _store, Snapshot, (auto, api) => _account.Update(auto, string.IsNullOrWhiteSpace(api) ? _account.Settings.ApiBase : api), () => _account.StartPairing(Environment.MachineName));
                return "http://127.0.0.1:" + port + "/";
            }
            catch (HttpListenerException) { }
            catch (SocketException) { }
        }
        return "";
    }

    private UiState Snapshot()
    {
        string peer;
        int inputHz;
        int outputHz;
        bool ac;
        lock (_gate)
        {
            peer = _phone?.Address.ToString() ?? "no phone";
            inputHz = _inputHz;
            outputHz = _outputHz;
            ac = _acOpen;
        }
        return new UiState
        {
            Line = $"{peer}   in {inputHz} Hz   out {outputHz} Hz   {(ac ? "Assetto Corsa" : "no game")}   {_output.Name}",
            Code = _account.Code,
            PairStatus = _account.PairStatus,
            AutoUpload = _account.Settings.AutoUpload,
            ApiBase = _account.Settings.ApiBase,
            Linked = !string.IsNullOrEmpty(_account.Settings.DeviceToken),
            Error = _account.LastError,
            LogStatus = _store.Status,
            LogDir = _store.LogDir,
            Logs = _store.List().Take(20).ToList(),
        };
    }

    public void Dispose()
    {
        _run = false;
        try { _store.FlushOpen(); } catch { /* disk full */ }
        _ui?.Dispose();
        _output.Dispose();
        _discovery.Dispose();
        _telemetry.Dispose();
        _tcp.Stop();
    }
}
