param([int]$RecoveryTimeoutSeconds = 60)
$ErrorActionPreference = 'Stop'

function Wait-Healthy([string]$Url, [string]$Name) {
    $deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    do {
        try { if ((Invoke-WebRequest -UseBasicParsing $Url -TimeoutSec 2).StatusCode -eq 200) { return } } catch { }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "$Name did not recover within $RecoveryTimeoutSeconds seconds"
}

$timeline = [System.Collections.Generic.List[string]]::new()
foreach ($target in @(
    @{ Container = 'redis'; Probe = 'http://localhost:8081/actuator/health'; Name = 'catalog-after-redis' },
    @{ Container = 'rabbitmq'; Probe = 'http://localhost:8083/actuator/health'; Name = 'order-after-rabbitmq' },
    @{ Container = 'order-db'; Probe = 'http://localhost:8083/actuator/health'; Name = 'order-after-postgresql' }
)) {
    $started = Get-Date
    docker compose stop $target.Container | Out-Null
    docker compose start $target.Container | Out-Null
    Wait-Healthy $target.Probe $target.Name
    $seconds = ((Get-Date) - $started).TotalSeconds
    $timeline.Add("$($target.Container): recovered in $([math]::Round($seconds, 2)) s")
}
$timeline | ForEach-Object { Write-Host $_ }
