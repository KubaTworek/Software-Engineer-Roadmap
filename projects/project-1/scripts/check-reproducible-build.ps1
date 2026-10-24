$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $artifact = Join-Path (Get-Location) 'target/booking-capacity-platform-0.1.0-SNAPSHOT.jar'
    $hashes = @()
    for ($attempt=0; $attempt -lt 2; $attempt++) {
        # Ten sam timestamp usuwa jedną znaną przyczynę różnic ZIP/JAR.
        # clean czyści wyłącznie target projektu przez standardowy lifecycle Mavena.
        & .\mvnw.cmd --batch-mode --no-transfer-progress clean package -DskipTests '-Dproject.build.outputTimestamp=2026-01-01T00:00:00Z'
        if ($LASTEXITCODE -ne 0) { throw 'Build nie powiódł się' }
        $hashes += (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash
    }
    if ($hashes[0] -ne $hashes[1]) { throw "Artefakty różnią się: $($hashes -join ', ')" }
    Write-Output "PASS: dwa czyste buildy Booking mają SHA-256 $($hashes[0])"
} finally { Pop-Location }
