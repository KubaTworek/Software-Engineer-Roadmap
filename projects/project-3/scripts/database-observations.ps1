param([ValidateSet('catalog-db','reservation-db','order-db')][string]$Service = 'order-db')
$ErrorActionPreference = 'Stop'
$database = @{ 'catalog-db'='catalog'; 'reservation-db'='reservation'; 'order-db'='orders' }[$Service]
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    Get-Content (Join-Path $PSScriptRoot 'database-observations.sql') -Raw |
        docker compose exec -T $Service psql -U app -d $database -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw 'Niepełny pomiar bazy; sprawdź rolę, uprawnienia i stan serwisu' }
} finally { Pop-Location }
