param([switch]$Check)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
# Explicit entry points prevent a generated table from confusing target design
# with executable implementation. Existing theory is left intact.
$routes = @{
    'system-design/README.md' = @('1. Requirements', 'Wykonywalne studia przypadku', 'Weryfikacja studiów przypadku', '45. Trade-offs')
    'system-design/chat-system/README.md' = @('Fundamenty teoretyczne systemu czatu', 'Jak czytać to laboratorium', '38. Testowanie', '42. Kompromisy projektowe')
    'system-design/ecommerce/README.md' = @('Wymagania', 'Mapa mechanizmów', 'Uruchomienie', 'Failure modes')
    'system-design/file-storage/README.md' = @('3A. Model pojęciowy i granice systemu', '38. Mapa teorii na kod', '41. Uruchamianie i strategia testów', '50. Granice implementacji')
    'system-design/metrics-logging-system/README.md' = @('34. Semantyka sygnałów telemetrycznych', 'Co jest rzeczywiście wykonywalne', '44. Uruchamianie i eksperymenty', '39. Semantyka awarii i dostarczenia')
    'system-design/news-feed/README.md' = @('5. Główne wyzwanie projektowe', '55. Mapa wykonywalnej implementacji', '59. Strategia testów i operacje', '66. Ewolucja architektury i granice rozwiązania')
    'system-design/notification-system/README.md' = @('Fundamenty teoretyczne projektowania Notification System', 'Co rzeczywiście uruchamia projekt', 'Uruchamianie', 'Gwarancje dostarczenia i okna awarii')
    'system-design/payment-system/README.md' = @('Fundamenty teoretyczne', 'Zacznij tutaj', 'Fundamenty teoretyczne', '31. Błędy i edge cases')
    'system-design/rate-limiter/README.md' = @('1. Problem, wymagania i budżet', 'Mapa implementacji', '15. Uruchomienie', '24. Granice gotowości produkcyjnej')
    'system-design/ride-sharing/README.md' = @('7. Geospatial Indexing', 'Jak czytać implementację', 'Jak czytać implementację', '32. Failure Scenarios')
    'system-design/search-autocomplete/README.md' = @('12. Candidate generation vs ranking', 'Jak pracować z laboratorium', 'Jak pracować z laboratorium', '26. Failure modes')
    'system-design/video-streaming/README.md' = @('8. Adaptacyjny streaming', 'Przepływ wykonywalnej implementacji', 'Uruchomienie i weryfikacja', '25. Obsługa błędów')
    'projects/project-3/docs/project-brief.md' = @('Project Overview', 'Start here — czego dowodzi Project 3', 'Najkrótsza ścieżka uruchomienia', 'Failure Scenarios')
}
function Get-Slug([string]$heading) {
    return ($heading.ToLowerInvariant() -replace '[^\p{L}\p{N}_\-\s]', '' -replace '\s', '-')
}
$changed = @()
foreach ($entry in $routes.GetEnumerator()) {
    $path = Join-Path $root $entry.Key
    $content = [IO.File]::ReadAllText($path)
    $labels = @('Teoria i model problemu', 'Implementacja i jej dowody', 'Uruchomienie i eksperymenty', 'Ograniczenia i scenariusze awarii')
    $rows = for ($i = 0; $i -lt 4; $i++) {
        $title = $entry.Value[$i]
        if ($content -notmatch ('(?m)^#{1,6} ' + [regex]::Escape($title) + '\r?$')) { throw "Missing entry point in $path : $title" }
        '| ' + $labels[$i] + ' | [' + $title + '](#' + (Get-Slug $title) + ') |'
    }
    $foundation = if ($entry.Key.StartsWith('system-design/')) {
        $prefix = if ($entry.Key -eq 'system-design/README.md') { '../' } else { '../../' }
        "Wspólne mechanizmy i ich testy znajdziesz w [indeksie pojęć backend-engineering](${prefix}backend-engineering/README.md#globalny-indeks-pojęć). Tutaj śledź ich konsekwencje dla opisywanego systemu."
    } else {
        'Ten dokument zachowuje specyfikację edukacyjną. Aktualne komendy i gwarancje weryfikuj w [README projektu](../README.md).'
    }
    $block = @"
<!-- reading-guide:start -->
## Ścieżki czytania

| Cel czytania | Punkt wejścia |
| --- | --- |
$($rows -join "`n")

$foundation
Diagramy docelowe i warianty rozwoju czytaj razem z mapą implementacji; sam opis architektury nie oznacza, że wszystkie jej komponenty są uruchamiane w laboratorium.
<!-- reading-guide:end -->
"@
    $pattern = '(?s)<!-- reading-guide:start -->.*?<!-- reading-guide:end -->'
    if ([regex]::IsMatch($content, $pattern)) {
        $updated = [regex]::Replace($content, $pattern, [Text.RegularExpressions.MatchEvaluator]{param($m) $block.Trim()})
    } else {
        $end = $content.IndexOf("`n")
        $updated = $content.Substring(0, $end + 1) + "`n" + $block.Trim() + "`n" + $content.Substring($end + 1)
    }
    if ($updated.Replace("`r`n", "`n") -cne $content.Replace("`r`n", "`n")) {
        $changed += $entry.Key
        if (!$Check) { [IO.File]::WriteAllText($path, $updated, [Text.UTF8Encoding]::new($false)) }
    }
}
if ($Check -and $changed.Count) { throw "Reading guides out of date: $($changed -join ', ')" }
Write-Output "reading guides=$($routes.Count) changed=$($changed.Count) check=$Check"
