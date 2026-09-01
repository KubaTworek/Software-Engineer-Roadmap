param(
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$moduleRoot = Split-Path -Parent $PSScriptRoot
$mainRoot = Join-Path $moduleRoot 'src/main/java/pl/jakubtworek/backend_engineering'
$testRoot = Join-Path $moduleRoot 'src/test/java/pl/jakubtworek/backend_engineering'
$startMarker = '<!-- material-card:start -->'
$endMarker = '<!-- material-card:end -->'

$titleOverrides = @{
    'concurrent_hash_map' = 'ConcurrentHashMap i atomowe ładowanie cache'
    'deadlock' = 'Deadlock — reprodukcja, wykrywanie i zapobieganie'
    'race_condition' = 'Race condition i utrata aktualizacji'
    'testing' = 'Deterministyczne testowanie współbieżności'
    'thread_confinement' = 'Thread confinement i własność stanu'
    'visibility' = 'Widoczność pamięci i happens-before'
}

$pathTitleOverrides = @{
    'stage_1/block_a' = 'Stage 1A — współbieżność, czas i modele wykonania'
    'stage_1/block_b' = 'Stage 1B — wydajność JVM i metodologia pomiaru'
    'stage_1/block_c' = 'Stage 1C — Spring pod maską'
    'stage_1/block_d' = 'Stage 1D — dane, persystencja i wyszukiwanie'
    'stage_1/block_e' = 'Stage 1E — refaktoryzacja i bezpieczna zmiana'
    'stage_1/block_f' = 'Stage 1F — networking aplikacyjny'
    'stage_2/block_a' = 'Stage 2A — modelowanie, API i architektura'
    'stage_2/block_b' = 'Stage 2B — event-driven, messaging i kanały live'
    'stage_2/block_c' = 'Stage 2C — delivery i operacje'
    'stage_2/block_d' = 'Stage 2D — Application Security i Secure SDLC'
    'stage_3/block_a' = 'Stage 3A — system design i systemy rozproszone'
    'stage_3/block_b' = 'Stage 3B — observability'
    'stage_3/block_c' = 'Stage 3C — cloud architecture i disaster recovery'
}

function Get-Scope([string]$path) {
    if ($path -match '[\\/]stage_1[\\/]') { return 'fundament' }
    if ($path -match '[\\/]stage_2[\\/]') { return 'praktyka-produkcyjna' }
    if ($path -match '[\\/]stage_3[\\/]') { return 'temat-zaawansowany' }
    return 'fundament'
}

function Get-Boundary([string]$scope, [string]$path) {
    if ($path -match '[\\/]block_b[\\/](allocation_rate|array_vs_linked|big_decimal|cpu_vs_io|escape_analysis|false_sharing|g1_vs_zgc|heap_size|heap_vs_stack|lock_contention|naive_vs_jmh|object_pooling|polymorphism_vs_jit|stream_vs_loop|thread_count)[\\/]') {
        return 'Wynik eksperymentu opisuje tę maszynę i workload; nie jest uniwersalną liczbą produkcyjną.'
    }
    switch ($scope) {
        'fundament' { return 'Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.' }
        'praktyka-produkcyjna' { return 'Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.' }
        default { return 'Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.' }
    }
}

function Get-RelativeTopicPath([System.IO.FileInfo]$readme) {
    $relative = [System.IO.Path]::GetRelativePath($mainRoot, $readme.DirectoryName)
    if ($relative.StartsWith('..')) {
        $relative = [System.IO.Path]::GetRelativePath((Join-Path $moduleRoot 'src/test/java/pl/jakubtworek/backend_engineering'), $readme.DirectoryName)
    }
    return $relative
}

function Get-TestCommand([System.IO.FileInfo]$readme) {
    $topicPath = Get-RelativeTopicPath $readme
    $candidate = Join-Path $testRoot $topicPath
    $tests = @()
    if (Test-Path -LiteralPath $candidate) {
        $tests = @(Get-ChildItem -LiteralPath $candidate -Recurse -Filter '*Test.java' |
            Sort-Object FullName |
            Select-Object -First 3 |
            ForEach-Object { $_.BaseName })
    }
    if ($tests.Count -eq 0) {
        return '.\mvnw.cmd --batch-mode --no-transfer-progress test'
    }
    return '.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=' + ($tests -join ',') + '" test'
}

function Format-Role([string]$name, [string]$role) {
    return '`' + $name + '` = `' + $role + '`'
}

function Get-ClassRoles([System.IO.FileInfo]$readme) {
    $classes = @(Get-ChildItem -LiteralPath $readme.DirectoryName -Recurse -Filter '*.java' |
        Where-Object { $_.FullName -notmatch '[\\/]src[\\/](test|main)[\\/]java[\\/].*[\\/]src[\\/]' })
    $roles = [ordered]@{
        'naive' = @($classes | Where-Object { $_.BaseName -match '^(Broken|Naive|Unsafe|Legacy)' })
        'correct' = @($classes | Where-Object { $_.BaseName -match '^(Correct|Safe|Fenced|Idempotent|Atomic|Validated|Bounded)' })
        'simulation' = @($classes | Where-Object { $_.BaseName -match '(^InMemory|^Fake|Simulation$|Simulator$|Demo$)' })
        'production-boundary' = @($classes | Where-Object {
            $_.BaseName -match '(Controller|Adapter|Publisher|Consumer|Configuration)$' -and
            $_.BaseName -notin @('DemandAwarePublisher', 'NaivePushPublisher')
        })
    }
    $parts = @()
    foreach ($entry in $roles.GetEnumerator()) {
        if ($entry.Value.Count -eq 0) { continue }
        $shown = @($entry.Value | Sort-Object BaseName -Unique | Select-Object -First 3 |
            ForEach-Object { Format-Role $_.BaseName $entry.Key })
        $remaining = ($entry.Value | Select-Object -ExpandProperty BaseName -Unique).Count - $shown.Count
        $part = $shown -join ', '
        if ($remaining -gt 0) { $part += " (+$remaining)" }
        $parts += $part
    }
    if ($parts.Count -eq 0) {
        return 'brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej'
    }
    return $parts -join '; '
}

function Get-Card([System.IO.FileInfo]$readme, [string]$title) {
    $scope = Get-Scope $readme.FullName
    $testCommand = Get-TestCommand $readme
    $roles = Get-ClassRoles $readme
    $boundary = Get-Boundary $scope $readme.FullName
    $mistake = 'Uznanie pojedynczego wyniku dotyczącego „{0}” za gwarancję bez sprawdzenia niezmiennika i failure modes.' -f $title
    return @"
$startMarker
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** ``$scope``
> - **Uczy:** $title.
> - **Typowy błąd:** $mistake
> - **Najkrótsza weryfikacja:** ``$testCommand``
> - **Role klas:** $roles.
> - **Granica:** $boundary
$endMarker
"@
}

function Normalize-BodyHeadings([string]$body, [string]$title) {
    $insideFence = $false
    $normalized = foreach ($line in ($body -split '\r?\n')) {
        # Remove stray carriage returns left by documents that previously mixed
        # CRLF and LF. A carriage return has no semantic role inside Markdown text.
        $line = $line.Replace("`r", '')
        if (-not $insideFence -and ($line.Trim() -ceq "# $title" -or $line.Trim() -ceq "## $title")) {
            continue
        }
        if ($line -match '^\s*(```|~~~)') {
            $insideFence = -not $insideFence
            $line
            continue
        }
        if (-not $insideFence -and $line -match '^#[ \t]+') {
            '#' + $line
        } else {
            $line
        }
    }
    return ($normalized -join "`n")
}

$readmes = @(Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'src') -Recurse -Filter 'README.md' |
    Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' })
$changed = @()

foreach ($readme in $readmes) {
    $content = [System.IO.File]::ReadAllText($readme.FullName)
    if ($Check) {
        $requiredFragments = @(
            $startMarker,
            '> - **Zakres:**',
            '> - **Uczy:**',
            '> - **Typowy błąd:**',
            '> - **Najkrótsza weryfikacja:**',
            '> - **Role klas:**',
            '> - **Granica:**',
            $endMarker)
        $valid = $content.StartsWith('# ')
        foreach ($fragment in $requiredFragments) {
            $valid = $valid -and $content.Contains($fragment)
        }
        if (-not $valid) {
            $changed += [System.IO.Path]::GetRelativePath($moduleRoot, $readme.FullName)
        }
        continue
    }

    $titleMatch = [regex]::Match($content, '(?m)^#[ \t]+([^\r\n]+)$')
    if ($titleMatch.Success) {
        $title = $titleMatch.Groups[1].Value.Trim()
    } else {
        $directoryName = $readme.Directory.Name
        $title = if ($titleOverrides.ContainsKey($directoryName)) {
            $titleOverrides[$directoryName]
        } else {
            ($directoryName -replace '_', ' ')
        }
        $content = "# $title`n`n" + $content.TrimStart()
        $titleMatch = [regex]::Match($content, '(?m)^#[ \t]+([^\r\n]+)$')
    }

    $topicKey = (Get-RelativeTopicPath $readme).Replace('\', '/')
    if ($pathTitleOverrides.ContainsKey($topicKey)) {
        $title = $pathTitleOverrides[$topicKey]
        $heading = [regex]::new('(?m)^#[ \t]+[^\r\n]+$')
        $content = $heading.Replace($content, '# ' + $title, 1)
    }

    $card = Get-Card $readme $title
    $cardPattern = '(?s)' + [regex]::Escape($startMarker) + '.*?' + [regex]::Escape($endMarker)
    $withoutCard = [regex]::Replace($content, $cardPattern, '')
    # A previous card changes offsets, so rebuild from semantic parts instead of
    # reusing positions from the original string. Removing every identical H1 also
    # repairs files produced by an interrupted or older version of this script.
    $titleLine = '# ' + $title
    $bodyLines = @($withoutCard -split '\r?\n' |
        Where-Object { $_.Trim() -cne $titleLine })
    $body = Normalize-BodyHeadings (($bodyLines -join "`n").Trim()) $title
    $updated = '# ' + $title + "`n`n" + $card.Trim() + "`n`n" + $body + "`n"

    $comparableUpdated = $updated.Replace("`r`n", "`n")
    $comparableContent = $content.Replace("`r`n", "`n")
    if ($comparableUpdated -cne $comparableContent) {
        $changed += [System.IO.Path]::GetRelativePath($moduleRoot, $readme.FullName)
        if (-not $Check) {
            [System.IO.File]::WriteAllText($readme.FullName, $updated, [System.Text.UTF8Encoding]::new($false))
        }
    }
}

if ($Check -and $changed.Count -gt 0) {
    $changed | ForEach-Object { Write-Output "outdated material card: $_" }
    exit 1
}

Write-Output "material cards processed=$($readmes.Count) changed=$($changed.Count) check=$Check"
