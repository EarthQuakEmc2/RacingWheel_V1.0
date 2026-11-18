using System.IO.Compression;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace RacingWheelPro.Pc;

public sealed class ReceiverSettings
{
    public bool AutoUpload { get; set; }
    public string ApiBase { get; set; } = "https://myracingwheel.site";
    public string DeviceToken { get; set; } = "";
    public string DeviceId { get; set; } = "";
}

public sealed class LogSummary
{
    public string Id { get; init; } = "";
    public string StartedAt { get; init; } = "";
    public string Track { get; init; } = "";
    public string Layout { get; init; } = "";
    public string Car { get; init; } = "";
    public string Source { get; init; } = "";
    public int Laps { get; init; }
    public int? BestLapMs { get; init; }
    public string Path { get; init; } = "";
    public string Upload { get; set; } = "";
}

/// <summary>
/// Writes gzipped JSON and CSV under %AppData%\RacingWheelPro\Logs.
/// Assetto Corsa laps are cut on completedLaps. With no game, phone inputs are stored instead.
/// </summary>
public sealed class SessionStore
{
    public static readonly JsonSerializerOptions Json = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    private readonly object _gate = new();
    private readonly string _root;
    private readonly List<AcFrame> _acFrames = new();
    private readonly List<Sample> _wheel = new();
    private string _track = "";
    private string _layout = "";
    private string _car = "";
    private string _player = "";
    private double _length;
    private DateTime _started = DateTime.UtcNow;
    private DateTime _lastWheelMotion = DateTime.MinValue;
    private DateTime _lastWheelSample = DateTime.MinValue;
    private bool _wheelActive;
    private bool _acActive;
    private int _seenLaps = -1;
    private string _status = "Waiting for Assetto Corsa or the phone";

    public SessionStore(string root)
    {
        _root = root;
        Directory.CreateDirectory(LogDir);
    }

    public string Root => _root;
    public string LogDir => Path.Combine(_root, "Logs");
    public string Status { get { lock (_gate) return _status; } }
    private string? _saved;

    public string? TakeSaved()
    {
        lock (_gate)
        {
            var path = _saved;
            _saved = null;
            return path;
        }
    }

    public static string DefaultRoot() =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "RacingWheelPro");

    public void OnAc(AcSnapshot snap)
    {
        if (string.IsNullOrWhiteSpace(snap.Track)) return;
        lock (_gate)
        {
            var layout = snap.TrackConfiguration ?? "";
            var car = string.IsNullOrWhiteSpace(snap.CarModel) ? "unknown" : snap.CarModel;
            if (!_acActive || snap.Track != _track || car != _car || layout != _layout)
            {
                if (_acActive) FlushLocked("assetto-corsa");
                _track = snap.Track;
                _layout = layout;
                _car = car;
                _player = snap.PlayerName ?? "";
                _length = snap.TrackLengthM > 1 ? snap.TrackLengthM : 0;
                _started = DateTime.UtcNow;
                _acFrames.Clear();
                _seenLaps = snap.CompletedLaps;
                _acActive = true;
            }
            _acFrames.Add(new AcFrame
            {
                TMs = _acFrames.Count == 0 ? 0 : (DateTime.UtcNow - _started).TotalMilliseconds,
                Pos = snap.NormalizedPosition,
                Speed = snap.SpeedKmh,
                Gas = snap.Gas,
                Brake = snap.Brake,
                Steer = snap.SteerAngle,
                Rpm = snap.Rpm,
                Gear = snap.Gear,
                CompletedLaps = snap.CompletedLaps,
                IsInPit = snap.IsInPit,
                LastTimeMs = snap.LastTimeMs,
            });
            if (_acFrames.Count > 200_000) _acFrames.RemoveRange(0, 50_000);
            if (snap.CompletedLaps != _seenLaps)
            {
                _seenLaps = snap.CompletedLaps;
                _status = $"{_track}  lap {snap.CompletedLaps}  last {snap.LastTimeMs} ms";
            }
            else _status = $"AC {_track} / {_car}  {snap.SpeedKmh:0} km/h";
        }
    }

    public void OnAcLost()
    {
        lock (_gate)
        {
            if (!_acActive) return;
            FlushLocked("assetto-corsa");
            _acActive = false;
            _status = "Assetto Corsa not running. Logging the phone wheel.";
        }
    }

    public void OnWheel(StatePacket state, bool acLive)
    {
        if (acLive) return;
        var moving = Math.Abs(state.Steer) > 0.04 || state.Throttle > 0.04 || state.Brake > 0.04 || state.Buttons != 0;
        var now = DateTime.UtcNow;
        lock (_gate)
        {
            if (_acActive) return;
            if (moving)
            {
                if (!_wheelActive)
                {
                    _wheelActive = true;
                    _wheel.Clear();
                    _started = now;
                    _track = "wheel";
                    _layout = "";
                    _car = "phone";
                    _length = 0;
                }
                _lastWheelMotion = now;
            }
            if (!_wheelActive) return;
            if ((now - _lastWheelSample).TotalMilliseconds < 20 && moving) return;
            _lastWheelSample = now;
            _wheel.Add(new Sample
            {
                TMs = (now - _started).TotalMilliseconds,
                Pos = -1,
                Speed = 0,
                Gas = state.Throttle,
                Brake = state.Brake,
                Steer = state.Steer * 180.0,
                Rpm = 0,
                Gear = 0,
            });
            if (!moving && (now - _lastWheelMotion).TotalSeconds >= 8 && _wheel.Count > 40)
            {
                FlushLocked("wheel");
                _wheelActive = false;
                _status = "Phone log saved";
            }
            else _status = "Logging phone inputs (Assetto Corsa is not running)";
        }
    }

    public void FlushOpen()
    {
        lock (_gate)
        {
            if (_acActive) FlushLocked("assetto-corsa");
            else if (_wheelActive) FlushLocked("wheel");
            _acActive = false;
            _wheelActive = false;
        }
    }

    public IReadOnlyList<LogSummary> List()
    {
        if (!Directory.Exists(LogDir)) return Array.Empty<LogSummary>();
        var list = new List<LogSummary>();
        foreach (var path in Directory.GetFiles(LogDir, "*.json.gz").OrderByDescending(path => path))
        {
            try
            {
                list.Add(ReadSummary(path));
            }
            catch
            {
                list.Add(new LogSummary { Id = Path.GetFileName(path), Path = path, Track = Path.GetFileName(path) });
            }
        }
        return list;
    }

    public string ReadText(string fileName)
    {
        var path = Path.Combine(LogDir, Path.GetFileName(fileName));
        if (!File.Exists(path)) return "";
        using var input = File.OpenRead(path);
        using var gzip = new GZipStream(input, CompressionMode.Decompress);
        using var reader = new StreamReader(gzip);
        return reader.ReadToEnd();
    }

    private void FlushLocked(string source)
    {
        List<LapDraft> laps;
        if (source == "assetto-corsa")
        {
            if (_acFrames.Count < 20) 
            {
                _acFrames.Clear();
                return;
            }
            laps = CornerMath.SegmentLaps(_acFrames);
            var consumed = laps.Sum(lap => lap.Samples.Count);
            if (_acFrames.Count - consumed >= 20)
            {
                laps.Add(new LapDraft
                {
                    Index = laps.Count + 1,
                    TimeMs = (int)(_acFrames[^1].TMs - _acFrames[consumed].TMs),
                    Valid = false,
                    Samples = _acFrames.Skip(consumed).Select(frame => frame.ToSample()).ToList(),
                });
            }
            if (laps.Count == 0)
            {
                laps.Add(new LapDraft
                {
                    Index = 1,
                    TimeMs = (int)_acFrames[^1].TMs,
                    Valid = false,
                    Samples = _acFrames.Select(frame => frame.ToSample()).ToList(),
                });
            }
            _acFrames.Clear();
        }
        else
        {
            if (_wheel.Count < 20)
            {
                _wheel.Clear();
                return;
            }
            laps = new List<LapDraft>
            {
                new()
                {
                    Index = 1,
                    TimeMs = (int)_wheel[^1].TMs,
                    Valid = true,
                    Samples = _wheel.ToList(),
                },
            };
            _wheel.Clear();
        }
        var length = _length > 1 ? _length : (double?)null;
        foreach (var lap in laps) lap.Corners = CornerMath.Detect(lap.Samples, length);
        var doc = new SessionDocument
        {
            Source = source,
            StartedAt = _started.ToString("yyyy-MM-ddTHH:mm:ss.fffZ"),
            Track = string.IsNullOrWhiteSpace(_track) ? "unknown" : _track,
            Layout = _layout ?? "",
            Car = string.IsNullOrWhiteSpace(_car) ? "unknown" : _car,
            TrackLengthM = length,
            Player = _player,
            Laps = laps.Select(lap => new LapDocument
            {
                Index = lap.Index,
                TimeMs = lap.TimeMs,
                Valid = lap.Valid,
                Samples = Decimate(lap.Samples, 1000).Select(sample => sample.ToArray()).ToList(),
                Corners = lap.Corners,
            }).ToList(),
        };
        var stamp = _started.ToString("yyyyMMdd-HHmmss");
        var safeTrack = new string((doc.Track + (string.IsNullOrEmpty(doc.Layout) ? "" : "-" + doc.Layout)).Select(ch => Path.GetInvalidFileNameChars().Contains(ch) ? '_' : ch).ToArray());
        var baseName = $"{stamp}-{safeTrack}";
        Directory.CreateDirectory(LogDir);
        WriteGzip(Path.Combine(LogDir, baseName + ".json.gz"), JsonSerializer.Serialize(doc, Json));
        WriteGzip(Path.Combine(LogDir, baseName + ".csv.gz"), ToCsv(doc));
        _saved = Path.Combine(LogDir, baseName + ".json.gz");
        _status = $"Saved {baseName}";
    }

    private static LogSummary ReadSummary(string path)
    {
        using var input = File.OpenRead(path);
        using var gzip = new GZipStream(input, CompressionMode.Decompress);
        var doc = JsonSerializer.Deserialize<SessionDocument>(gzip, Json) ?? new SessionDocument();
        int? best = null;
        foreach (var lap in doc.Laps)
        {
            if (!lap.Valid || lap.TimeMs is not int time || time <= 0) continue;
            if (best == null || time < best) best = time;
        }
        return new LogSummary
        {
            Id = Path.GetFileName(path),
            StartedAt = doc.StartedAt,
            Track = doc.Track,
            Layout = doc.Layout,
            Car = doc.Car,
            Source = doc.Source,
            Laps = doc.Laps.Count,
            BestLapMs = best,
            Path = path,
        };
    }

    public static List<Sample> Decimate(List<Sample> samples, int maxPoints)
    {
        if (samples.Count <= maxPoints) return samples;
        var step = samples.Count / (double)maxPoints;
        var list = new List<Sample>(maxPoints);
        for (var i = 0; i < maxPoints; i++) list.Add(samples[Math.Min(samples.Count - 1, (int)Math.Floor(i * step))]);
        list[^1] = samples[^1];
        return list;
    }

    private static string ToCsv(SessionDocument doc)
    {
        var builder = new StringBuilder();
        builder.AppendLine("lap,t_ms,pos,speed_kmh,gas,brake,steer_deg,rpm,gear");
        foreach (var lap in doc.Laps)
        {
            foreach (var sample in lap.Samples)
            {
                builder.Append(lap.Index).Append(',').Append(sample[0].ToString(System.Globalization.CultureInfo.InvariantCulture)).Append(',')
                    .Append(sample[1].ToString(System.Globalization.CultureInfo.InvariantCulture)).Append(',')
                    .Append(sample[2].ToString(System.Globalization.CultureInfo.InvariantCulture)).Append(',')
                    .Append(sample[3].ToString(System.Globalization.CultureInfo.InvariantCulture)).Append(',')
                    .Append(sample[4].ToString(System.Globalization.CultureInfo.InvariantCulture)).Append(',')
                    .Append(sample[5].ToString(System.Globalization.CultureInfo.InvariantCulture)).Append(',')
                    .Append(sample[6].ToString(System.Globalization.CultureInfo.InvariantCulture)).Append(',')
                    .Append(sample[7].ToString(System.Globalization.CultureInfo.InvariantCulture)).AppendLine();
            }
        }
        return builder.ToString();
    }

    private static void WriteGzip(string path, string text)
    {
        using var output = File.Create(path);
        using var gzip = new GZipStream(output, CompressionLevel.SmallestSize);
        var bytes = Encoding.UTF8.GetBytes(text);
        gzip.Write(bytes, 0, bytes.Length);
    }
}

public sealed class SessionDocument
{
    public string Schema { get; set; } = "racing-wheel-pro.session/v1";
    public string Source { get; set; } = "assetto-corsa";
    public string StartedAt { get; set; } = "";
    public string Track { get; set; } = "";
    public string Layout { get; set; } = "";
    public string Car { get; set; } = "";
    public double? TrackLengthM { get; set; }
    public string Player { get; set; } = "";
    public List<LapDocument> Laps { get; set; } = new();
}

public sealed class LapDocument
{
    public int Index { get; set; }
    public int? TimeMs { get; set; }
    public bool Valid { get; set; }
    public List<double[]> Samples { get; set; } = new();
    public List<CornerMetric> Corners { get; set; } = new();
}

public sealed class AccountLink
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(30) };
    private readonly string _settingsPath;
    private readonly object _gate = new();
    public ReceiverSettings Settings { get; private set; } = new();
    public string Code { get; private set; } = "";
    public string PollSecret { get; private set; } = "";
    public string PairStatus { get; private set; } = "Not linked";
    public string LastError { get; set; } = "";

    public AccountLink(string root)
    {
        Directory.CreateDirectory(root);
        _settingsPath = Path.Combine(root, "settings.json");
        if (File.Exists(_settingsPath))
        {
            try { Settings = JsonSerializer.Deserialize<ReceiverSettings>(File.ReadAllText(_settingsPath), SessionStore.Json) ?? new ReceiverSettings(); }
            catch { Settings = new ReceiverSettings(); }
        }
        if (!string.IsNullOrEmpty(Settings.DeviceId)) PairStatus = "Linked";
    }

    public void Save()
    {
        lock (_gate) File.WriteAllText(_settingsPath, JsonSerializer.Serialize(Settings, SessionStore.Json));
    }

    public void Update(bool autoUpload, string apiBase)
    {
        Settings.AutoUpload = autoUpload;
        if (!string.IsNullOrWhiteSpace(apiBase)) Settings.ApiBase = apiBase.TrimEnd('/');
        Save();
    }

    public async Task StartPairing(string name)
    {
        var url = Settings.ApiBase.TrimEnd('/') + "/api/pair/start";
        var response = await Http.PostAsync(url, new StringContent(JsonSerializer.Serialize(new { name }), Encoding.UTF8, "application/json"));
        var text = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException(text);
        var doc = JsonDocument.Parse(text);
        Code = doc.RootElement.GetProperty("code").GetString() ?? "";
        PollSecret = doc.RootElement.GetProperty("pollSecret").GetString() ?? "";
        PairStatus = "Waiting for the website";
        LastError = "";
    }

    public async Task Poll()
    {
        if (string.IsNullOrEmpty(PollSecret) || !string.IsNullOrEmpty(Settings.DeviceToken)) return;
        var url = Settings.ApiBase.TrimEnd('/') + "/api/pair/poll";
        var response = await Http.PostAsync(url, new StringContent(JsonSerializer.Serialize(new { pollSecret = PollSecret }), Encoding.UTF8, "application/json"));
        var text = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode)
        {
            LastError = text;
            return;
        }
        var doc = JsonDocument.Parse(text);
        var status = doc.RootElement.GetProperty("status").GetString();
        if (status == "linked")
        {
            Settings.DeviceToken = doc.RootElement.GetProperty("deviceToken").GetString() ?? "";
            Settings.DeviceId = doc.RootElement.GetProperty("deviceId").GetString() ?? "";
            Save();
            PairStatus = "Linked";
            PollSecret = "";
        }
        else if (status == "expired")
        {
            PairStatus = "Code expired";
            Code = "";
            PollSecret = "";
        }
        else PairStatus = "Waiting for the website";
    }

    public async Task Upload(string jsonPath)
    {
        if (!Settings.AutoUpload || string.IsNullOrEmpty(Settings.DeviceToken)) return;
        var bytes = await File.ReadAllBytesAsync(jsonPath);
        using var content = new ByteArrayContent(bytes);
        content.Headers.ContentType = new MediaTypeHeaderValue("application/gzip");
        content.Headers.ContentEncoding.Add("gzip");
        using var request = new HttpRequestMessage(HttpMethod.Post, Settings.ApiBase.TrimEnd('/') + "/api/sessions");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", Settings.DeviceToken);
        request.Content = content;
        var response = await Http.SendAsync(request);
        var text = await response.Content.ReadAsStringAsync();
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException(text);
    }
}
