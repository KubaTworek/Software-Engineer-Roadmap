$ErrorActionPreference = "Stop"
$Namespace = if ($env:NAMESPACE) { $env:NAMESPACE } else { "marketplace" }

Write-Host "# Pods"
kubectl -n $Namespace get pods -o wide

Write-Host "# Services"
kubectl -n $Namespace get services

Write-Host "# Recent events"
kubectl -n $Namespace get events --sort-by=.lastTimestamp

Write-Host "# App describe"
kubectl -n $Namespace describe deployment marketplace-app

Write-Host "# App logs"
kubectl -n $Namespace logs deployment/marketplace-app --tail=120
