import http from 'k6/http';
import { check } from 'k6';
import {
  BASE_URL,
  EVENT_ID,
  jsonHeaders,
  uuidLike,
  isSuccessStatus,
  safeJson,
} from './config.js';

export function browseEvents() {
  const res = http.get(`${BASE_URL}/events`, {
    headers: jsonHeaders(),
  });

  check(res, {
    'browse events status is 200': (r) => r.status === 200,
  });

  return res;
}

export function getAvailability() {
  const res = http.get(`${BASE_URL}/events/${EVENT_ID}/availability`, {
    headers: jsonHeaders(),
  });

  check(res, {
    'availability status is 200': (r) => r.status === 200,
  });

  return res;
}

export function createReservation(quantity = 1) {
  const userId = uuidLike('user');

  const payload = JSON.stringify({
    eventId: EVENT_ID,
    userId,
    quantity,
  });

  const res = http.post(`${BASE_URL}/reservations`, payload, {
    headers: jsonHeaders(),
  });

  check(res, {
    'reservation status is 200 or 201': (r) => isSuccessStatus(r),
    'reservation has id': (r) => isSuccessStatus(r) && Boolean(safeJson(r, 'id')),
    'reservation has expected userId': (r) => !isSuccessStatus(r) || safeJson(r, 'userId') === userId,
  });

  return {
    response: res,
    userId,
    reservationId: safeJson(res, 'id'),
  };
}

export function createOrderFromReservation(reservationId, userId) {
  const payload = JSON.stringify({
    reservationId,
    userId,
  });

  const res = http.post(`${BASE_URL}/orders`, payload, {
    headers: jsonHeaders({
      'Idempotency-Key': uuidLike('idem'),
    }),
  });

  check(res, {
    'order status is 200 or 201': (r) => isSuccessStatus(r),
    'order has id': (r) => isSuccessStatus(r) && Boolean(safeJson(r, 'id')),
    'order has accepted state': (r) => {
      if (!isSuccessStatus(r)) {
        return false;
      }

      const status = safeJson(r, 'status');
      return ['PAID', 'PAYMENT_PENDING', 'FAILED'].includes(status);
    },
  });

  return res;
}

export function createOrderHappyPath() {
  const reservation = createReservation(1);

  if (!isSuccessStatus(reservation.response) || !reservation.reservationId) {
    return {
      reservation: reservation.response,
      order: null,
    };
  }

  const order = createOrderFromReservation(
    reservation.reservationId,
    reservation.userId,
  );

  return {
    reservation: reservation.response,
    order,
  };
}

export function configurePaymentChaos(mode, options = {}) {
  const payload = JSON.stringify({
    mode,
    delayMs: options.delayMs ?? 0,
    errorRate: options.errorRate ?? 0,
  });

  const res = http.post(`${BASE_URL}/internal/chaos/payment`, payload, {
    headers: jsonHeaders(),
  });

  check(res, {
    'payment chaos configured': (r) => r.status === 200 || r.status === 204,
  });

  return res;
}

export function resetPaymentChaos() {
  const res = http.post(`${BASE_URL}/internal/chaos/payment/reset`, null, {
    headers: jsonHeaders(),
  });

  check(res, {
    'payment chaos reset': (r) => r.status === 200 || r.status === 204,
  });

  return res;
}

export function configureCatalogDbDelay(delayMs) {
  const payload = JSON.stringify({
    delayMs,
  });

  const res = http.post(`${BASE_URL}/internal/chaos/catalog/db-delay`, payload, {
    headers: jsonHeaders(),
  });

  check(res, {
    'catalog db delay configured': (r) => r.status === 200 || r.status === 204,
  });

  return res;
}

export function resetCatalogDbDelay() {
  return configureCatalogDbDelay(0);
}

export function configureNotificationProcessingDelay(delayMs) {
  const payload = JSON.stringify({
    delayMs,
  });

  const res = http.post(`${BASE_URL}/internal/chaos/notification/processing-delay`, payload, {
    headers: jsonHeaders(),
  });

  check(res, {
    'notification delay configured': (r) => r.status === 200 || r.status === 204,
  });

  return res;
}

export function resetNotificationProcessingDelay() {
  return configureNotificationProcessingDelay(0);
}