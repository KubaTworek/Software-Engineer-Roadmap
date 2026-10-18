param(
    [Parameter(Mandatory = $true)][string]$DatabaseDump,
    [Parameter(Mandatory = $true)][string]$ObjectSnapshotDirectory,
    [string]$DatabaseName = "filestorage_restore_drill",
    [string]$ComposeProject = "filestorage-restore-drill"
)

$ErrorActionPreference = "Stop"

if (-not $DatabaseName.EndsWith("_restore_drill")) {
    throw "Safety guard: DatabaseName must end with _restore_drill"
}

$dump = (Resolve-Path -LiteralPath $DatabaseDump).Path
$objects = (Resolve-Path -LiteralPath $ObjectSnapshotDirectory).Path

function Invoke-Checked([scriptblock]$Command, [string]$Description) {
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Description failed with exit code $LASTEXITCODE" }
}

Invoke-Checked { docker compose -p $ComposeProject up -d --build --wait postgres minio } "Starting isolated dependencies"
$postgresContainer = docker compose -p $ComposeProject ps -q postgres
if (-not $postgresContainer) { throw "PostgreSQL restore container was not created" }

$exists = docker compose -p $ComposeProject exec -T postgres psql -U filestorage -d postgres -tAc `
    "SELECT 1 FROM pg_database WHERE datname='$DatabaseName'"
if ($exists.Trim() -eq "1") {
    throw "Safety guard: restore database already exists; use a fresh ComposeProject"
}

Invoke-Checked { docker compose -p $ComposeProject exec -T postgres createdb -U filestorage $DatabaseName } "Creating restore database"
Invoke-Checked { docker cp $dump "${postgresContainer}:/tmp/restore.dump" } "Copying database dump"
Invoke-Checked { docker compose -p $ComposeProject exec -T postgres pg_restore -U filestorage -d $DatabaseName --exit-on-error /tmp/restore.dump } "Restoring metadata"

$network = "${ComposeProject}_default"
$clientImage = "roadmap-minio-mc:b00526b153a3"
Invoke-Checked {
    docker build -t $clientImage -f "$PSScriptRoot/../docker/mc.Dockerfile" "$PSScriptRoot/../docker"
} "Building pinned MinIO client"
Invoke-Checked {
    docker run --rm --network $network -v "${objects}:/snapshot:ro" --entrypoint /bin/sh $clientImage `
        -c "mc alias set target http://minio:9000 minioadmin minioadmin && mc mb --ignore-existing target/file-storage && mc mirror /snapshot target/file-storage"
} "Restoring object snapshot"

Invoke-Checked {
    docker compose -p $ComposeProject exec -T postgres psql -U filestorage -d $DatabaseName -c `
        "SELECT COUNT(*) AS files, COALESCE(SUM(size_bytes), 0) AS logical_bytes FROM file_metadata;"
} "Validating restored metadata"

Invoke-Checked {
    docker run --rm --network $network --entrypoint /bin/sh $clientImage `
        -c "mc alias set target http://minio:9000 minioadmin minioadmin && mc du target/file-storage"
} "Validating restored object inventory"

Write-Host "Restore drill prepared successfully. Run application reconciliation before allowing traffic."
