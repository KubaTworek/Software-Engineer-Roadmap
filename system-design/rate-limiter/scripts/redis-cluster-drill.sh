#!/bin/sh
set -eu

seed=redis-node-1:6379
# Init kończy się po uzgodnieniu slotów, ale DNS i gossip mogą być przez moment
# niespójne dla nowego kontenera drill. Czekamy jawnie zamiast tworzyć flaky test.
attempt=0
until redis-cli -h redis-node-1 cluster info 2>/dev/null | grep -q 'cluster_state:ok'; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo "Redis Cluster did not become ready" >&2
    exit 1
  fi
  sleep 1
done
redis-cli -h redis-node-1 cluster info | grep 'cluster_state:ok'

# `-c` follows MOVED/ASK exactly as an application client must. The read after
# reshard and failover proves that data remains reachable through redirects.
redis-cli -c -h redis-node-1 SET 'rl:{cluster-drill}:region:rule' 'before-failover'

# Przeniesienie jednego slotu dowodzi, że topologia jest rzeczywistym klastrem,
# nie sześcioma niezależnymi instancjami. Dane w migrowanym slocie są kopiowane
# przez redis-cli zgodnie z protokołem IMPORTING/MIGRATING.
nodes="$(redis-cli -h redis-node-1 cluster nodes)"
from="$(printf '%s\n' "$nodes" | awk '$3 ~ /master/ {print $1; exit}')"
to="$(printf '%s\n' "$nodes" | awk '$3 ~ /master/ {seen++; if (seen == 2) {print $1; exit}}')"
redis-cli --cluster reshard "$seed" --cluster-from "$from" --cluster-to "$to" \
  --cluster-slots 1 --cluster-yes

# redis-node-4 jest repliką w topologii utworzonej z trzema pierwszymi masterami.
# Wymuszamy kontrolowany failover, zamiast zabijać proces i pozostawiać wynik
# zależny od timeoutów środowiska CI.
redis-cli -h redis-node-4 cluster failover
sleep 5
redis-cli -h redis-node-1 cluster info | grep 'cluster_state:ok'
test "$(redis-cli -c -h redis-node-1 GET 'rl:{cluster-drill}:region:rule')" = 'before-failover'
redis-cli -h redis-node-1 cluster nodes
