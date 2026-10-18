param([string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($RepositoryRoot)
$errors = [Collections.Generic.List[string]]::new()
function Add-Problem([string]$file, [string]$message) { $errors.Add("${file}: $message") }
function Get-HeadingIds([string]$content) {
    $ids = @{}
    $duplicates = @{}
    $fence = $null
    foreach ($line in ($content -split '\r?\n')) {
        if ($line -match '^\s*(`{3,}|~{3,})') {
            $mark = $Matches[1][0]
            if ($null -eq $fence) { $fence = $mark } elseif ($fence -eq $mark) { $fence = $null }
            continue
        }
        if ($null -eq $fence -and $line -match '^#{1,6}\s+(.+?)\s*#*\s*$') {
            $slug = $Matches[1].ToLowerInvariant() -replace '[^\p{L}\p{N}_\-\s]', '' -replace '\s', '-'
            $id = $slug
            if ($duplicates.ContainsKey($slug)) { $duplicates[$slug]++; $id += '-' + $duplicates[$slug] }
            else { $duplicates[$slug] = 0 }
            $ids[$id] = $true
        }
    }
    return $ids
}
Push-Location $root
try {
    $docs = @(rg --files -g '*.md' -g '!**/target/**' -g '!**/build/**')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate repository Markdown files with rg.' }
    $headingCache = @{}
    $linkCount = 0
    foreach ($doc in $docs) {
        $full = Join-Path $root $doc
        $content = [IO.File]::ReadAllText($full)
        $lines = $content -split '\r?\n'
        $fence = $null
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i]
            if ($line -match '^\s*(`{3,}|~{3,})') {
                $mark = $Matches[1][0]
                if ($null -eq $fence) { $fence = $mark } elseif ($fence -eq $mark) { $fence = $null }
                continue
            }
            if ($null -ne $fence) { continue }
            # Inline Markdown links, including fragment-only links. External URLs
            # are deliberately not fetched: the check is deterministic and offline.
            foreach ($m in [regex]::Matches($line, '\[[^\]]*\]\(([^)]+)\)')) {
                $target = $m.Groups[1].Value.Trim('<', '>')
                if ($target -match '^(https?://|mailto:)' -or $target -match '\s"') { continue }
                $linkCount++
                $parts = $target -split '#', 2
                $pathPart = [uri]::UnescapeDataString($parts[0])
                $resolved = if (!$pathPart) { $full } else { [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetDirectoryName($full)) $pathPart)) }
                if (!(Test-Path -LiteralPath $resolved)) { Add-Problem $doc "line $($i+1): missing $target"; continue }
                if ($parts.Count -gt 1 -and $resolved.EndsWith('.md')) {
                    if (!$headingCache.ContainsKey($resolved)) { $headingCache[$resolved] = Get-HeadingIds ([IO.File]::ReadAllText($resolved)) }
                    $fragment = [uri]::UnescapeDataString($parts[1])
                    if (!$headingCache[$resolved].ContainsKey($fragment)) { Add-Problem $doc "line $($i+1): missing heading $target" }
                }
            }
        }
        if ($null -ne $fence) { Add-Problem $doc 'unclosed code fence' }
        if ($doc.Replace('\', '/') -notmatch '^backend-engineering/src/.*/README.md$') { continue }
        $card = [regex]::Match($content, '(?s)<!-- material-card:start -->(.*?)<!-- material-card:end -->')
        if (!$card.Success) { Add-Problem $doc 'missing material card'; continue }
        foreach ($field in @('Zakres', 'Uczy', 'Typowy błąd', 'Najkrótsza weryfikacja', 'Katalog uruchomienia', 'Role klas', 'Granica')) {
            if ($card.Value -notmatch ('\*\*' + [regex]::Escape($field) + ':\*\*\s+\S')) { Add-Problem $doc "missing or empty $field" }
        }
        if ($card.Value.Contains('Uznanie pojedynczego wyniku dotyczącego')) { Add-Problem $doc 'generic teaching point' }
        $module = Join-Path $root 'backend-engineering'
        if ($card.Value -match '\s-f\s+([^\s`]+)') {
            $pom = Join-Path $module $Matches[1]
            if (!(Test-Path -LiteralPath $pom)) { Add-Problem $doc "missing Maven module $pom" }
            $module = Split-Path -Parent $pom
        }
        if ($card.Value -match '-Dtest=([^"`\s]+)') {
            foreach ($test in ($Matches[1] -split ',')) {
                $testFiles = @(Get-ChildItem (Join-Path $module 'src/test') -Recurse -Filter "$test.java" -ErrorAction SilentlyContinue)
                if (!$testFiles.Count) { Add-Problem $doc "test $test does not exist in selected module" }
            }
        }
        $normalized = $doc.Replace('\', '/')
        if ($normalized -match 'stage_2/block_c/(workshop|configuration|docker|kubernetes)/README.md$|stage_3/block_c/README.md$') {
            if ($card.Value -notmatch '\s-f\s') { Add-Problem $doc 'standalone laboratory must select its own pom with -f' }
        }
    }
    # The reference application has a concrete documentation contract: a card
    # alone must not conceal accidental loss of its body.
    $workshop = 'backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/README.md'
    $body = [IO.File]::ReadAllText((Join-Path $root $workshop))
    foreach ($required in @('## Problem i niezmiennik', '## Uruchomienie', '## Przepływ i endpointy', '## Dowody i ograniczenia', 'ApplicationHealthStateTest', 'DeliveryArtifactsTest', 'StorageServicesTest')) {
        if (!$body.Contains($required)) { Add-Problem $workshop "missing $required" }
    }
    $profileSystems = @(Get-ChildItem (Join-Path $root 'system-design') -Directory | Where-Object {
        $found = $false
        foreach ($pom in (Get-ChildItem $_.FullName -Recurse -Filter pom.xml | Where-Object { $_.FullName -notmatch '[\\/](target|build)[\\/]' })) {
            [xml]$xml = [IO.File]::ReadAllText($pom.FullName)
            if ($xml.SelectSingleNode("//*[local-name()='profile']/*[local-name()='id' and text()='infrastructure-tests']")) { $found = $true }
        }
        $found
    } | ForEach-Object Name | Sort-Object)
    $workflow = [IO.File]::ReadAllText((Join-Path $root '.github/workflows/system-design-ci.yml'))
    $job = [regex]::Match($workflow, '(?ms)^  infrastructure-tests:.*?(?=^  [a-z][a-z-]+:|\z)').Value
    $matrix = [regex]::Match($job, '(?ms)^        system:\s*\r?\n(.*?)(?=^    defaults:)').Groups[1].Value
    $ciSystems = @([regex]::Matches($matrix, '(?m)^          - ([a-z-]+)\s*$') | ForEach-Object { $_.Groups[1].Value } | Sort-Object)
    if (@(Compare-Object $profileSystems $ciSystems).Count) { Add-Problem '.github/workflows/system-design-ci.yml' "infrastructure matrix must equal discovered profiles: $($profileSystems -join ', ')" }
    $roadmap = [IO.File]::ReadAllText((Join-Path $root 'README.md'))
    foreach ($topic in @('virtual_threads', 'temporal_correctness', 'reactive_streams', 'search_engine', 'graphql', 'grpc', 'websocket')) {
        if (!$roadmap.Contains("/$topic/README.md")) { Add-Problem 'README.md' "missing roadmap topic $topic" }
    }
    & (Join-Path $root 'scripts/update-reading-guides.ps1') -Check
    if ($errors.Count) { $errors | ForEach-Object { Write-Output $_ }; throw "$($errors.Count) documentation errors" }
    Write-Output "documentation: $($docs.Count) files, $linkCount local links, $($profileSystems.Count) infrastructure profiles; OK"
} finally { Pop-Location }
