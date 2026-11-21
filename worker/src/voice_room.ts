import {
  admit,
  claimPtt,
  inspectAudio,
  noteFrame,
  prepareVoice,
  recentJoins,
  releasePtt,
  type RadioPeer,
} from './voice_logic';

/**
 * One Durable Object per room. WebSocket hibernation keeps idle drivers from
 * burning duration. Audio frames are copied to the other sockets and dropped.
 * Nothing is written to SQLite.
 */

interface SocketState extends RadioPeer {}

interface CfSocket {
  send(data: string | ArrayBuffer): void;
  close(code?: number, reason?: string): void;
  serializeAttachment(value: SocketState): void;
  deserializeAttachment(): SocketState | null;
  readyState: number;
}

interface VoiceCtx {
  acceptWebSocket(ws: CfSocket): void;
  getWebSockets(): CfSocket[];
}

declare const WebSocketPair: {
  new (): { 0: CfSocket; 1: CfSocket };
};

const OPEN = 1;

export class VoiceRoom {
  private joins: number[] = [];

  constructor(private readonly ctx: VoiceCtx, _env: unknown) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const prepared = prepareVoice(url.searchParams.get('room'), url.searchParams.get('name'));
    if (!prepared.ok) return Response.json({ error: prepared.error }, { status: prepared.status });
    if ((request.headers.get('upgrade') || '').toLowerCase() !== 'websocket') {
      return Response.json({ error: 'WebSocket upgrade required' }, { status: 426 });
    }
    const now = Date.now();
    const live = this.states();
    this.joins = recentJoins(this.joins, now);
    const gate = admit(live.length, this.joins.length);
    if (gate === 'full') return Response.json({ error: 'Room is full' }, { status: 429 });
    if (gate === 'rate') return Response.json({ error: 'Too many joins' }, { status: 429 });

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    const state: SocketState = {
      id: crypto.randomUUID().replaceAll('-', '').slice(0, 8),
      name: prepared.name,
      ptt: false,
      windowStart: now,
      frames: 0,
      joinedAt: now,
    };
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment(state);
    this.joins.push(now);
    const others = this.states().filter((peer) => peer.id !== state.id);
    server.send(JSON.stringify({ op: 'hello', id: state.id, count: others.length }));
    this.broadcast(server, { op: 'peer', id: state.id, name: state.name, join: true, count: others.length + 1 });
    return new Response(null, { status: 101, webSocket: client } as ResponseInit);
  }

  async webSocketMessage(ws: CfSocket, message: ArrayBuffer | string) {
    const state = ws.deserializeAttachment();
    if (!state) return;
    if (typeof message === 'string') {
      if (message.length > 200) return;
      let op = '';
      let down = false;
      try {
        const body = JSON.parse(message) as { op?: unknown; down?: unknown };
        op = typeof body.op === 'string' ? body.op : '';
        down = body.down === true;
      } catch {
        return;
      }
      if (op === 'ptt') this.setFloor(ws, state, down);
      else if (op === 'bye') {
        try {
          ws.close(1000, 'bye');
        } catch {
          /* already gone */
        }
      }
      return;
    }
    const packet = new Uint8Array(message);
    if (!state.ptt) return;
    if (!inspectAudio(packet).ok) return;
    const now = Date.now();
    if (!noteFrame(state, now)) {
      ws.serializeAttachment(state);
      return;
    }
    ws.serializeAttachment(state);
    const copy = packet.buffer.slice(packet.byteOffset, packet.byteOffset + packet.byteLength);
    for (const peer of this.ctx.getWebSockets()) {
      if (peer === ws || peer.readyState !== OPEN) continue;
      try {
        peer.send(copy);
      } catch {
        /* dropped listener */
      }
    }
  }

  async webSocketClose(ws: CfSocket) {
    this.forget(ws);
  }

  async webSocketError(ws: CfSocket) {
    this.forget(ws);
  }

  private setFloor(ws: CfSocket, state: SocketState, down: boolean) {
    const peers = this.states();
    if (down) {
      const claimed = claimPtt(peers, state.id);
      if (!claimed.ok) {
        try {
          ws.send(JSON.stringify({ op: 'busy', name: claimed.name }));
        } catch {
          /* sender gone */
        }
        return;
      }
      state.ptt = true;
      ws.serializeAttachment(state);
      this.broadcast(null, { op: 'talk', id: state.id, name: state.name, down: true });
      return;
    }
    releasePtt(peers, state.id);
    state.ptt = false;
    ws.serializeAttachment(state);
    this.broadcast(null, { op: 'talk', id: state.id, name: state.name, down: false });
  }

  private forget(ws: CfSocket) {
    const state = ws.deserializeAttachment();
    if (!state) return;
    const talking = state.ptt;
    state.ptt = false;
    ws.serializeAttachment(state);
    if (talking) this.broadcast(ws, { op: 'talk', id: state.id, name: state.name, down: false });
    const count = this.states().filter((peer) => peer.id !== state.id).length;
    this.broadcast(ws, { op: 'peer', id: state.id, name: state.name, join: false, count });
  }

  private states(): SocketState[] {
    const out: SocketState[] = [];
    for (const ws of this.ctx.getWebSockets()) {
      const state = ws.deserializeAttachment();
      if (state) out.push(state);
    }
    return out;
  }

  private broadcast(except: CfSocket | null, body: unknown) {
    const text = JSON.stringify(body);
    for (const peer of this.ctx.getWebSockets()) {
      if (peer === except || peer.readyState !== OPEN) continue;
      try {
        peer.send(text);
      } catch {
        /* dropped listener */
      }
    }
  }
}
