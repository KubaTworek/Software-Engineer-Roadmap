import http from 'k6/http';
import { check, sleep } from 'k6';

const profile = __ENV.PROFILE || 'load';
const scenarios = {
  load: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 25), timeUnit: '1s', duration: '2m', preAllocatedVUs: 20, maxVUs: 100 },
  spike: { executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 300,
    stages: [{ target: 10, duration: '20s' }, { target: 250, duration: '15s' }, { target: 10, duration: '45s' }] },
  soak: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 10), timeUnit: '1s', duration: __ENV.DURATION || '30m', preAllocatedVUs: 20, maxVUs: 80 }
};

export const options = {
  scenarios: { notification_pipeline: scenarios[profile] },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '30s' }],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99']
  }
};

export default function () {
  const unique = `${__VU}-${__ITER}-${Date.now()}`;
  const body = JSON.stringify({
    userId: `load-${__VU}`, notificationType: 'PAYMENT_FAILED', channels: ['EMAIL'],
    contactPoint: { email: `load-${__VU}@example.com` },
    payload: { firstName: 'Load', invoiceId: unique, amount: '10' }, idempotencyKey: unique
  });
  const response = http.post(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1/notifications`, body,
    { headers: { 'Content-Type': 'application/json', 'X-Tenant-Id': __ENV.TENANT || 'load-test' } });
  check(response, { 'accepted or deliberately shed': r => r.status === 202 || r.status === 429 });
  sleep(0.05);
}
