import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const writerKey = __ENV.WRITER_KEY || 'demo-writer-key';
const viewerKey = __ENV.VIEWER_KEY || 'demo-viewer-key';
const profile = __ENV.K6_PROFILE || 'load';
const errors = new Rate('telemetry_errors');
const ingestLatency = new Trend('ingest_latency', true);
const queryLatency = new Trend('query_latency', true);

const profiles = {
  load: { executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s', duration: '2m', preAllocatedVUs: 20, maxVUs: 100 },
  stress: { executor: 'ramping-arrival-rate', startRate: 20, timeUnit: '1s', preAllocatedVUs: 30, maxVUs: 250,
    stages: [{ target: 100, duration: '2m' }, { target: 300, duration: '3m' }, { target: 0, duration: '1m' }] },
  spike: { executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 400,
    stages: [{ target: 10, duration: '30s' }, { target: 500, duration: '10s' }, { target: 10, duration: '1m' }] },
  soak: { executor: 'constant-arrival-rate', rate: 40, timeUnit: '1s', duration: '30m', preAllocatedVUs: 20, maxVUs: 100 },
};

export const options = {
  scenarios: {
    ingest: { ...profiles[profile], exec: 'ingest', tags: { workload: profile } },
    query: { executor: 'constant-vus', vus: 5, duration: profiles[profile].duration || '6m', exec: 'query' },
  },
  thresholds: {
    telemetry_errors: ['rate<0.01'],
    ingest_latency: ['p(95)<500', 'p(99)<1000'],
    query_latency: ['p(95)<1000', 'p(99)<2000'],
    dropped_iterations: ['count==0'],
  },
};

export function ingest() {
  const payload = JSON.stringify({ logs: [{ level: 'INFO', service: 'k6', host: 'load-generator',
    message: `load-event-${__VU}-${__ITER}`, attributes: { profile } }] });
  const response = http.post(`${baseUrl}/api/v1/ingest/logs`, payload,
    { headers: { 'Content-Type': 'application/json', 'X-API-Key': writerKey } });
  ingestLatency.add(response.timings.duration);
  errors.add(!check(response, { 'ingest accepted': r => r.status === 202 }));
}

export function query() {
  const response = http.get(`${baseUrl}/api/v1/query/logs?tenantId=demo&service=k6&limit=100`,
    { headers: { 'X-API-Key': viewerKey } });
  queryLatency.add(response.timings.duration);
  errors.add(!check(response, { 'query succeeds': r => r.status === 200 }));
  sleep(0.2);
}
