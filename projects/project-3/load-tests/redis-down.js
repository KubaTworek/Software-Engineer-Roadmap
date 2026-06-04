import { sleep } from 'k6';
import { check } from 'k6';
import { browseEvents, getAvailability } from './lib/flows.js';
import { makeSummary } from './lib/config.js';

export const options = {
  scenarios: {
    redis_down_read_path: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 25),
      timeUnit: '1s',
      duration: __ENV.DURATION || '90s',
      preAllocatedVUs: 30,
      maxVUs: 120,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<1500', 'p(99)<3000'],
    checks: ['rate>0.90'],
  },
};

// Fault injection before this test:
// docker compose stop redis
// Expected behavior: catalog read path falls back to DB; gateway rate limiter fails open.
export default function () {
  const events = browseEvents();
  const availability = getAvailability();
  check(events, { 'events still available without redis': (r) => r.status === 200 });
  check(availability, { 'availability still available without redis': (r) => r.status === 200 });
  sleep(0.2);
}

export function handleSummary(data) {
  return makeSummary('redis-down', data);
}
