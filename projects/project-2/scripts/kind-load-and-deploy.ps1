$ErrorActionPreference = "Stop"
$ClusterName = if ($env:CLUSTER_NAME) { $env:CLUSTER_NAME } else { "marketplace" }
$ImageName = "marketplace-modular-monolith:local"

$clusters = kind get clusters
if (-not ($clusters -contains $ClusterName)) {
  kind create cluster --name $ClusterName
}

docker build -t $ImageName .
kind load docker-image $ImageName --name $ClusterName
kubectl apply -k k8s
kubectl -n marketplace rollout status deployment/postgres --timeout=180s
kubectl -n marketplace rollout status deployment/kafka --timeout=240s
kubectl -n marketplace rollout status deployment/marketplace-app --timeout=240s
