import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    users_read: { executor: 'constant-vus', vus: 20, duration: '60s' },
  },
  thresholds: {
    http_req_duration: ['p(95)<250'],
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
  sleep(0.1);
}
