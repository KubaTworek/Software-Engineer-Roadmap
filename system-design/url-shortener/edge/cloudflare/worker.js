export default {
  async fetch(request, env, ctx) {
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      return new Response('Method not allowed', { status: 405, headers: { Allow: 'GET, HEAD' } });
    }
    const url = new URL(request.url);
    const shortCode = url.pathname.replace(/^\//, '');

    if (!shortCode || shortCode.includes('/')) {
      return new Response('Not found', { status: 404 });
    }

    const cache = caches.default;
    // Query parameters do not affect lookup and must not fragment the edge cache.
    const cacheKey = new Request(`${url.origin}/${encodeURIComponent(shortCode)}`);
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    const originLookupUrl = `${env.ORIGIN_BASE_URL}/internal/edge/urls/${encodeURIComponent(shortCode)}`;
    const originResponse = await fetch(originLookupUrl, {
      headers: { 'X-Edge-Token': env.EDGE_INTERNAL_TOKEN }
    });

    if (!originResponse.ok) {
      const cacheableNegative = originResponse.status === 404 || originResponse.status === 410;
      const ttl = originResponse.status === 404 ? 30 : 5;
      const response = new Response(await originResponse.text(), {
        status: originResponse.status,
        headers: { 'Cache-Control': cacheableNegative ? `public, max-age=${ttl}` : 'no-store' }
      });
      if (cacheableNegative) ctx.waitUntil(cache.put(cacheKey, response.clone()));
      return response;
    }

    const payload = await originResponse.json();
    // A constructed response has mutable headers; Response.redirect() may expose an immutable guard.
    const response = new Response(null, {
      status: 302,
      headers: {
        Location: payload.longUrl,
        'Cache-Control': `public, max-age=${payload.cacheTtlSeconds}`,
        'X-URL-Shortener-Region': payload.regionId
      }
    });
    ctx.waitUntil(cache.put(cacheKey, response.clone()));
    return response;
  }
};
