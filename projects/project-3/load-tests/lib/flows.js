import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, EVENT_ID, jsonHeaders, uuidLike } from './config.js';

export function browseEvents() {
  const res = http.get(`${BASE_URL}/events`, { headers: jsonHeaders() });
  check(res, {
    'browse events status is 200': (r) => r.status === 200,
  });
  return res;
}

export function getAvailability() {
  const res = http.get(`${BASE_URL}/events/${EVENT_ID}/availability`, { headers: jsonHeaders() });
  check(res, {
    'availability status is 200': (r) => r.status === 200,
  });
  return res;
}

export function createReservation(quantity = 1) {
  const userId = uuidLike('user');
  const res = http.post(`${BASE_URL}/reservations`, JSON.stringify({
    eventId: EVENT_ID,
    userId,
    quantity,
  }), { headers: jsonHeaders() });

  check(res, {
    'reservation status is 200': (r) => r.status === 200,
    'reservation has id': (r) => r.status !== 200 || Boolean(r.json('id')),
  });
  return { response: res, userId };
}

export function createOrderFromReservation(reservationId, userId) {
  const res = http.post(`${BASE_URL}/orders`, JSON.stringify({
    reservationId,
    userId,
  }), {
    headers: jsonHeaders({ 'Idempotency-Key': uuidLike('idem') }),
  });

  check(res, {
    'order status is 200': (r) => r.status === 200,
    'order has accepted state': (r) => r.status !== 200 || ['PAID', 'PAYMENT_PENDING', 'FAILED'].includes(r.json('status')),
  });
  return res;
}

export function createOrderHappyPath() {
  const reservation = createReservation(1);
  if (reservation.response.status !== 200) {
    return { reservation: reservation.response, order: null };
  }
  const order = createOrderFromReservation(reservation.response.json('id'), reservation.userId);
  return { reservation: reservation.response, order };
}
