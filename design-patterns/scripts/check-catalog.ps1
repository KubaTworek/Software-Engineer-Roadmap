param([string]$ModuleRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($ModuleRoot)
$problems = [Collections.Generic.List[string]]::new()
$index = [IO.File]::ReadAllText((Join-Path $root 'INDEKS.md'))
$readme = [IO.File]::ReadAllText((Join-Path $root 'README.md'))
$chapters = @(Get-ChildItem (Join-Path $root 'catalog') -Directory | Sort-Object Name)
$total = 0
$expectedLinks = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$gof = @{
    '01-creational' = @('Factory Method', 'Abstract Factory', 'Builder', 'Prototype', 'Singleton')
    '02-structural' = @('Adapter', 'Bridge', 'Composite', 'Decorator', 'Facade', 'Flyweight', 'Proxy')
    '03-behavioral' = @('Chain of Responsibility', 'Command', 'Interpreter', 'Iterator', 'Mediator', 'Memento', 'Observer', 'State', 'Strategy', 'Template Method', 'Visitor')
}
foreach ($chapter in $chapters) {
    $path = Join-Path $chapter.FullName 'README.md'
    if (!(Test-Path -LiteralPath $path)) { $problems.Add("Missing category README: $($chapter.Name)"); continue }
    $content = [IO.File]::ReadAllText($path)
    $cards = @([regex]::Matches($content, '(?ms)^## (?!Spis kart\s*$)([^\r\n]+)\r?\n(.*?)(?=^## |\z)'))
    $total += $cards.Count
    if ($content -notmatch ('\*\*' + $cards.Count + ' kart\.\*\*')) { $problems.Add("Wrong category count: $($chapter.Name)") }
    if ($content -notmatch '\*\*Status przykładów:\*\*') { $problems.Add("Missing example status: $($chapter.Name)") }
    if (!$readme.Contains("catalog/$($chapter.Name)/README.md")) { $problems.Add("Missing category in README: $($chapter.Name)") }
    foreach ($card in $cards) {
        $name = $card.Groups[1].Value.Trim()
        foreach ($field in @('Intuicja', 'Problem i mechanizm', 'Role i współpraca', 'Przykład', 'Mechanizm w kodzie', 'Konkretny przypadek użycia', 'Kiedy zastosować', 'Koszt i pułapka', 'Rozwinięcie i ślad wykonania', 'Ćwiczenie sprawdzające')) {
            if ($card.Groups[2].Value -notmatch ('\*\*' + [regex]::Escape($field) + ':\*\*\s+\S')) {
                $problems.Add("Missing field '$field': $($chapter.Name)/$name")
            }
        }
        foreach ($caseField in @('Wejście', 'Przebieg', 'Oczekiwany wynik', 'Błąd lub granica')) {
            $pattern = '(?m)^- \*\*' + [regex]::Escape($caseField) + ':\*\*\s+\S'
            if ($card.Groups[2].Value -notmatch $pattern) {
                $problems.Add("Missing use-case field '$caseField': $($chapter.Name)/$name")
            }
        }
        if ($card.Groups[2].Value -notmatch '(?m)^```(?:java|sql|http|yaml|text)\s*$') {
            $problems.Add("Missing inline code example: $($chapter.Name)/$name")
        }
        $slug = $name.ToLowerInvariant() -replace '[^\p{L}\p{N}_\-\s]', '' -replace '\s', '-'
        $link = "catalog/$($chapter.Name)/README.md#$slug"
        if (!$expectedLinks.Add($link)) { $problems.Add("Duplicate card: $link") }
    }
    if ($gof.ContainsKey($chapter.Name)) {
        $actual = @($cards | ForEach-Object { $_.Groups[1].Value.Trim() } | Sort-Object)
        if (@(Compare-Object @($gof[$chapter.Name] | Sort-Object) $actual).Count) { $problems.Add("Incomplete GoF category: $($chapter.Name)") }
    }
}
foreach ($required in $gof.Keys) {
    if ($required -notin $chapters.Name) { $problems.Add("Missing GoF category: $required") }
}
$indexLinks = @([regex]::Matches($index, '\]\((catalog/[^)]+)\)') | ForEach-Object { $_.Groups[1].Value })
if ($indexLinks.Count -ne $expectedLinks.Count) { $problems.Add('Index card count mismatch') }
foreach ($link in $indexLinks) {
    if (!$expectedLinks.Remove($link)) { $problems.Add("Duplicate or unknown index link: $link") }
}
foreach ($missing in $expectedLinks) { $problems.Add("Missing index link: $missing") }
if ($readme -notmatch ('\*\*' + $total + ' kartami w ' + $chapters.Count + ' kategoriach\*\*')) { $problems.Add('README total mismatch') }
if ($index -notmatch ('\*\*' + $total + ' kart w ' + $chapters.Count + ' kategoriach\*\*')) { $problems.Add('Index total mismatch') }
foreach ($obsolete in @('src', 'pom.xml', 'mvnw', 'mvnw.cmd', '.mvn')) {
    if (Test-Path -LiteralPath (Join-Path $root $obsolete)) {
        $problems.Add("Documentation module contains obsolete application structure: $obsolete")
    }
}
foreach ($file in (Get-ChildItem -LiteralPath $root -Recurse -File)) {
    if ($file.Extension -in @('.java', '.class', '.jar')) {
        $problems.Add("Documentation module contains Java source or build artifact: $($file.FullName)")
    }
    if ($file.Extension -eq '.md') {
        $body = [IO.File]::ReadAllText($file.FullName)
        if ($body -match '\]\([^)]*\.java(?:#[^)]*)?\)' -or $body -match 'sync-gof-examples\.ps1') {
            $problems.Add("Stale source link or synchronization command: $($file.FullName)")
        }
        if ($body -match '(?m)[ \t]+\r?$') {
            $problems.Add("Trailing whitespace: $($file.FullName)")
        }
    }
}
if ($problems.Count) { $problems | ForEach-Object { Write-Output $_ }; throw "$($problems.Count) catalog errors" }
Write-Output "catalog: $total cards and concrete use cases, $($chapters.Count) categories, 23 GoF, inline code, complete index, documentation-only structure; OK"
