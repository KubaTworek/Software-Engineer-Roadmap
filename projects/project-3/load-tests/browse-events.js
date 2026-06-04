import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 50,
  duration: '60s',
};

export default function () {
  const res = http.get('http://localhost:8080/events');
  check(res, { 'events listed': r => r.status === 200 });
  sleep(1);
}
