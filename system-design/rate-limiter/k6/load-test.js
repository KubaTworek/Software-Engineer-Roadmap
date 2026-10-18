import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    // Otwarty model utrzymuje zadany napływ niezależnie od opóźnienia serwera,
    // więc przeciążenie nie ukrywa popytu przez coordinated omission.
    users_read: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 200),
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 50,
      maxVUs: 500,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<250'],
    dropped_iterations: ['count==0'],
  },
};

export default function () {
  const headers = {
    'X-Tenant-Id': 'acme',
    'X-User-Id': `user-${__VU}`,
    'X-Plan': 'PRO',
    'X-Api-Key': `secret-key-${__VU}`,
  };
  const res = http.get('http://localhost:8080/api/users', { headers });
  check(res, {
    'status is 200 or 429': r => r.status === 200 || r.status === 429,
    'has limit header': r => r.headers['X-Ratelimit-Limit'] !== undefined,
  });
}
