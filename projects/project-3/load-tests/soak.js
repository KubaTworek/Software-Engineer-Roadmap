import { sleep } from 'k6';
import { browseEvents, createOrderHappyPath } from './lib/flows.js';
import { makeSummary } from './lib/config.js';

const duration = __ENV.SOAK_DURATION || '15m';
const rate = Number(__ENV.SOAK_RPS || 10);

/**
 * Open model utrzymuje tempo napływu niezależnie od latency systemu. Closed model z VU
 * zwalnia razem z aplikacją i może ukryć coordinated omission właśnie podczas degradacji.
 */
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    sustained_arrivals: {
      executor: 'constant-arrival-rate', rate, timeUnit: '1s', duration,
      preAllocatedVUs: Math.max(20, rate * 2), maxVUs: Math.max(100, rate * 10),
    },
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
    http_req_duration: [{ threshold: 'p(99)<3000', abortOnFail: true, delayAbortEval: '30s' }],
    checks: ['rate>0.95'],
    dropped_iterations: ['count<10'],
  },
};

export default function () {
  if (__ITER % 10 === 0) createOrderHappyPath();
  else browseEvents();
  sleep(0.05);
}

export function handleSummary(data) { return makeSummary('soak', data); }
