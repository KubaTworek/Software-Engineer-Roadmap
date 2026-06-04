import http from 'k6/http';
import { check, sleep } from 'k6';
import { PAYMENT_URL, makeSummary } from './lib/config.js';
import { createOrderHappyPath } from './lib/flows.js';

export const options = {
  scenarios: {
    payment_errors: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 8),
      timeUnit: '1s',
      duration: __ENV.DURATION || '90s',
      preAllocatedVUs: 30,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.20'],
    http_req_duration: ['p(95)<2500'],
    checks: ['rate>0.85'],
  },
};

export function setup() {
  http.post(`${PAYMENT_URL}/internal/chaos/payment`, JSON.stringify({ failureRate: Number(__ENV.PAYMENT_FAILURE_RATE || 0.75), maxDelayMs: 300 }), {
    headers: { 'Content-Type': 'application/json' },
  });
}

export default function () {
  const result = createOrderHappyPath();
  if (result.order) {
    check(result.order, {
      'order survives payment errors': (r) => r.status === 200 && ['PAYMENT_PENDING', 'PAID'].includes(r.json('status')),
    });
  }
  sleep(0.2);
}

export function teardown() {
  http.post(`${PAYMENT_URL}/internal/chaos/payment/reset`);
}

export function handleSummary(data) {
  return makeSummary('payment-errors', data);
}
