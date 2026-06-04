import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomUUID } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '11111111-1111-1111-1111-111111111111';

export default function () {
  const correlationId = randomUUID();
  const headers = {
    'Content-Type': 'application/json',
    'X-Correlation-Id': correlationId,
  };

  const reservationResponse = http.post(`${BASE_URL}/reservations`, JSON.stringify({
    eventId: EVENT_ID,
    userId: `user-${__VU}`,
    quantity: 1,
  }), { headers });

  check(reservationResponse, {
    'reservation created': (r) => r.status === 200,
  });

  if (reservationResponse.status !== 200) {
    sleep(1);
    return;
  }

  const reservation = reservationResponse.json();
  const orderResponse = http.post(`${BASE_URL}/orders`, JSON.stringify({
    reservationId: reservation.id,
    userId: reservation.userId,
  }), {
    headers: {
      ...headers,
      'Idempotency-Key': randomUUID(),
    },
  });

  check(orderResponse, {
    'order accepted': (r) => r.status === 200,
    'order paid or pending': (r) => ['PAID', 'PAYMENT_PENDING'].includes(r.json('status')),
  });

  sleep(1);
}
