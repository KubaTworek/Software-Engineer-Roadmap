import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const celebrityToken = __ENV.CELEBRITY_TOKEN || '';

export const options = {
  scenarios: {
    celebrity_publish_spike: {
      executor: 'per-vu-iterations', vus: 20, iterations: 5, maxDuration: '1m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(99)<1000'],
  },
};

export default function () {
  const response = http.post(`${baseUrl}/api/v1/posts`, JSON.stringify({
    content: `celebrity spike ${__VU}-${__ITER}-${Date.now()}`,
    topics: ['capacity'], visibility: 'PUBLIC',
  }), { headers: { Authorization: `Bearer ${celebrityToken}`, 'Content-Type': 'application/json' } });
  check(response, { 'write accepted': (r) => r.status === 200 || r.status === 201 });
}
