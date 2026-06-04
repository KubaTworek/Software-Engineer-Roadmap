import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 2,
  iterations: 10,
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '11111111-1111-1111-1111-111111111111';

export default function () {
  const userId = `k6-${__VU}-${__ITER}`;
  const reservation = http.post(`${BASE_URL}/reservations`, JSON.stringify({
    eventId: EVENT_ID,
    userId,
    quantity: 1,
  }), { headers: { 'Content-Type': 'application/json' } });

  check(reservation, { 'reservation created': (r) => r.status === 200 });
  if (reservation.status !== 200) return;

  const reservationId = reservation.json('id');
  const order = http.post(`${BASE_URL}/orders`, JSON.stringify({
    reservationId,
    userId,
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `k6-${reservationId}`,
    },
  });

  check(order, {
    'order accepted': (r) => r.status === 200,
    'paid or pending': (r) => ['PAID', 'PAYMENT_PENDING'].includes(r.json('status')),
  });
}
