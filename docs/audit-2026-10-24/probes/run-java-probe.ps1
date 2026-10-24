param([Parameter(Mandatory)][ValidateSet('Temporal','PaymentRace','RefreshFamily')][string]$Probe)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
# Historyczne źródła pozostają dowodem audytu. Nie kopiujemy testu potwierdzającego
# błąd do poprawionej aplikacji; uruchamiamy stałą regresję docelowej gwarancji.
$module = if($Probe -eq 'PaymentRace') {'projects/project-2'} else {'backend-engineering'}
Push-Location (Join-Path $root $module)
try {
    switch($Probe) {
        'Temporal' { & .\mvnw.cmd --batch-mode --no-transfer-progress -f labs/durable-processes/pom.xml '-Dtest=CancellationCompensationTest' test }
        'PaymentRace' { & .\gradlew.bat integrationTest --tests '*PaymentReconciliationConcurrencyTest' }
        'RefreshFamily' { & .\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=RefreshFamilyTransactionTest' test }
    }
    if($LASTEXITCODE -ne 0) {throw 'Test regresji nie potwierdził gwarancji; sprawdź raport testu'}
} finally { Pop-Location }
