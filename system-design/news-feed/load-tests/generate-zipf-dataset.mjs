import { writeFileSync } from 'node:fs';

const users = Number(process.argv[2] || 10000);
const maxFollowers = Number(process.argv[3] || 1000000);
const exponent = Number(process.argv[4] || 1.15);

// Syntetyczny manifest opisuje rozkład, który importer środowiska testowego może
// zamienić na użytkowników i relacje. Nie zawiera prawdziwych tokenów ani PII.
const dataset = Array.from({ length: users }, (_, rank) => ({
  externalId: `load-user-${rank + 1}`,
  rank: rank + 1,
  followerCount: Math.max(1, Math.floor(maxFollowers / Math.pow(rank + 1, exponent))),
  postsPerDay: Math.max(1, Math.round(20 / Math.pow(rank + 1, 0.35))),
}));

writeFileSync('zipf-dataset.json', JSON.stringify({ users, maxFollowers, exponent, dataset }, null, 2));
console.log(`Generated zipf-dataset.json with ${users} synthetic users.`);
