$ErrorActionPreference='Stop'
if ($env:COMPOSE_PROJECT_NAME -notlike 'roadmap-audit-ticketing-*') { throw 'Wymagany efemeryczny stack audytu' }
function Sql([string]$service,[string]$db,[string]$query) {
    $result=docker compose exec -T $service psql -U app -d $db -At -v ON_ERROR_STOP=1 -c $query
    if($LASTEXITCODE -ne 0) {throw "SQL failed: $service"}
    return ($result -join "`n").Trim()
}
$user='audit-commit-'+[Guid]::NewGuid().ToString('N')
$res=Invoke-RestMethod 'http://localhost:8082/reservations' -Method Post -ContentType 'application/json' -Body (@{eventId='11111111-1111-1111-1111-111111111111';userId=$user;quantity=1}|ConvertTo-Json)
$id=([Guid]$res.id).ToString()
$body=@{reservationId=$id;userId=$user}|ConvertTo-Json -Compress
$key='audit-commit-'+$id
$trigger=@'
CREATE FUNCTION audit_reject_order_commit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.user_id LIKE 'audit-commit-%' THEN
    RAISE EXCEPTION 'AUDIT: rejected local commit after external side effects';
  END IF;
  RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER audit_commit_probe AFTER INSERT ON orders
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION audit_reject_order_commit();
'@
try {
    Sql order-db orders $trigger | Out-Null
    $rejected=$false
    try {Invoke-RestMethod 'http://localhost:8083/orders' -Method Post -ContentType 'application/json' -Headers @{'Idempotency-Key'=$key} -Body $body | Out-Null}
    catch {$rejected=$true}
    if(-not $rejected) {throw 'Nie odrzucono commitu'}
    $orders=Sql order-db orders "select count(*) from orders where reservation_id='$id'"
    $payments=Sql payment-db payments "select count(*) from payments where user_id='$user' and status='PAID'"
    $reservation=Sql reservation-db reservation "select status from reservations where id='$id'"
    if($orders -ne '0' -or $payments -ne '1' -or $reservation -ne 'CONFIRMED') {throw "Inny wynik: orders=$orders payments=$payments reservation=$reservation"}
    Write-Output "AUDIT CONFIRMED: orders=$orders payments=$payments reservation=$reservation after rejected local commit"
} finally {
    Sql order-db orders 'DROP TRIGGER IF EXISTS audit_commit_probe ON orders; DROP FUNCTION IF EXISTS audit_reject_order_commit();' | Out-Null
}
$retryRejected=$false
try {Invoke-RestMethod 'http://localhost:8083/orders' -Method Post -ContentType 'application/json' -Headers @{'Idempotency-Key'=$key} -Body $body | Out-Null}
catch {$retryRejected=$true}
if(-not $retryRejected) {throw 'Retry odtworzył zamówienie; zmienił się kontrakt probe'}
Write-Output 'AUDIT CONFIRMED: retry with same HTTP Idempotency-Key cannot recover orphaned paid reservation'
