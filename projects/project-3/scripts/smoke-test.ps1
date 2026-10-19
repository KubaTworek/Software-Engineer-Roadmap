param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$EventId = '11111111-1111-1111-1111-111111111111',
    [string]$UserId = 'kubat'
)
$ErrorActionPreference = 'Stop'

function Assert-Equal($Expected, $Actual, [string]$Message) {
    if ($Expected -ne $Actual) { throw "$Message. Expected '$Expected', got '$Actual'." }
}

function Wait-ForSql([string]$Service, [string]$Database, [string]$Sql, [string]$Expected) {
    foreach ($attempt in 1..40) {
        $actual = docker compose exec -T $Service psql -U app -d $Database -Atc $Sql
        if ($LASTEXITCODE -eq 0 -and $actual.Trim() -eq $Expected) { return $actual.Trim() }
        Start-Sleep -Milliseconds 250
    }
    throw "Database assertion did not become true: $Sql"
}

foreach ($service in @(
    @{ Name = 'API Gateway'; Url = "$BaseUrl/actuator/health/readiness" },
    @{ Name = 'Catalog Service'; Url = 'http://localhost:8081/actuator/health/readiness' },
    @{ Name = 'Reservation Service'; Url = 'http://localhost:8082/actuator/health/readiness' },
    @{ Name = 'Order Service'; Url = 'http://localhost:8083/actuator/health/readiness' },
    @{ Name = 'Payment Mock'; Url = 'http://localhost:8084/actuator/health/readiness' },
    @{ Name = 'Notification Service'; Url = 'http://localhost:8085/actuator/health/readiness' }
)) {
    Write-Host "Waiting for $($service.Name) readiness"
    $ready = $false
    foreach ($attempt in 1..120) {
        try {
            $health = Invoke-RestMethod $service.Url -TimeoutSec 2
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw "$($service.Name) did not become ready." }
}

$correlation = "smoke-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
Write-Host '1/8 GET /events'
$events = Invoke-RestMethod "$BaseUrl/events" -Headers @{ 'X-Correlation-Id' = "$correlation-events" }
if (@($events).Count -eq 0) { throw 'Catalog returned no bootstrap event.' }

Write-Host '2/8 POST /reservations'
$reservation = Invoke-RestMethod "$BaseUrl/reservations" -Method Post -ContentType 'application/json' `
    -Headers @{ 'X-Correlation-Id' = "$correlation-reservation" } `
    -Body (@{ eventId = $EventId; userId = $UserId; quantity = 1 } | ConvertTo-Json)

Write-Host '3/8 POST /orders'
$key = "smoke-$($reservation.id)"
$body = @{ reservationId = $reservation.id; userId = $UserId } | ConvertTo-Json
$order = Invoke-RestMethod "$BaseUrl/orders" -Method Post -ContentType 'application/json' `
    -Headers @{ 'X-Correlation-Id' = "$correlation-order"; 'Idempotency-Key' = $key } -Body $body
Assert-Equal 'PAID' $order.status 'Saga did not reach PAID'

Write-Host '4/8 replay POST /orders'
$replay = Invoke-RestMethod "$BaseUrl/orders" -Method Post -ContentType 'application/json' `
    -Headers @{ 'Idempotency-Key' = $key } -Body $body
Assert-Equal $order.id $replay.id 'Idempotency replay created a different order'

Write-Host '5/8 reservation is CONFIRMED'
$confirmed = Invoke-RestMethod "$BaseUrl/reservations/$($reservation.id)"
Assert-Equal 'CONFIRMED' $confirmed.status 'Reservation was not confirmed'

Write-Host '6/8 outbox was acknowledged by RabbitMQ'
$outboxSql = "select coalesce(max(id::text),'') from outbox_messages where aggregate_id='$($order.id)' and published_at is not null"
$outboxId = $null
foreach ($attempt in 1..40) {
    $outboxId = (docker compose exec -T order-db psql -U app -d orders -Atc $outboxSql).Trim()
    if ($outboxId) { break }
    Start-Sleep -Milliseconds 250
}
if (!$outboxId) { throw 'Outbox message was not acknowledged.' }

Write-Host '7/8 notification inbox contains messageId'
Wait-ForSql 'notification-db' 'notifications' `
    "select count(*) from processed_messages where message_id='$outboxId'" '1' | Out-Null

Write-Host '8/8 payment survives service restart'
docker compose restart payment-mock-service | Out-Null
foreach ($attempt in 1..60) {
    try {
        $payment = Invoke-RestMethod "http://localhost:8084/payments/orders/$($order.id)" -TimeoutSec 2
        if ($payment.status -eq 'PAID') { break }
    } catch { }
    Start-Sleep -Milliseconds 500
}
Assert-Equal 'PAID' $payment.status 'Durable payment lookup failed after restart'

[pscustomobject]@{
    Result = 'PASS'; OrderId = $order.id; ReservationId = $reservation.id
    OutboxMessageId = $outboxId; CorrelationId = $correlation
} | Format-List
