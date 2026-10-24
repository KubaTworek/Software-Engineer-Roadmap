param([int]$SteadySeconds=900,[int]$OutageSeconds=15,[int]$RecoverySeconds=900,[int]$Rate=2,
      [decimal]$EstimatedHourlyCost=1.00,[string]$Currency='PLN')
$ErrorActionPreference='Stop'
if($env:COMPOSE_PROJECT_NAME -notmatch '^roadmap-[a-z0-9-]+$'){throw 'Use a private COMPOSE_PROJECT_NAME=roadmap-...'}
if($SteadySeconds -lt 30 -or $RecoverySeconds -lt 30 -or $SteadySeconds+$OutageSeconds+$RecoverySeconds -gt 7200 -or $OutageSeconds -lt 1 -or $OutageSeconds -gt 60 -or $Rate -lt 1 -or $Rate -gt 20){throw 'Invalid bounded drill parameters'}
if($EstimatedHourlyCost -lt 0 -or $Currency -notmatch '^[A-Z]{3}$'){throw 'Invalid explicit cost assumptions'}
# Kontener running/Compose --wait bez HEALTHCHECK nie oznacza gotowego HTTP.
foreach($port in @(8080,8081,8082,8083,8084,8085)){
    $ready=$false;$readinessDeadline=(Get-Date).AddSeconds(120)
    do {
        try { $health=Invoke-RestMethod "http://localhost:$port/actuator/health/readiness" -TimeoutSec 2; if($health.status -eq 'UP'){$ready=$true;break} } catch {}
        Start-Sleep -Milliseconds 500
    } while((Get-Date)-lt $readinessDeadline)
    if(!$ready){throw "HTTP readiness failed on port $port"}
}
$access=Invoke-WebRequest "http://localhost:8080/orders/$([guid]::NewGuid())" -SkipHttpErrorCheck -TimeoutSec 5
if([int]$access.StatusCode -in @(401,403)){throw 'This generator requires the local playground or an authenticated workload adapter'}
$run='endurance-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$loadContainer="$($env:COMPOSE_PROJECT_NAME)-$run"
$total=$SteadySeconds+$OutageSeconds+$RecoverySeconds
$directory=Join-Path (Get-Location) 'target/endurance'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$stdout=Join-Path $directory "$run.stdout.log"
$stderr=Join-Path $directory "$run.stderr.log"
$arguments=@('compose','--profile','apps','--profile','load-tests','run','--rm','--no-deps','--name',$loadContainer,'-e',"RUN_ID=$run",'-e',"DURATION=$($total)s",'-e',"RATE=$Rate",'k6','run','load-tests/endurance-recovery.js')
$launch=@{FilePath='docker';ArgumentList=$arguments;WorkingDirectory=(Get-Location).Path;PassThru=$true;RedirectStandardOutput=$stdout;RedirectStandardError=$stderr}
if($IsWindows){$launch.WindowStyle='Hidden'}
$process=Start-Process @launch
$started=[DateTimeOffset]::UtcNow
$stopped=$false;$restarted=$false
$samples=[Collections.Generic.List[object]]::new()
$result='FAILED'
try {
    while(!$process.HasExited){
        $elapsed=([DateTimeOffset]::UtcNow-$started).TotalSeconds
        if($elapsed -gt $total+90){throw 'Load generator exceeded its deadline'}
        if(!$stopped -and $elapsed -ge $SteadySeconds){
            docker compose stop rabbitmq | Out-Null
            if($LASTEXITCODE -ne 0){throw 'Cannot stop broker'}
            $stopped=$true;$stopAt=[DateTimeOffset]::UtcNow
        }
        if($stopped -and !$restarted -and ([DateTimeOffset]::UtcNow-$stopAt).TotalSeconds -ge $OutageSeconds){
            docker compose start rabbitmq | Out-Null
            if($LASTEXITCODE -ne 0){throw 'Cannot restore broker'}
            $restarted=$true
        }
        $stats=docker stats --no-stream --format '{{json .}}' (docker compose ps -q order-service reservation-service notification-service)
        $samples.Add(@{at=[DateTimeOffset]::UtcNow.ToString('o');containers=@($stats|ForEach-Object{$_|ConvertFrom-Json})})
        Start-Sleep -Seconds 2
        $process.Refresh()
    }
    $problems=[Collections.Generic.List[string]]::new()
    if($process.ExitCode -ne 0){$problems.Add("k6 thresholds failed; see $stdout and $stderr")}
    if(!$stopped -or !$restarted){throw 'Fault phase was not exercised'}
    function Sql([string]$Service,[string]$Database,[string]$Query){
        $value=docker compose exec -T $Service psql -v ON_ERROR_STOP=1 -U app -d $Database -Atc $Query
        if($LASTEXITCODE -ne 0){throw "SQL failed in $Service"};return ($value -join "`n").Trim()
    }
    $drainDeadline=(Get-Date).AddSeconds(30)
    do {
        $completed=[long](Sql order-db orders "select count(*) from orders where user_id like '$run-%' and status='PAID'")
        $unfinished=[long](Sql order-db orders "select count(*) from orders where user_id like '$run-%' and status<>'PAID'")
        $missing=Sql order-db orders "select count(*) from orders o where user_id like '$run-%' and not exists(select 1 from outbox_messages m where m.aggregate_id=o.id::text and m.published_at is not null and m.dead_at is null)"
        if($unfinished -eq 0 -and $missing -eq '0'){break}
        Start-Sleep -Milliseconds 250
    } while((Get-Date)-lt $drainDeadline)
    if($completed -eq 0 -or $unfinished -ne 0 -or $missing -ne '0'){$problems.Add('Workflow/outbox did not converge in recovery phase')}
    $inventory=Sql reservation-db reservation "select count(*) from event_inventory i where i.available_tickets<0 or i.available_tickets + coalesce((select sum(r.quantity) from reservations r where r.event_id=i.event_id and r.status in ('PENDING','CONFIRMED')),0)<>i.total_tickets"
    if($inventory -ne '0'){$problems.Add('Inventory conservation failed')}
    $messages=Sql order-db orders "select m.id from outbox_messages m join orders o on m.aggregate_id=o.id::text where o.user_id like '$run-%'"
    $messageIds=@($messages -split '\s+'|Where-Object{$_})
    do {
        $delivered=0L
        # Małe porcje nie przekraczają limitu długości polecenia Windows przy tysiącach zdarzeń.
        for($offset=0;$offset -lt $messageIds.Count;$offset+=100){
            $last=[math]::Min($offset+99,$messageIds.Count-1)
            $ids=@($messageIds[$offset..$last]|ForEach-Object{"'$_'"}) -join ','
            $delivered += [long](Sql notification-db notifications "select count(*) from processed_messages where message_id in ($ids)")
        }
        if($delivered -eq $completed){break}
        Start-Sleep -Milliseconds 250
    }while((Get-Date)-lt $drainDeadline)
    if($delivered -ne $completed){$problems.Add('Inbox and completed orders disagree')}
    $pairs=@((Sql order-db orders "select id::text||':'||reservation_id::text from orders where user_id like '$run-%'") -split '\s+'|Where-Object{$_})
    $paidLedger=0L;$confirmed=0L
    for($offset=0;$offset -lt $pairs.Count;$offset+=100){
        $last=[math]::Min($offset+99,$pairs.Count-1)
        $orderIds=@($pairs[$offset..$last]|ForEach-Object{"'$($_.Split(':')[0])'"}) -join ','
        $reservationIds=@($pairs[$offset..$last]|ForEach-Object{"'$($_.Split(':')[1])'"}) -join ','
        $paidLedger += [long](Sql payment-db payments "select count(*) from payments where order_id in ($orderIds) and status='PAID'")
        $confirmed += [long](Sql reservation-db reservation "select count(*) from reservations where id in ($reservationIds) and status='CONFIRMED'")
    }
    if($paidLedger -ne $completed -or $confirmed -ne $completed){$problems.Add('Financial ledger/reservations disagree with completed orders')}
    $summary=Get-Content (Join-Path $directory "$run.json") -Raw|ConvertFrom-Json
    $attempts=[long]$summary.metrics.checkout_attempts.values.count
    $good=[long]$summary.metrics.checkout_good.values.count
    if($completed -ne $attempts){$problems.Add('Accepted workload lost a logical order')}
    $seconds=[long][math]::Ceiling(([DateTimeOffset]::UtcNow-$started).TotalSeconds)
    $estimate=$EstimatedHourlyCost*$seconds/3600
    $culture=[Globalization.CultureInfo]::InvariantCulture
    $csv="scenario,seconds,cost,currency,eligible,good,completed,p99Millis`n$run,$seconds,$($estimate.ToString($culture)),$Currency,$attempts,$good,$completed,$($summary.metrics.checkout_latency.values.'p(99)'.ToString($culture))"
    [IO.File]::WriteAllText((Join-Path $directory "$run.csv"),$csv)
    if($problems.Count){throw ($problems -join '; ')}
    $result='PASS'
    Write-Host "PASS: $run, $completed unique PAID, $delivered inbox markers; observations in $directory"
} finally {
    @{result=$result;run=$run;brokerStopped=$stopped;brokerRestarted=$restarted;estimated=$true;hourlyCost=$EstimatedHourlyCost;currency=$Currency;started=$started.ToString('o');completed=$completed;delivered=$delivered;paidLedger=$paidLedger;confirmed=$confirmed;samples=$samples} |
        ConvertTo-Json -Depth 8 | Set-Content (Join-Path $directory "$run.resources.json")
    if($stopped -and !$restarted){docker compose start rabbitmq|Out-Null}
    if(!$process.HasExited){$process.Kill($true)}
    docker rm --force $loadContainer 2>$null | Out-Null
}
