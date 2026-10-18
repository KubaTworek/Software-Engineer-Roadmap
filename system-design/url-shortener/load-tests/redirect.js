import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const shortCode = __ENV.SHORT_CODE || 'load-test';

export const options = {
  scenarios: {
    redirect_open_model: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 100),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 20,
      maxVUs: 500
    }
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '15s' }],
    http_req_duration: ['p(95)<100', 'p(99)<250'],
    dropped_iterations: [{ threshold: 'count==0', abortOnFail: true }]
  }
};

export default function () {
  const response = http.get(`${baseUrl}/${shortCode}`, { redirects: 0 });
  check(response, { 'redirect is 302': result => result.status === 302 });
}
