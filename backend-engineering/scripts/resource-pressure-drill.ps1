param([ValidateSet('memory','cpu','fd')][string]$Mode = 'cpu')
$ErrorActionPreference = 'Stop'
$labRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_g'
$containerName = 'roadmap-pressure-' + [Guid]::NewGuid().ToString('N')
try {
    # Źródło jest read-only, kompilacja trafia do efemerycznego /tmp kontenera.
    $probeOutput = docker run --name $containerName --memory 160m --memory-swap 160m --cpus 0.25 --pids-limit 64 --ulimit nofile=128:128 --mount "type=bind,source=$labRoot,target=/lab,readonly" eclipse-temurin:21-jdk sh -c 'javac -d /tmp /lab/ResourcePressureProbe.java && timeout 25s java -Xmx32m -XX:MaxDirectMemorySize=1g -cp /tmp pl.jakubtworek.backend_engineering.stage_1.block_g.ResourcePressureProbe "$1"' sh $Mode 2>&1
    $probeExit = $LASTEXITCODE
    $probeOutput | ForEach-Object { Write-Output $_ }
    $stateJson = docker inspect --format '{{json .State}}' $containerName
    if ($LASTEXITCODE -ne 0) { throw 'Brak stanu kontenera: eksperyment nie został wykonany' }
    $state = $stateJson | ConvertFrom-Json
    [PSCustomObject]@{ Mode = $Mode; ExitCode = $probeExit; OOMKilled = $state.OOMKilled }
    if ($Mode -eq 'memory' -and -not $state.OOMKilled) { throw 'Brak potwierdzonego OOM kill; nie oznaczaj eksperymentu jako zaliczony' }
    if ($Mode -eq 'cpu' -and $probeExit -ne 0) { throw 'Eksperyment CPU nie ukończył pracy' }
    $text = $probeOutput -join "`n"
    if ($Mode -eq 'cpu' -and $text -notmatch 'nr_throttled\s+[1-9][0-9]*') { throw 'Nie potwierdzono throttlingu' }
    if ($Mode -eq 'fd' -and ($probeExit -eq 0 -or $text -notmatch 'Too many open files')) { throw 'Nie odtworzono wyczerpania deskryptorów' }
} finally {
    docker rm -f $containerName 2>$null | Out-Null
}
