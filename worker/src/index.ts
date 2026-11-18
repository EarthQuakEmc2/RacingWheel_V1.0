import { type Env, handleRequest } from './api';

export type { Env };

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    return handleRequest(request, env);
  },
};
