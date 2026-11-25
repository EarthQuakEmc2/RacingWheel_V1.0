import { withAssetContentType } from './assetType';
import { type Env, handleRequest } from './api';

export type { Env };

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const response = await handleRequest(request, env);
    if (url.pathname === '/api' || url.pathname.startsWith('/api/')) return response;
    return withAssetContentType(response, url.pathname);
  },
};
