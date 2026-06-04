import { sleep } from 'k6';
import { browseEvents, createOrderHappyPath, createReservation } from './lib/flows.js';
import { makeSummary } from './lib/config.js';

export const options = {
  scenarios: {
    browse_spike: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 400,
      stages: [
        { duration: '30s', target: 30 },
        { duration: '20s', target: 200 },
        { duration: '40s', target: 200 },
        { duration: '30s', target: 20 },
      ],
      exec: 'browse',
    },
    write_spike: {
      executor: 'ramping-arrival-rate',
      startRate: 2,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 250,
      stages: [
        { duration: '30s', target: 5 },
        { duration: '20s', target: 60 },
        { duration: '40s', target: 60 },
        { duration: '30s', target: 5 },
      ],
      exec: 'writeFlow',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.15'],
    http_req_duration: ['p(95)<3000', 'p(99)<5000'],
    checks: ['rate>0.85'],
  },
};

export function browse() {
  browseEvents();
  sleep(0.1);
}

export function writeFlow() {
  if (__ITER % 3 === 0) {
    createOrderHappyPath();
  } else {
    createReservation(1);
  }
  sleep(0.1);
}

export default function () {
  browse();
}

export function handleSummary(data) {
  return makeSummary('traffic-spike', data);
}
