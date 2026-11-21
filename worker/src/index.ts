import { type Env, handleRequest } from './api';

export type { Env };
export { VoiceRoom } from './voice_room';

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    return handleRequest(request, env);
  },
};
