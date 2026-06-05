param(
    [string]$Region = "eu-central-1",
    [string]$Environment = "dev",
    [string]$ProjectName = "ticketing-platform",
    [string]$Tag = "latest"
)

$ErrorActionPreference = "Stop"

$services = @(
    "api-gateway",
    "catalog-service",
    "reservation-service",
    "order-service",
    "payment-mock-service",
    "notification-service"
)

$accountId = aws sts get-caller-identity --query Account --output text
$registry = "$accountId.dkr.ecr.$Region.amazonaws.com"
$namePrefix = "$ProjectName-$Environment"

aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $registry

foreach ($service in $services) {
    $repository = "$namePrefix/$service"
    $image = "$registry/$repository`:$Tag"

    Write-Host "Building $service -> $image"
    docker build -f "$service/Dockerfile" -t $image .

    Write-Host "Pushing $image"
    docker push $image
}

Write-Host "Done. Images pushed with tag '$Tag'."
