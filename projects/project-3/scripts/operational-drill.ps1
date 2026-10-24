param([int]$RecoveryTimeoutSeconds = 60, [int]$OutageSeconds = 3)
$ErrorActionPreference = 'Stop'
if ($env:COMPOSE_PROJECT_NAME -notmatch '^roadmap-[a-z0-9-]+$') { throw 'Use a private COMPOSE_PROJECT_NAME=roadmap-...' }
if ($OutageSeconds -lt 1 -or $OutageSeconds -gt 30) { throw 'OutageSeconds must be 1..30' }

function Wait-Healthy([string]$Url, [string]$Name) {
    $deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    do {
        try { if ((Invoke-WebRequest -UseBasicParsing $Url -TimeoutSec 2).StatusCode -eq 200) { return } } catch { }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "$Name did not recover within $RecoveryTimeoutSeconds seconds"
}

$timeline = [System.Collections.Generic.List[string]]::new()
$user = "drill-$([guid]::NewGuid().ToString('N'))"
$event = '11111111-1111-1111-1111-111111111111'
function Reserve {
    Invoke-RestMethod 'http://localhost:8082/reservations' -Method Post -ContentType application/json `
        -Body (@{eventId=$event;userId=$user;quantity=1}|ConvertTo-Json) -TimeoutSec 10
}
function Place($Reservation) {
    Invoke-RestMethod 'http://localhost:8083/orders' -Method Post -ContentType application/json `
        -Headers @{'Idempotency-Key'="drill-$($Reservation.id)"} `
        -Body (@{reservationId=$Reservation.id;userId=$user}|ConvertTo-Json) -TimeoutSec 15
}
function Wait-Delivery($Order) {
    $deadline=(Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    do {
        $id=(docker compose exec -T order-db psql -U app -d orders -Atc "select id from outbox_messages where aggregate_id='$($Order.id)' and published_at is not null and dead_at is null limit 1").Trim()
        if ($LASTEXITCODE -eq 0 -and $id) {
            $count=(docker compose exec -T notification-db psql -U app -d notifications -Atc "select count(*) from processed_messages where message_id='$id'").Trim()
            if ($LASTEXITCODE -eq 0 -and $count -eq '1') { return }
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw 'Outbox/inbox did not converge after dependency recovery'
}
foreach ($target in @(
    @{ Container = 'redis'; Probe = 'http://localhost:8081/actuator/health'; Name = 'catalog-after-redis' },
    @{ Container = 'rabbitmq'; Probe = 'http://localhost:8083/actuator/health'; Name = 'order-after-rabbitmq' },
    @{ Container = 'order-db'; Probe = 'http://localhost:8083/actuator/health'; Name = 'order-after-postgresql' }
)) {
    $started = Get-Date
    $reservation = Reserve
    $order = $null
    docker compose stop $target.Container | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot stop dependency' }
    try {
        $until=(Get-Date).AddSeconds($OutageSeconds)
        do {
            if ($target.Container -eq 'redis') {
                # Rzeczywisty ruch przez endpoint, gdy cache pozostaje wyłączony.
                $events=Invoke-RestMethod 'http://localhost:8081/events' -TimeoutSec 5
                if (@($events).Count -eq 0) { throw 'Redis outage lost catalog fallback' }
            } elseif ($target.Container -eq 'rabbitmq') {
                $order=Place $reservation
                if ($order.status -ne 'PAID') { throw 'Broker outage broke local order commit' }
            } else {
                try { $order=Place $reservation } catch {
                    # Utrata odpowiedzi jest niejednoznaczna; rozstrzygamy ją retry tego
                    # samego klucza po recovery, nie liczbą 2xx w okresie awarii.
                    $timeline.Add('order-db: request failed or timed out; stable-key recovery required')
                }
            }
            Start-Sleep -Milliseconds 200
        } while ((Get-Date) -lt $until)
    } finally { docker compose start $target.Container | Out-Null }
    Wait-Healthy $target.Probe $target.Name
    $order=Place $reservation
    $replay=Place $reservation
    if ($order.status -ne 'PAID' -or $replay.id -ne $order.id) { throw 'Recovery lost order progress/idempotency' }
    $confirmed=Invoke-RestMethod "http://localhost:8082/reservations/$($reservation.id)" -TimeoutSec 5
    $payment=Invoke-RestMethod "http://localhost:8084/payments/orders/$($order.id)" -TimeoutSec 5
    if ($confirmed.status -ne 'CONFIRMED' -or $payment.status -ne 'PAID') { throw 'Recovery left inconsistent external effects' }
    Wait-Delivery $order
    $seconds = ((Get-Date) - $started).TotalSeconds
    $timeline.Add("$($target.Container): recovered in $([math]::Round($seconds, 2)) s")
}
$timeline | ForEach-Object { Write-Host $_ }
