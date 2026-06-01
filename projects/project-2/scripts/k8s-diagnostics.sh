#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-marketplace}"

echo "# Pods"
kubectl -n "${NAMESPACE}" get pods -o wide

echo "# Services"
kubectl -n "${NAMESPACE}" get services

echo "# Recent events"
kubectl -n "${NAMESPACE}" get events --sort-by=.lastTimestamp | tail -40

echo "# App describe"
kubectl -n "${NAMESPACE}" describe deployment marketplace-app

echo "# App logs"
kubectl -n "${NAMESPACE}" logs deployment/marketplace-app --tail=120 || true
