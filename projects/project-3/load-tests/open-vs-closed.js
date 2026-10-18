import { browseEvents } from './lib/flows.js';
import { makeSummary } from './lib/config.js';

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    closed_model: { executor: 'constant-vus', vus: 10, duration: '30s', exec: 'browse', tags: { model: 'closed' } },
    open_model: { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '30s',
      preAllocatedVUs: 20, maxVUs: 100, startTime: '35s', exec: 'browse', tags: { model: 'open' } },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(99)<3000'],
    'http_req_duration{model:closed}': ['p(99)<3000'],
    'http_req_duration{model:open}': ['p(99)<3000'],
    'http_req_failed{model:closed}': ['rate<0.05'],
    'http_req_failed{model:open}': ['rate<0.05'],
    'http_reqs{model:closed}': ['count>0'],
    'http_reqs{model:open}': ['count>0'],
  },
};

export function browse() { browseEvents(); }
export default function () { browseEvents(); }
export function handleSummary(data) { return makeSummary('open-vs-closed', data); }
