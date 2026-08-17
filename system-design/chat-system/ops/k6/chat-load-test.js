import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    steady_chat_load: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<800'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

function registerOrLogin(username) {
  const password = 'Password123!';
  const registerPayload = JSON.stringify({
    username,
    displayName: username,
    email: `${username}@example.com`,
    password,
  });

  http.post(`${BASE_URL}/api/auth/register`, registerPayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  const loginRes = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ username, password }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(loginRes, { 'login ok': (r) => r.status === 200 });
  return { token: loginRes.json('accessToken'), userId: loginRes.json('user.id') };
}

export default function () {
  const userA = `load_a_${__VU}`;
  const userB = `load_b_${__VU}`;
  const accountA = registerOrLogin(userA);
  const accountB = registerOrLogin(userB);
  const tokenA = accountA.token;
  const tokenB = accountB.token;
  const recipientId = accountB.userId;

  const convRes = http.post(`${BASE_URL}/api/conversations/direct`, JSON.stringify({ participantId: recipientId }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenA}` },
  });
  check(convRes, { 'conversation created': (r) => r.status === 200 || r.status === 201 });
  const conversationId = convRes.json('id');

  for (let i = 0; i < 5; i++) {
    const msgRes = http.post(`${BASE_URL}/api/conversations/${conversationId}/messages`, JSON.stringify({
      clientMessageId: crypto.randomUUID(),
      body: `load-test-message-${__VU}-${i}`,
      attachmentIds: [],
    }), {
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenA}` },
    });
    check(msgRes, { 'message sent': (r) => r.status === 200 || r.status === 201 });
  }

  http.get(`${BASE_URL}/api/conversations/${conversationId}/messages?limit=20`, {
    headers: { Authorization: `Bearer ${tokenB}` },
  });

  sleep(1);
}
