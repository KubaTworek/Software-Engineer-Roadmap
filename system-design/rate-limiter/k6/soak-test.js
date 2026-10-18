import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    sustained_api: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 100),
      timeUnit: '1s',
      duration: __ENV.DURATION || '30m',
      preAllocatedVUs: 50,
      maxVUs: 500,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<250', 'p(99)<500'],
    http_req_failed: ['rate<0.01'],
    dropped_iterations: ['count==0'],
  },
};

export default function () {
  const id = `${__VU}-${__ITER % 1000}`;
  const response = http.get('http://localhost:8080/api/users', {
    headers: {'X-Api-Key': `soak-${id}`},
  });
  check(response, {
    'request has an explicit admission decision': r => r.status === 200 || r.status === 429,
    'standard rate limit contract is present': r => r.headers['Ratelimit-Limit'] !== undefined,
  });
}
