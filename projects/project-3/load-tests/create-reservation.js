import { sleep } from 'k6';
import { createReservation } from './lib/flows.js';
import { makeSummary } from './lib/config.js';

export const options = {
  scenarios: {
    create_reservation: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 30,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    checks: ['rate>0.95'],
  },
};

export default function () {
  createReservation(1);
  sleep(0.2);
}

export function handleSummary(data) {
  return makeSummary('create-reservation', data);
}
