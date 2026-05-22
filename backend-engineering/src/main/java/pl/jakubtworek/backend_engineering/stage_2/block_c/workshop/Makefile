IMAGE_NAME ?= demo-api-workshop
IMAGE_TAG ?= dev
CLUSTER_NAME ?= kind

.PHONY: test
test:
	# Run the application test suite before building the image.
	mvn test

.PHONY: build
build:
	# Build a local Docker image for kind or minikube workflow.
	docker build -t $(IMAGE_NAME):$(IMAGE_TAG) .

.PHONY: kind-create
kind-create:
	# Create a local kind cluster for fast Kubernetes development.
	kind create cluster --name $(CLUSTER_NAME) --wait 60s

.PHONY: kind-load
kind-load:
	# Load the locally built image into the kind cluster.
	kind load docker-image $(IMAGE_NAME):$(IMAGE_TAG) --name $(CLUSTER_NAME)

.PHONY: deploy
deploy:
	# Apply Kubernetes manifests to the current kubectl context.
	kubectl apply -f k8s/

.PHONY: rollout
rollout:
	# Wait until the Deployment rollout completes or fails.
	kubectl rollout status deployment/demo-api --timeout=120s

.PHONY: port-forward
port-forward:
	# Forward the Service to localhost for manual testing.
	kubectl port-forward svc/demo-api 8080:80

.PHONY: debug-status
debug-status:
	# Start with object status before entering containers.
	kubectl get pods,svc,endpointslices -o wide

.PHONY: debug-pods
debug-pods:
	# Describe Pods to inspect scheduling, events, probes, image pulls and restarts.
	kubectl describe pods -l app=demo-api

.PHONY: debug-logs
debug-logs:
	# Show current logs for the application container.
	kubectl logs -l app=demo-api -c app --tail=200

.PHONY: debug-previous-logs
debug-previous-logs:
	# Show previous container logs, useful for CrashLoopBackOff.
	kubectl logs -l app=demo-api -c app -p --tail=200

.PHONY: delete
delete:
	# Delete local Kubernetes resources.
	kubectl delete -f k8s/ --ignore-not-found=true