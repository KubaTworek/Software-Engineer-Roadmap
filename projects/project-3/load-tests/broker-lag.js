import http from 'k6/http';
import encoding from 'k6/encoding';
import { check, sleep } from 'k6';
import { createOrderHappyPath } from './lib/flows.js';
import { NOTIFICATION_URL, RABBITMQ_API_URL, RABBITMQ_AUTH, makeSummary } from './lib/config.js';

export const options = {
  scenarios: {
    broker_lag: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.12'],
    http_req_duration: ['p(95)<2500'],
    checks: ['rate>0.88'],
  },
};

export function setup() {
  http.post(`${NOTIFICATION_URL}/internal/chaos/notification/processing-delay`, JSON.stringify({ delayMs: Number(__ENV.NOTIFICATION_DELAY_MS || 2000) }), {
    headers: { 'Content-Type': 'application/json' },
  });
}

export default function () {
  const result = createOrderHappyPath();
  if (result.order) {
    check(result.order, {
      'order endpoint not blocked by notification lag': (r) => r.status === 200,
    });
  }

  if (__ITER % 10 === 0) {
    const queue = http.get(`${RABBITMQ_API_URL}/queues/%2F/notifications.order-paid`, {
      headers: { Authorization: `Basic ${encoding.b64encode(RABBITMQ_AUTH)}` },
    });
    check(queue, {
      'rabbitmq queue API reachable': (r) => r.status === 200,
    });
  }
  sleep(0.1);
}

export function teardown() {
  http.post(`${NOTIFICATION_URL}/internal/chaos/notification/reset`);
}

export function handleSummary(data) {
  return makeSummary('broker-lag', data);
}
