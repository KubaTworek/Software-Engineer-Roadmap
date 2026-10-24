$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot 'compose.yml'
$labProject = 'roadmap-db-' + [Guid]::NewGuid().ToString('N').Substring(0,12)
function Invoke-LabSql([string]$service, [string]$sql) {
    $result = docker compose -p $labProject -f $composeFile exec -T $service psql -U postgres -At -v ON_ERROR_STOP=1 -c $sql
    if ($LASTEXITCODE -ne 0) { throw "SQL nie powiódł się w $service" }
    return $result
}
try {
    docker compose -p $labProject -f $composeFile up -d --wait --wait-timeout 120
    if ($LASTEXITCODE -ne 0) { throw 'Klaster laboratoryjny nie jest gotowy' }
    Invoke-LabSql primary 'create table freshness_probe(id int primary key, value int); insert into freshness_probe values(1,1)' | Out-Null
    $ready = $false
    for ($i=0; $i -lt 30; $i++) {
        $relation = Invoke-LabSql replica "select to_regclass('freshness_probe')"
        if ($relation -eq 'freshness_probe') { $ready = $true; break }
        Start-Sleep -Milliseconds 200
    }
    if (-not $ready) { throw 'Nie dogoniono początkowej tabeli' }
    Invoke-LabSql replica 'select pg_wal_replay_pause()' | Out-Null
    Invoke-LabSql primary 'update freshness_probe set value=2 where id=1' | Out-Null
    if ((Invoke-LabSql replica 'select value from freshness_probe where id=1') -ne '1') { throw 'Nie odtworzono starego odczytu' }
    Invoke-LabSql replica 'select pg_wal_replay_resume()' | Out-Null
    $caughtUp = $false
    for ($i=0; $i -lt 30; $i++) {
        if ((Invoke-LabSql replica 'select value from freshness_probe where id=1') -eq '2') { $caughtUp=$true; break }
        Start-Sleep -Milliseconds 200
    }
    if (-not $caughtUp) { throw 'Replika nie dogoniła źródła w budżecie czasu' }
    Invoke-LabSql primary "select * from pg_create_logical_replication_slot('retention_probe','test_decoding')" | Out-Null
    Invoke-LabSql primary 'update freshness_probe set value=3 where id=1' | Out-Null
    $retained = Invoke-LabSql primary "select pg_wal_lsn_diff(pg_current_wal_lsn(),restart_lsn)::bigint from pg_replication_slots where slot_name='retention_probe'"
    if ([long]$retained -le 0) { throw 'Nie zaobserwowano retencji WAL' }
    Write-Output "PASS: odczyt 1 podczas pause, potem 2; slot zatrzymuje $retained bajtów WAL"
} finally {
    # Usuwamy wyłącznie efemeryczny stack o losowej nazwie utworzony w tym wywołaniu.
    docker compose -p $labProject -f $composeFile down --volumes --remove-orphans
}
