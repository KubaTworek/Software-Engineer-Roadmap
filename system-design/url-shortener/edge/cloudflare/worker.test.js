import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.js';

function harness(originResponse) {
  const entries = new Map();
  globalThis.caches = {
    default: {
      match: request => Promise.resolve(entries.get(request.url)),
      put: async (request, response) => entries.set(request.url, response)
    }
  };
  globalThis.fetch = async () => originResponse.clone();
  const pending = [];
  return { entries, ctx: { waitUntil: promise => pending.push(promise) }, pending };
}

test('does not cache an origin 5xx response', async () => {
  const h = harness(new Response('down', { status: 503 }));
  const response = await worker.fetch(new Request('https://sho.rt/code'), { ORIGIN_BASE_URL: 'https://origin', EDGE_INTERNAL_TOKEN: 'secret' }, h.ctx);
  await Promise.all(h.pending);
  assert.equal(response.status, 503);
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  assert.equal(h.entries.size, 0);
});

test('redirect response is cacheable and query parameters do not fragment lookup', async () => {
  const payload = { longUrl: 'https://example.com/path', cacheTtlSeconds: 30, regionId: 'eu-1' };
  const h = harness(Response.json(payload));
  const env = { ORIGIN_BASE_URL: 'https://origin', EDGE_INTERNAL_TOKEN: 'secret' };
  const first = await worker.fetch(new Request('https://sho.rt/code?campaign=a'), env, h.ctx);
  await Promise.all(h.pending);
  const second = await worker.fetch(new Request('https://sho.rt/code?campaign=b'), env, h.ctx);

  assert.equal(first.status, 302);
  assert.equal(first.headers.get('Location'), payload.longUrl);
  assert.equal(second.status, 302);
  assert.equal(h.entries.size, 1);
});
