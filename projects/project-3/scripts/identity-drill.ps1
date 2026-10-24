$ErrorActionPreference='Stop'
if($env:COMPOSE_PROJECT_NAME -notmatch '^roadmap-[a-z0-9-]+$'){throw 'Use a private COMPOSE_PROJECT_NAME=roadmap-...'}
$issuer='http://localhost:8089/realms/roadmap'
foreach($port in @(8080,8081,8082,8083,8084,8085)){
    $ready=$false;$readinessDeadline=(Get-Date).AddSeconds(120)
    do {try{$health=Invoke-RestMethod "http://localhost:$port/actuator/health/readiness" -TimeoutSec 3;if($health.status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 500}while((Get-Date)-lt $readinessDeadline)
    if(!$ready){throw "HTTP readiness failed on port $port"}
}
$deadline=(Get-Date).AddSeconds(120)
$discovery=$null
$lastDiscoveryError=$null
do {try{$discovery=Invoke-RestMethod "$issuer/.well-known/openid-configuration" -TimeoutSec 3;break}catch{$lastDiscoveryError=$_.Exception.Message};Start-Sleep -Milliseconds 500}while((Get-Date)-lt $deadline)
if(!$discovery){throw "Discovery unavailable: $lastDiscoveryError"}
if($discovery.issuer -ne $issuer){throw "Discovery issuer mismatch: expected $issuer; got $($discovery.issuer)"}
Write-Host '1/4 Discovery ready; requesting a local fixture token'
$token=Invoke-RestMethod $discovery.token_endpoint -Method Post -ContentType application/x-www-form-urlencoded `
    -Body @{grant_type='password';client_id='roadmap-user';username='alice';password='local-alice-password'} -TimeoutSec 5
$headers=@{Authorization="Bearer $($token.access_token)"}
$alice='11111111-1111-1111-1111-111111111aaa'
$bob='22222222-2222-2222-2222-222222222bbb'
function Assert-Status([string]$Url,[int]$Expected,$Headers=@{},[string]$Method='GET',$Body=$null){
    $requestParameters=@{Uri=$Url;Method=$Method;Headers=$Headers;TimeoutSec=10;SkipHttpErrorCheck=$true}
    if($Body){$requestParameters.ContentType='application/json';$requestParameters.Body=$Body}
    $reply=Invoke-WebRequest @requestParameters
    if([int]$reply.StatusCode -ne $Expected){throw "Expected HTTP $Expected for $Method $Url, got $($reply.StatusCode)"}
}
Assert-Status 'http://localhost:8080/reservations/11111111-1111-1111-1111-111111111111' 401
Write-Host '2/4 Validating authenticated reservation and ownership'
$reservation=Invoke-RestMethod 'http://localhost:8080/reservations' -Method Post -Headers $headers -ContentType application/json `
    -Body (@{eventId='11111111-1111-1111-1111-111111111111';userId=$alice;quantity=1}|ConvertTo-Json) -TimeoutSec 10
Assert-Status 'http://localhost:8080/reservations' 403 $headers POST (@{eventId='11111111-1111-1111-1111-111111111111';userId=$bob;quantity=1}|ConvertTo-Json)
Assert-Status "http://localhost:8082/reservations/$($reservation.id)/confirm" 403 $headers POST
$headers['Idempotency-Key']="identity-$($reservation.id)"
$body=@{reservationId=$reservation.id;userId=$alice}|ConvertTo-Json
Write-Host '3/4 Completing service-to-service payment and confirmation'
$order=Invoke-RestMethod 'http://localhost:8080/orders' -Method Post -Headers $headers -ContentType application/json -Body $body -TimeoutSec 15
if($order.status -ne 'PAID' -or $order.userId -ne $alice){throw 'Authenticated saga did not reach PAID'}
$replay=Invoke-RestMethod 'http://localhost:8080/orders' -Method Post -Headers $headers -ContentType application/json -Body $body -TimeoutSec 15
if($replay.id -ne $order.id){throw 'Authenticated retry duplicated order'}
$other=Invoke-RestMethod $discovery.token_endpoint -Method Post -ContentType application/x-www-form-urlencoded `
    -Body @{grant_type='password';client_id='roadmap-user';username='bob';password='local-bob-password'} -TimeoutSec 5
Write-Host '4/4 Validating direct-service and gateway ownership'
Assert-Status "http://localhost:8080/orders/$($order.id)" 403 @{Authorization="Bearer $($other.access_token)"}
Assert-Status "http://localhost:8083/orders/$($order.id)" 403 @{Authorization="Bearer $($other.access_token)"}
Assert-Status "http://localhost:8084/payments/orders/$($order.id)" 403 $headers
Write-Host 'PASS: discovery, verified subject, gateway/direct ownership, client credentials saga and retry'
