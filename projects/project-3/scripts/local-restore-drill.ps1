param([int]$RecoveryTimeoutSeconds = 60)
$ErrorActionPreference = 'Stop'
if ($env:COMPOSE_PROJECT_NAME -notmatch '^roadmap-[a-z0-9-]+$') {
    throw 'Use an explicit private COMPOSE_PROJECT_NAME=roadmap-... and start its apps first.'
}
$taskId = [guid]::NewGuid().ToString('N')
$temporaryDatabase = "orders_restore_$taskId"
$probe = "$($env:COMPOSE_PROJECT_NAME)-restore-$taskId"
$archive = "/tmp/restore-$taskId.dump"
$started = Get-Date
$paused = $false
function Sql([string]$Database, [string]$Query) {
    $result = docker compose exec -T order-db psql -v ON_ERROR_STOP=1 -U app -d $Database -Atc $Query
    if ($LASTEXITCODE -ne 0) { throw "SQL failed in $Database" }
    return ($result -join "`n").Trim()
}
function Manifest([string]$Database) {
    # Count sam nie wykrywa podmienionej kwoty, stanu sagi ani payloadu outboxa.
    Sql $Database @'
select 'orders|'||count(*)||'|'||md5(coalesce(string_agg(to_jsonb(t)::text,E'\n' order by id),'')) from orders t
union all
select 'outbox|'||count(*)||'|'||md5(coalesce(string_agg(to_jsonb(t)::text,E'\n' order by id),'')) from outbox_messages t;
'@
}
try {
    docker compose stop order-service | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot pause order writer' }
    $paused = $true
    $expected = Manifest 'orders'
    docker compose exec -T order-db pg_dump -U app -d orders -Fc -f $archive
    if ($LASTEXITCODE -ne 0) { throw 'Snapshot failed' }
    docker compose exec -T order-db createdb -U app $temporaryDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated restore target' }
    docker compose exec -T order-db pg_restore --exit-on-error -U app -d $temporaryDatabase $archive
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed' }
    if ((Manifest $temporaryDatabase) -cne $expected) { throw 'Snapshot content checksum mismatch' }
    $order = Sql $temporaryDatabase "select row_to_json(t) from (select id,reservation_id,user_id,amount,idempotency_key from orders where status='PAID' order by created_at limit 1) t"
    if (!$order) { throw 'Run smoke-test first; restore must exercise a real PAID order' }
    $baseline = $order | ConvertFrom-Json
    docker compose run --detach --no-deps --name $probe -p '127.0.0.1::8093' `
        -e SERVER_PORT=8093 -e "SPRING_DATASOURCE_URL=jdbc:postgresql://order-db:5432/$temporaryDatabase" `
        -e APP_RECONCILIATION_ENABLED=false -e APP_OUTBOX_ENABLED=false order-service | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot boot service against restored data' }
    $binding = (docker port $probe 8093/tcp | Select-Object -First 1).Trim()
    $base = "http://$binding"
    $deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    do {
        try { $read = Invoke-RestMethod "$base/orders/$($baseline.id)" -TimeoutSec 2; break } catch { }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    if (!$read -or $read.status -ne 'PAID' -or [decimal]$read.amount -ne [decimal]$baseline.amount) { throw 'Restored HTTP read failed' }
    # Krytyczny retry po restore: ten sam zasób zwraca PAID bez nowego obciążenia.
    $replay = Invoke-RestMethod "$base/orders" -Method Post -ContentType application/json `
        -Headers @{ 'Idempotency-Key'=$(if($baseline.idempotency_key){$baseline.idempotency_key}else{"reservation:$($baseline.reservation_id)"}) } `
        -Body (@{ reservationId=$baseline.reservation_id; userId=$baseline.user_id } | ConvertTo-Json) -TimeoutSec 15
    if ($replay.id -ne $baseline.id -or $replay.status -ne 'PAID') { throw 'Restored idempotent journey failed' }
    $reservation = Invoke-RestMethod "http://localhost:8082/reservations/$($baseline.reservation_id)" -TimeoutSec 5
    $payment = Invoke-RestMethod "http://localhost:8084/payments/orders/$($baseline.id)" -TimeoutSec 5
    if ($reservation.status -ne 'CONFIRMED' -or $payment.status -ne 'PAID') { throw 'Cross-service integrity mismatch' }
    if ((Manifest $temporaryDatabase) -cne $expected) { throw 'Replay unexpectedly mutated restored data/outbox' }
    $outboxId = Sql $temporaryDatabase "select id from outbox_messages where aggregate_id='$($baseline.id)' and published_at is not null limit 1"
    if (!$outboxId) { throw 'Baseline order has no acknowledged outbox' }
    $inboxCount = docker compose exec -T notification-db psql -v ON_ERROR_STOP=1 -U app -d notifications -Atc "select count(*) from processed_messages where message_id='$outboxId'"
    if ($LASTEXITCODE -ne 0 -or "$inboxCount".Trim() -ne '1') { throw 'Acknowledged outbox has no inbox marker' }
    Write-Host "PASS: content + restored HTTP replay + payment/reservation + outbox/inbox; $([math]::Round(((Get-Date)-$started).TotalSeconds,2)) s"
} finally {
    docker rm --force $probe 2>$null | Out-Null
    docker compose exec -T order-db dropdb -U app --if-exists $temporaryDatabase | Out-Null
    docker compose exec -T order-db rm -f $archive | Out-Null
    if ($paused) { docker compose start order-service | Out-Null }
}
