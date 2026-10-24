param([switch]$Check)
$ErrorActionPreference = 'Stop'
$moduleRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $moduleRoot 'checks/DocumentTools.ps1')
$files = @(Get-ChildItem -LiteralPath $moduleRoot -Filter '*.md' | Where-Object Name -Match '^\d' | Sort-Object Name)
$changed = [Collections.Generic.List[string]]::new()
$index = [Collections.Generic.List[string]]::new()
$index.Add('# Indeks pytań')
$index.Add('')
$index.Add('[Rozdziały i sposób nauki](README.md) · [Materiały diagnostyczne](DIAGNOSTYKA.md)')
$index.Add('')
$index.Add('Indeks prowadzi do jednej kanonicznej odpowiedzi na każde pytanie. Dopytania pod wybranymi odpowiedziami służą samodzielnemu sprawdzeniu rozumienia.')
$total = 0
foreach ($file in $files) {
    $body = [IO.File]::ReadAllText($file.FullName).Replace("`r`n","`n")
    $questions = @(Get-QuestionSections $body)
    $title = [regex]::Match($body,'(?m)^# (.+)$').Groups[1].Value
    $rows = @($questions | ForEach-Object { '- [' + $_.Id + '. ' + $_.Title + '](#' + (Get-HeadingSlug ($_.Heading -replace '^## ','')) + ')' })
    $navigation = "<!-- question-toc:start -->`n## Spis pytań`n`n" + ($rows -join "`n") + "`n<!-- question-toc:end -->"
    $pattern = '(?s)<!-- question-toc:start -->.*?<!-- question-toc:end -->'
    if ([regex]::IsMatch($body,$pattern)) {
        $updated = [regex]::Replace($body,$pattern,[Text.RegularExpressions.MatchEvaluator]{param($m) $navigation})
    } else {
        $first = [regex]::Match($body,'(?m)^## [A-Z]+-\d+\.').Index
        if (!$questions.Count) { throw "Brak pytań: $($file.Name)" }
        $updated = $body.Substring(0,$first) + $navigation + "`n`n" + $body.Substring($first)
    }
    if ($updated -cne $body) {
        $changed.Add($file.Name)
        if (!$Check) { [IO.File]::WriteAllText($file.FullName,$updated,[Text.UTF8Encoding]::new($false)) }
    }
    $index.Add('')
    $index.Add('## ' + $title)
    $index.Add('')
    foreach ($q in $questions) {
        $index.Add('- [' + $q.Id + '. ' + $q.Title + '](' + $file.Name + '#' + (Get-HeadingSlug ($q.Heading -replace '^## ','')) + ')')
    }
    $total += $questions.Count
}
$indexPath = Join-Path $moduleRoot 'INDEKS.md'
$indexBody = ($index -join "`n") + "`n"
if (!(Test-Path -LiteralPath $indexPath) -or [IO.File]::ReadAllText($indexPath).Replace("`r`n","`n") -cne $indexBody) {
    $changed.Add('INDEKS.md')
    if (!$Check) { [IO.File]::WriteAllText($indexPath,$indexBody,[Text.UTF8Encoding]::new($false)) }
}
if ($Check -and $changed.Count) { throw "Nieaktualna nawigacja: $($changed -join ', '). Uruchom scripts/update-navigation.ps1." }
Write-Output "navigation: $($files.Count) chapters, $total questions; changed=$($changed.Count) check=$Check"
