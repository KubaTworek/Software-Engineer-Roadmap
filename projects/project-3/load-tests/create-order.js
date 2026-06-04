import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '30s',
};

export default function () {
  const reservationPayload = JSON.stringify({
    eventId: '11111111-1111-1111-1111-111111111111',
    userId: `user-${__VU}`,
    quantity: 1,
  });

  const reservation = http.post('http://localhost:8080/reservations', reservationPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(reservation, { 'reservation created': r => r.status === 200 });
  const reservationId = reservation.json('id');

  const orderPayload = JSON.stringify({ reservationId, userId: `user-${__VU}` });
  const order = http.post('http://localhost:8080/orders', orderPayload, {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `${__VU}-${__ITER}` },
  });

  check(order, { 'order accepted': r => r.status === 200 });
  sleep(1);
}
