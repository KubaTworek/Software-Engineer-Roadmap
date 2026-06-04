import { sleep } from 'k6';
import { check } from 'k6';
import { createOrderHappyPath } from './lib/flows.js';
import { makeSummary } from './lib/config.js';

export const options = {
  scenarios: {
    create_order: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 10),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 30,
      maxVUs: 120,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.08'],
    http_req_duration: ['p(95)<1800', 'p(99)<3500'],
    checks: ['rate>0.92'],
  },
};

export default function () {
  const result = createOrderHappyPath();
  if (result.order) {
    check(result.order, {
      'order paid or payment pending': (r) => ['PAID', 'PAYMENT_PENDING'].includes(r.json('status')),
    });
  }
  sleep(0.3);
}

export function handleSummary(data) {
  return makeSummary('create-order', data);
}
