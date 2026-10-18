import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const token = __ENV.AUTH_TOKEN || '';
const tokens = (__ENV.AUTH_TOKENS || token).split(',').filter(Boolean);
const testType = __ENV.TEST_TYPE || 'load';

const profiles = {
  load: [
    { target: 100, duration: '2m' },
    { target: 100, duration: '5m' },
    { target: 0, duration: '1m' },
  ],
  stress: [
    { target: 100, duration: '2m' },
    { target: 300, duration: '3m' },
    { target: 600, duration: '3m' },
    { target: 0, duration: '2m' },
  ],
  spike: [
    { target: 50, duration: '1m' },
    { target: 1000, duration: '15s' },
    { target: 50, duration: '2m' },
    { target: 0, duration: '30s' },
  ],
  soak: [
    { target: 100, duration: '2m' },
    { target: 100, duration: '30m' },
    { target: 0, duration: '2m' },
  ],
};

export const options = {
  scenarios: {
    // Open model ujawnia nasycenie: liczba nowych żądań nie zwalnia tylko dlatego,
    // że serwer odpowiada wolniej. Closed model mógłby ukryć coordinated omission.
    feed_load: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 500,
      stages: profiles[testType] || profiles.load,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300', 'p(99)<750'],
    dropped_iterations: ['count<10'],
  },
};

export default function () {
  // Tokeny powinny pochodzić z datasetu o rozkładzie Zipfa. Najbardziej aktywni
  // użytkownicy są wybierani częściej, co ujawnia hot keys i nierówny cache.
  const selectedToken = tokens.length === 0 ? token : tokens[zipfIndex(tokens.length, 1.15)];
  const response = http.get(`${baseUrl}/api/v1/feed?limit=20`, {
    headers: { Authorization: `Bearer ${selectedToken}` },
    timeout: '2s',
  });
  check(response, {
    'feed returned 200': (r) => r.status === 200,
    'response is bounded': (r) => r.body.length < 2_000_000,
  });
}

function zipfIndex(size, exponent) {
  if (size <= 1) return 0;
  const harmonic = Array.from({ length: size }, (_, i) => 1 / Math.pow(i + 1, exponent))
    .reduce((a, b) => a + b, 0);
  let value = Math.random() * harmonic;
  for (let i = 0; i < size; i += 1) {
    value -= 1 / Math.pow(i + 1, exponent);
    if (value <= 0) return i;
  }
  return size - 1;
}

export function handleSummary(data) {
  return {
    'capacity-report.json': JSON.stringify({
      profile: testType,
      generatedAt: new Date().toISOString(),
      thresholds: data.metrics,
      interpretation: 'Capacity kończy się przy pierwszym trwałym naruszeniu SLO lub wzroście saturation/dropped_iterations.',
    }, null, 2),
    stdout: `Profile ${testType}: p95=${data.metrics.http_req_duration?.values?.['p(95)']} ms, `
      + `p99=${data.metrics.http_req_duration?.values?.['p(99)']} ms\n`,
  };
}
