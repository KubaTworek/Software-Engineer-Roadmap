export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const shortCode = url.pathname.replace(/^\//, '');

    if (!shortCode || shortCode.includes('/')) {
      return new Response('Not found', { status: 404 });
    }

    const cache = caches.default;
    const cacheKey = new Request(url.toString(), request);
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    const originLookupUrl = `${env.ORIGIN_BASE_URL}/internal/edge/urls/${encodeURIComponent(shortCode)}`;
    const originResponse = await fetch(originLookupUrl, {
      headers: { 'X-Edge-Token': env.EDGE_INTERNAL_TOKEN }
    });

    if (!originResponse.ok) {
      const ttl = originResponse.status === 404 ? 30 : 10;
      const response = new Response(await originResponse.text(), {
        status: originResponse.status,
        headers: { 'Cache-Control': `public, max-age=${ttl}` }
      });
      ctx.waitUntil(cache.put(cacheKey, response.clone()));
      return response;
    }

    const payload = await originResponse.json();
    const response = Response.redirect(payload.longUrl, 302);
    response.headers.set('Cache-Control', `public, max-age=${payload.cacheTtlSeconds}, stale-while-revalidate=300`);
    response.headers.set('X-URL-Shortener-Region', payload.regionId);
    ctx.waitUntil(cache.put(cacheKey, response.clone()));
    return response;
  }
};
