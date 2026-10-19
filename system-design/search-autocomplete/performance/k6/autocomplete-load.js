import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    open_arrival_rate: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<50', 'p(99)<100'],
  },
};

const prefixes = ['iph', 'java', 'prod', 'dock', 'ai'];

export default function () {
  const prefix = prefixes[Math.floor(Math.random() * prefixes.length)];
  const response = http.get(`http://localhost:8080/api/v1/autocomplete?q=${prefix}&sessionId=k6-${__VU}`);
  check(response, { 'autocomplete returns 200': (result) => result.status === 200 });
}
