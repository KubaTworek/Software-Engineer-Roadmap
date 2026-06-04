export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const CATALOG_URL = __ENV.CATALOG_URL || 'http://localhost:8081';
export const PAYMENT_URL = __ENV.PAYMENT_URL || 'http://localhost:8084';
export const NOTIFICATION_URL = __ENV.NOTIFICATION_URL || 'http://localhost:8085';
export const RABBITMQ_API_URL = __ENV.RABBITMQ_API_URL || 'http://localhost:15672/api';
export const RABBITMQ_AUTH = __ENV.RABBITMQ_AUTH || 'guest:guest';
export const EVENT_ID = __ENV.EVENT_ID || '11111111-1111-1111-1111-111111111111';

export function uuidLike(prefix = 'k6') {
  return `${prefix}-${Date.now()}-${__VU}-${__ITER}-${Math.random().toString(16).slice(2)}`;
}

export function jsonHeaders(extra = {}) {
  const correlationId = uuidLike('corr');
  return {
    'Content-Type': 'application/json',
    'X-Correlation-Id': correlationId,
    'X-Request-Id': uuidLike('req'),
    ...extra,
  };
}

export function makeReservationPayload(quantity = 1) {
  return JSON.stringify({
    eventId: EVENT_ID,
    userId: uuidLike('user'),
    quantity,
  });
}

export function makeSummary(name, data) {
  const metrics = data.metrics || {};
  const lines = [
    `# k6 summary: ${name}`,
    '',
    `date: ${new Date().toISOString()}`,
    '',
    '## Key metrics',
    '',
    `- http_req_duration p95: ${metrics.http_req_duration?.percentiles?.['p(95)'] ?? 'n/a'} ms`,
    `- http_req_duration p99: ${metrics.http_req_duration?.percentiles?.['p(99)'] ?? 'n/a'} ms`,
    `- http_req_failed rate: ${metrics.http_req_failed?.rate ?? 'n/a'}`,
    `- checks rate: ${metrics.checks?.rate ?? 'n/a'}`,
    '',
    'Raw JSON summary is stored next to this file when the script is run from the repository root.',
    '',
  ];
  return {
    stdout: `${lines.join('\n')}\n`,
    [`load-tests/reports/${name}.summary.json`]: JSON.stringify(data, null, 2),
    [`load-tests/reports/${name}.summary.md`]: lines.join('\n'),
  };
}
