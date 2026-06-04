import http from 'k6/http';
import { sleep } from 'k6';
import { CATALOG_URL, makeSummary } from './lib/config.js';
import { browseEvents, getAvailability } from './lib/flows.js';

export const options = {
  scenarios: {
    db_slow: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 15),
      timeUnit: '1s',
      duration: __ENV.DURATION || '90s',
      preAllocatedVUs: 30,
      maxVUs: 120,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<2500', 'p(99)<4000'],
    checks: ['rate>0.90'],
  },
};

export function setup() {
  http.post(`${CATALOG_URL}/internal/chaos/catalog/db-delay`, JSON.stringify({ delayMs: Number(__ENV.DB_DELAY_MS || 1200) }), {
    headers: { 'Content-Type': 'application/json' },
  });
}

export default function () {
  browseEvents();
  getAvailability();
  sleep(0.2);
}

export function teardown() {
  http.post(`${CATALOG_URL}/internal/chaos/catalog/reset`);
}

export function handleSummary(data) {
  return makeSummary('db-slow', data);
}
