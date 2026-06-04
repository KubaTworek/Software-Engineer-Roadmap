import { sleep } from 'k6';
import { browseEvents, getAvailability } from './lib/flows.js';
import { makeSummary } from './lib/config.js';

export const options = {
  scenarios: {
    browse_events: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  browseEvents();
  getAvailability();
  sleep(1);
}

export function handleSummary(data) {
  return makeSummary('browse-events', data);
}
