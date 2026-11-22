using System.Net;
using System.Text;
using System.Text.Json;

namespace RacingWheelPro.Pc;

/// <summary>
/// Loopback page for pairing, the upload switch, and the local log list.
/// </summary>
public sealed class LocalUi : IDisposable
{
    private readonly HttpListener _listener = new();
    private readonly Func<UiState> _state;
    private readonly Action<bool, string> _saveSettings;
    private readonly Func<Task> _pair;
    private readonly SessionStore _store;
    public int Port { get; }

    public LocalUi(int port, SessionStore store, Func<UiState> state, Action<bool, string> saveSettings, Func<Task> pair)
    {
        _store = store;
        _state = state;
        _saveSettings = saveSettings;
        _pair = pair;
        Port = port;
        _listener.Prefixes.Add($"http://127.0.0.1:{port}/");
        _listener.Start();
        Task.Run(Loop);
    }

    private async Task Loop()
    {
        while (_listener.IsListening)
        {
            HttpListenerContext ctx;
            try { ctx = await _listener.GetContextAsync(); }
            catch { break; }
            try { await Handle(ctx); }
            catch (Exception ex)
            {
                var bytes = Encoding.UTF8.GetBytes(ex.Message);
                ctx.Response.StatusCode = 500;
                ctx.Response.OutputStream.Write(bytes, 0, bytes.Length);
                ctx.Response.Close();
            }
        }
    }

    private async Task Handle(HttpListenerContext ctx)
    {
        var path = ctx.Request.Url?.AbsolutePath ?? "/";
        if (path == "/api/status")
        {
            Write(ctx, JsonSerializer.Serialize(_state(), SessionStore.Json), "application/json");
            return;
        }
        if (path == "/api/logs")
        {
            Write(ctx, JsonSerializer.Serialize(new { logs = _store.List() }, SessionStore.Json), "application/json");
            return;
        }
        if (path.StartsWith("/api/logs/", StringComparison.Ordinal))
        {
            var name = Uri.UnescapeDataString(path["/api/logs/".Length..]);
            Write(ctx, _store.ReadText(name), "application/json");
            return;
        }
        if (path == "/api/settings" && ctx.Request.HttpMethod == "POST")
        {
            using var reader = new StreamReader(ctx.Request.InputStream);
            var body = JsonDocument.Parse(await reader.ReadToEndAsync());
            var auto = body.RootElement.TryGetProperty("autoUpload", out var flag) && flag.GetBoolean();
            var api = body.RootElement.TryGetProperty("apiBase", out var apiBase) ? apiBase.GetString() ?? "" : "";
            _saveSettings(auto, api);
            Write(ctx, "{\"ok\":true}", "application/json");
            return;
        }
        if (path == "/api/pair" && ctx.Request.HttpMethod == "POST")
        {
            await _pair();
            Write(ctx, "{\"ok\":true}", "application/json");
            return;
        }
        Write(ctx, Page(_state()), "text/html; charset=utf-8");
    }

    private static void Write(HttpListenerContext ctx, string text, string type)
    {
        var bytes = Encoding.UTF8.GetBytes(text);
        ctx.Response.ContentType = type;
        ctx.Response.ContentLength64 = bytes.Length;
        ctx.Response.OutputStream.Write(bytes, 0, bytes.Length);
        ctx.Response.Close();
    }

    public void Dispose()
    {
        try { _listener.Stop(); } catch { /* already stopped */ }
        _listener.Close();
    }

    private static string Page(UiState state)
    {
        var code = string.IsNullOrEmpty(state.Code) ? "————" : state.Code;
        var logs = new StringBuilder();
        foreach (var log in state.Logs)
        {
            logs.Append("<tr><td>").Append(WebUtility.HtmlEncode(log.StartedAt)).Append("</td><td>")
                .Append(WebUtility.HtmlEncode(log.Track)).Append(' ').Append(WebUtility.HtmlEncode(log.Layout)).Append("</td><td>")
                .Append(WebUtility.HtmlEncode(log.Car)).Append("</td><td>").Append(log.Laps).Append("</td><td>")
                .Append(log.BestLapMs?.ToString() ?? "—").Append("</td><td>")
                .Append(WebUtility.HtmlEncode(Path.GetFileName(log.Path))).Append("</td></tr>");
        }
        if (state.Logs.Count == 0) logs.Append("<tr><td colspan='6'>No sessions yet.</td></tr>");
        return $$"""
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>Racing Wheel Pro</title>
<meta http-equiv="refresh" content="3">
<style>
body{margin:0;background:#0b0d10;color:#f3f1ec;font:16px/1.4 "Segoe UI",sans-serif}
main{max-width:980px;margin:0 auto;padding:24px}
h1{font-weight:500;letter-spacing:.04em;text-transform:uppercase}
.code{font:700 48px/1 ui-monospace,monospace;letter-spacing:.12em;color:#f5c518}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}
section{background:#14181d;border:1px solid #2c343d;padding:16px}
table{width:100%;border-collapse:collapse} td,th{text-align:left;padding:6px 8px;border-bottom:1px solid #2c343d}
button,input{font:inherit;background:#0e1217;color:#f3f1ec;border:1px solid #46515c;padding:8px 12px}
button{background:#e10600;border-color:#e10600;cursor:pointer}
.muted{color:#8e99a3}
</style></head>
<body><main>
<h1>Racing Wheel Pro</h1>
<p class="muted">{{WebUtility.HtmlEncode(state.Line)}}</p>
<div class="grid">
<section>
<h2>Link this PC</h2>
<p class="code">{{WebUtility.HtmlEncode(code)}}</p>
<p>{{WebUtility.HtmlEncode(state.PairStatus)}}</p>
<p class="muted">Enter the code at <a href="https://myracingwheel.site/account" style="color:#f5c518">myracingwheel.site/account</a>.</p>
<button type="button" onclick="fetch('/api/pair',{method:'POST'}).then(()=>location.reload())">New code</button>
</section>
<section>
<h2>Upload</h2>
<form onsubmit="event.preventDefault();fetch('/api/settings',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({autoUpload:document.getElementById('up').checked,apiBase:document.getElementById('api').value})}).then(()=>location.reload())">
<label><input id="up" type="checkbox" {{(state.AutoUpload ? "checked" : "")}}> Upload sessions automatically</label>
<p><input id="api" value="{{WebUtility.HtmlEncode(state.ApiBase)}}" size="42" aria-label="API base"></p>
<button type="submit">Save</button>
<p class="muted">{{WebUtility.HtmlEncode(state.Linked ? "This PC is linked." : "Not linked yet.")}} {{WebUtility.HtmlEncode(state.Error)}}</p>
</form>
</section>
</div>
<section>
<h2>Community</h2>
<p>Chat with other drivers, hear about updates, vote in polls, and grab downloads.</p>
<p><a href="{{WebUtility.HtmlEncode(Community.Telegram)}}" style="color:#f5c518">Join our Telegram</a></p>
</section>
<section>
<h2>Logs</h2>
<p class="muted">{{WebUtility.HtmlEncode(state.LogStatus)}} Files are gzipped JSON and CSV in {{WebUtility.HtmlEncode(state.LogDir)}}.</p>
<table><thead><tr><th>Started</th><th>Track</th><th>Car</th><th>Laps</th><th>Best ms</th><th>File</th></tr></thead>
<tbody>{{logs}}</tbody></table>
</section>
</main></body></html>
""";
    }
}

public sealed class UiState
{
    public string Line { get; set; } = "";
    public string Code { get; set; } = "";
    public string PairStatus { get; set; } = "";
    public bool AutoUpload { get; set; }
    public string ApiBase { get; set; } = "";
    public bool Linked { get; set; }
    public string Error { get; set; } = "";
    public string LogStatus { get; set; } = "";
    public string LogDir { get; set; } = "";
    public List<LogSummary> Logs { get; set; } = new();
}
