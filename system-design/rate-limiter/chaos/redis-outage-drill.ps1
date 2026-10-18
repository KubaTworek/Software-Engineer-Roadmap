$ErrorActionPreference = 'Stop'

# Nieinteraktywny game day: kontrolowana utrata Redisa, ruch podczas awarii i
# przywrócenie zależności. Wyniki 200/429 należy zestawić z metryką fallback oraz
# strategiami konkretnych reguł; sam kod HTTP nie rozstrzyga, czy test przeszedł.
try {
    docker compose stop redis
    $statuses = 1..20 | ForEach-Object {
        try {
            (Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/api/users' `
                -Headers @{'X-Api-Key'="chaos-$($_)"} -TimeoutSec 2).StatusCode
        } catch {
            if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        }
    }
    $statuses | Group-Object | Sort-Object Name | Format-Table Name, Count
}
finally {
    docker compose start redis
}
