$ErrorActionPreference = 'Stop'
$temporaryDatabase = 'orders_restore_drill'
$started = Get-Date

Write-Host "Creating and restoring an isolated snapshot into '$temporaryDatabase'..."
docker compose exec -T order-db sh -ec "pg_dump -U app -d orders -Fc -f /tmp/orders-restore-drill.dump; dropdb -U app --if-exists $temporaryDatabase; createdb -U app $temporaryDatabase; pg_restore -U app -d $temporaryDatabase /tmp/orders-restore-drill.dump"
if ($LASTEXITCODE -ne 0) { throw 'Backup or restore command failed.' }

$sourceCount = (docker compose exec -T order-db psql -U app -d orders -Atc 'select count(*) from orders').Trim()
$restoredCount = (docker compose exec -T order-db psql -U app -d $temporaryDatabase -Atc 'select count(*) from orders').Trim()
if ($sourceCount -ne $restoredCount) {
    throw "Restore invariant failed: source=$sourceCount, restored=$restoredCount"
}

$seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 2)
Write-Host "PASS: restored $restoredCount orders in $seconds s; source and restored counts match."
docker compose exec -T order-db dropdb -U app --if-exists $temporaryDatabase
