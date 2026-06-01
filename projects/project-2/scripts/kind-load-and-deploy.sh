#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-marketplace}"
IMAGE_NAME="marketplace-modular-monolith:local"

if ! kind get clusters | grep -q "^${CLUSTER_NAME}$"; then
  kind create cluster --name "${CLUSTER_NAME}"
fi

docker build -t "${IMAGE_NAME}" .
kind load docker-image "${IMAGE_NAME}" --name "${CLUSTER_NAME}"
kubectl apply -k k8s
kubectl -n marketplace rollout status deployment/postgres --timeout=180s
kubectl -n marketplace rollout status deployment/kafka --timeout=240s
kubectl -n marketplace rollout status deployment/marketplace-app --timeout=240s
