import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const accepted = new Counter('reservations_accepted');
const soldOut = new Counter('reservations_sold_out');
const unexpected = new Rate('reservation_unexpected_response');
const reservationLatency = new Trend('reservation_latency', true);

const profiles = {
  smoke: {
    executor: 'constant-arrival-rate',
    rate: 2,
    timeUnit: '1s',
    duration: __ENV.DURATION || '5s',
    preAllocatedVUs: 2,
    maxVUs: 10,
  },
  load: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || 20),
    timeUnit: '1s',
    duration: __ENV.DURATION || '30s',
    preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 20),
    maxVUs: Number(__ENV.MAX_VUS || 100),
  },
  stress: {
    executor: 'ramping-arrival-rate',
    startRate: 10,
    timeUnit: '1s',
    preAllocatedVUs: 30,
    maxVUs: 200,
    stages: [
      { target: 25, duration: '20s' },
      { target: 100, duration: '30s' },
      { target: 150, duration: '20s' },
    ],
  },
  spike: {
    executor: 'ramping-arrival-rate',
    startRate: 5,
    timeUnit: '1s',
    preAllocatedVUs: 50,
    maxVUs: 300,
    stages: [
      { target: 5, duration: '10s' },
      { target: 250, duration: '2s' },
      { target: 250, duration: '10s' },
      { target: 5, duration: '10s' },
    ],
  },
  soak: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || 20),
    timeUnit: '1s',
    duration: __ENV.DURATION || '30m',
    preAllocatedVUs: 30,
    maxVUs: 100,
  },
  closed: {
    executor: 'constant-vus',
    vus: Number(__ENV.VUS || 20),
    duration: __ENV.DURATION || '30s',
  },
};

const selectedProfile = __ENV.TEST_TYPE || 'load';
if (!profiles[selectedProfile]) {
  throw new Error(`Unknown TEST_TYPE=${selectedProfile}`);
}

export const options = {
  discardResponseBodies: false,
  scenarios: { reservations: profiles[selectedProfile] },
  thresholds: {
    reservation_unexpected_response: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '5s' }],
    reservation_latency: ['p(95)<750', 'p(99)<1500'],
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  let accessToken = __ENV.ACCESS_TOKEN;
  if (!accessToken) {
    const login = http.post(`${baseUrl}/api/auth/login`, JSON.stringify({
      email: __ENV.MANAGER_EMAIL || 'load-manager@example.test',
      password: __ENV.MANAGER_PASSWORD || 'load-test-password',
    }), { headers: { 'Content-Type': 'application/json' } });
    if (login.status !== 200) {
      fail(`Precondition failed: manager login returned ${login.status}: ${login.body}`);
    }
    accessToken = login.json('accessToken');
  }

  let eventId = __ENV.EVENT_ID;
  if (!eventId) {
    const created = http.post(`${baseUrl}/api/events`, JSON.stringify({
      name: `k6 ${selectedProfile} ${Date.now()}`,
      city: 'Warsaw',
      category: 'performance',
      startsAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      totalCapacity: Number(__ENV.EVENT_CAPACITY || 100000),
    }), {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
    });
    if (created.status !== 201) {
      fail(`Precondition failed: event creation returned ${created.status}: ${created.body}`);
    }
    eventId = created.json('id');
  }

  const event = http.get(`${baseUrl}/api/events/${eventId}`);
  if (event.status !== 200) {
    fail(`Precondition failed: event lookup returned ${event.status}`);
  }
  const body = event.json();
  if (body.availableCapacity < 1) {
    fail('Precondition failed: create an event with enough available capacity');
  }
  return { baseUrl, eventId, accessToken, totalCapacity: body.totalCapacity };
}

export default function (data) {
  // Unikalny email usuwa konflikt customer identity z eksperymentu. Mierzymy
  // gorący wiersz capacity, a nie przypadkowo contention indeksu customers.email.
  const email = `load-${__VU}-${__ITER}-${Date.now()}@example.test`;
  const response = http.post(
    `${data.baseUrl}/api/events/${data.eventId}/reservations`,
    JSON.stringify({ customerFullName: 'Load Test Customer', customerEmail: email }),
    {
      headers: {
        Authorization: `Bearer ${data.accessToken}`,
        'Content-Type': 'application/json',
      },
      responseCallback: http.expectedStatuses(201, 409),
      tags: { operation: 'create-reservation' },
    },
  );

  reservationLatency.add(response.timings.duration);
  const isUnexpected = response.status !== 201 && response.status !== 409;
  unexpected.add(isUnexpected);
  if (response.status === 201) {
    accepted.add(1);
  } else if (response.status === 409) {
    soldOut.add(1);
  }
  check(response, {
    'reservation accepted or capacity exhausted': (r) => r.status === 201 || r.status === 409,
  });

  // Closed model czeka po odpowiedzi, więc tempo zależy od latency. Profile
  // arrival-rate są open model i nie ukrywają spowolnienia przez coordinated omission.
  if (selectedProfile === 'closed') {
    sleep(0.1);
  }
}

export function teardown(data) {
  const response = http.get(`${data.baseUrl}/api/events/${data.eventId}`);
  check(response, {
    'capacity invariant remains visible through API': (r) => {
      if (r.status !== 200) return false;
      const event = r.json();
      return event.availableCapacity >= 0 && event.availableCapacity <= data.totalCapacity;
    },
  });
}

function required(name) {
  const value = __ENV[name];
  if (!value) fail(`Missing required environment variable ${name}`);
  return value;
}
