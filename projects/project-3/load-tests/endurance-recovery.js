import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, EVENT_ID, jsonHeaders, safeJson } from './lib/config.js';

const attempts = new Counter('checkout_attempts');
const good = new Counter('checkout_good');
const latency = new Trend('checkout_latency', true);
const run = __ENV.RUN_ID || 'manual-endurance';
const duration = __ENV.DURATION || '30m';
const rate = Number(__ENV.RATE || 2);
export const options = {
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  scenarios: { checkouts: { executor: 'constant-arrival-rate', rate, timeUnit: '1s', duration,
    preAllocatedVUs: Math.max(20, rate * 5), maxVUs: Math.max(60, rate * 20) } },
  thresholds: { checks: ['rate>0.99'], dropped_iterations: ['count==0'], checkout_latency: ['p(99)<5000'] },
};

export default function () {
  const started = Date.now();
  const user = `${run}-${__VU}-${__ITER}`;
  const reservation = http.post(`${BASE_URL}/reservations`, JSON.stringify({eventId: EVENT_ID, userId: user, quantity: 1}),
    {headers: jsonHeaders(), timeout: '5s'});
  attempts.add(1);
  const reservationId = safeJson(reservation, 'id');
  if (!reservationId) { good.add(0); latency.add(Date.now()-started); check(reservation, {'reservation created': r => r.status===200 || r.status===201}); return; }
  const key = `${run}-${reservationId}`;
  const body = JSON.stringify({reservationId, userId:user});
  const response = http.post(`${BASE_URL}/orders`, body, {headers:jsonHeaders({'Idempotency-Key':key}), timeout:'6s'});
  const elapsed = Date.now()-started;
  latency.add(elapsed);
  const paid = safeJson(response,'status') === 'PAID';
  good.add(paid && elapsed <= 3000 ? 1 : 0);
  check(response, {'checkout paid': () => paid});
  // Fizyczny retry podbija request count, lecz nie liczbę biznesowych zamówień.
  if (__ITER % 10 === 0) {
    const replay = http.post(`${BASE_URL}/orders`, body, {headers:jsonHeaders({'Idempotency-Key':key}), timeout:'6s'});
    check(replay, {'retry returns same order': r => safeJson(r,'id') === safeJson(response,'id')});
  }
}
export function handleSummary(data) {
  return { stdout: JSON.stringify({ attempts:data.metrics.checkout_attempts?.values.count,
      good:data.metrics.checkout_good?.values.count, p99:data.metrics.checkout_latency?.values['p(99)'],
      dropped:data.metrics.dropped_iterations?.values.count || 0 })+'\n',
    [`target/endurance/${run}.json`]: JSON.stringify(data,null,2) };
}
