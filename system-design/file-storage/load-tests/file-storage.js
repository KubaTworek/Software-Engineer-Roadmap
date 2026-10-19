import http from 'k6/http';
import { check, sleep } from 'k6';

const profile = __ENV.TEST_TYPE || 'load';
const profiles = {
  load: { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '2m', preAllocatedVUs: 20, maxVUs: 100 },
  stress: { executor: 'ramping-arrival-rate', startRate: 10, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 300,
    stages: [{ target: 50, duration: '2m' }, { target: 150, duration: '3m' }, { target: 0, duration: '1m' }] },
  spike: { executor: 'ramping-arrival-rate', startRate: 5, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 500,
    stages: [{ target: 300, duration: '15s' }, { target: 300, duration: '45s' }, { target: 5, duration: '30s' }] },
  soak: { executor: 'constant-arrival-rate', rate: 15, timeUnit: '1s', duration: '30m', preAllocatedVUs: 20, maxVUs: 100 },
};

export const options = {
  scenarios: { selected: profiles[profile] },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '30s' }],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const token = __ENV.TOKEN;
const fileId = __ENV.FILE_ID;
const downloadPercent = Number(__ENV.DOWNLOAD_PERCENT || 20);

export default function () {
  if (!token || !fileId) throw new Error('TOKEN and FILE_ID are required');
  const response = http.get(`${baseUrl}/api/v1/files/${fileId}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { operation: 'metadata-read' },
  });
  check(response, { 'metadata status is 200': (r) => r.status === 200 });

  // Część ruchu wykonuje mały Range download. Dzięki temu eksperyment
  // mierzy osobno control plane (metadane) i data plane bez pobierania całego,
  // potencjalnie wielogigabajtowego obiektu w każdej iteracji.
  if (Math.random() * 100 < downloadPercent) {
    const rangeResponse = http.get(`${baseUrl}/api/v1/files/${fileId}/download`, {
      headers: { Authorization: `Bearer ${token}`, Range: 'bytes=0-65535' },
      tags: { operation: 'range-download' },
    });
    check(rangeResponse, {
      'range status is 206': (r) => r.status === 206,
      'range has content-range': (r) => Boolean(r.headers['Content-Range']),
    });
  }
  sleep(0.1);
}
