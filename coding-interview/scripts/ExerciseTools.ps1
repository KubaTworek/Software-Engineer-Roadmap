function Get-Exercises([string]$Directory, [string]$File) {
    $files = if ($File) { @(Get-Item -LiteralPath $File) } else {
        @(Get-ChildItem -LiteralPath $Directory -Filter '*.md' | Where-Object Name -Match '^\d{2}-' | Sort-Object Name)
    }
    $result = [Collections.Generic.List[object]]::new()
    foreach ($document in $files) {
        $body = [IO.File]::ReadAllText($document.FullName).Replace("`r`n", "`n")
        $sections = [regex]::Matches($body, '(?ms)^## (?<id>[A-Z]+-?\d{2,}) — (?<title>[^\n]+)\n(?<body>.*?)(?=^## |\z)')
        if (!$sections.Count -or [regex]::Matches($body, '<!-- (?:exercise|tests):').Count -ne 2 * $sections.Count) { throw "$($document.Name): niezgodna liczba zadań i oznaczeń." }
        $context = [regex]::Replace($body.Substring(0, $sections[0].Index), '(?m)^# .*\n|^\[Bank zadań.*\n', '').Trim()
        foreach ($section in $sections) {
            $id = $section.Groups['id'].Value; $content = $section.Groups['body'].Value
            $blocks = [regex]::Matches($content, '(?ms)^<!-- (?<kind>exercise|tests):(?<id>[A-Z]+-?\d{2,}) -->\n```java\n(?<code>.*?)^```[ \t]*(?=\n|\z)')
            if ($blocks.Count -ne 2 -or @($blocks | Where-Object { $_.Groups['id'].Value -cne $id }).Count) { throw "${id}: wymagany szkielet i testy o tym samym ID." }
            $stub = @($blocks | Where-Object { $_.Groups['kind'].Value -eq 'exercise' }); $tests = @($blocks | Where-Object { $_.Groups['kind'].Value -eq 'tests' })
            if ($stub.Count -ne 1 -or $tests.Count -ne 1) { throw "${id}: powtórzona rola bloku." }
            $testCode = $tests[0].Groups['code'].Value
            if ($testCode -notmatch 'static\s+void\s+verify\s*\(\s*\)' -or $testCode -notmatch '\bassert\s') { throw "${id}: brak verify() z asercjami." }
            if ($content -notmatch '\*\*Poziom:\*\* L[123]' -or $content -notmatch '\*\*Wymagania:\*\*') { throw "${id}: brak poziomu lub wymagań." }
            if (!$File -and [regex]::Matches($content, '<summary>Wskazówka [123]</summary>').Count -ne 3) { throw "${id}: wymagane trzy wskazówki." }
            $stubCode = $stub[0].Groups['code'].Value
            $stubLine = ($body.Substring(0, $section.Groups['body'].Index + $stub[0].Groups['code'].Index) -split "`n").Count
            $testsLine = ($body.Substring(0, $section.Groups['body'].Index + $tests[0].Groups['code'].Index) -split "`n").Count
            $result.Add([pscustomobject]@{
                Id = $id; Title = $section.Groups['title'].Value; Body = $content; File = $document.FullName; Context = $context
                Stub = $stubCode; Tests = $testCode; Code = $stubCode + "`n" + $testCode
                ClassName = 'Exercise_' + ($id -replace '-', '_'); Pending = $stubCode -match '\bTODO\b'
                StubLine = $stubLine; TestsLine = $testsLine
                Level = [regex]::Match($content, '\*\*Poziom:\*\* (L[123])').Groups[1].Value
                Category = ($document.BaseName -replace '^\d+-', '')
            })
        }
    }
    if (!$result.Count -or @($result | Group-Object Id | Where-Object Count -gt 1).Count) { throw 'Pusty bank lub powtórzone ID.' }
    return $result.ToArray()
}

function Get-TextHash([string]$Text) {
    $normalized = $Text.Replace("`r`n", "`n").TrimEnd()
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($normalized))).ToLowerInvariant()
}

function Get-ContractHash($Item) {
    return Get-TextHash ($Item.Title + "`n" + $Item.Context + "`n" + ($Item.Body -split '(?m)^Kontekst:', 2)[0])
}

function Get-PracticeContent($Item) {
    $contract = ($Item.Body -split '(?m)^Kontekst:', 2)[0]
    $contract = [regex]::Replace($contract, '(?s)<details>.*?</details>\s*', '')
    $contract = [regex]::Replace($contract, '(?m)^\*\*Wymagania:\*\*.*\n', '')
    $contract = $contract -replace 'To ćwiczenie po\s+odpowiedzi: samą monotoniczność musisz uzasadnić przed implementacją\.', ''
    $contract = $contract -replace 'Użyj Trie, koszt', 'Docelowy koszt' -replace 'przejścia po Trie', 'przetwarzania słownika'
    $content = "$($Item.Context)`n`n$($contract.Trim())`n`n**Wymagania:** samodzielne rozpoznanie techniki; źródło pod podpowiedzią."
    return [regex]::Replace($content.Trim(), '\n{3,}', "`n`n")
}

function Get-PracticeOrigin($Item) {
    $origin = [regex]::Match($Item.Body, '<!-- origin:([A-Z]+-\d{2}) -->')
    if ($origin.Success) { return $origin.Groups[1].Value }
    return [regex]::Match($Item.Body, '\[Oryginał ([A-Z]+-\d{2})\]').Groups[1].Value
}

function Get-PracticeSnapshot($Item) {
    $snapshot = [regex]::Match($Item.Body, '<!-- snapshot:contract=([0-9a-f]{64});tests=([0-9a-f]{64});stub=([0-9a-f]{64});rendered=([0-9a-f]{64}) -->')
    if (!$snapshot.Success) { return $null }
    return @{ Contract = $snapshot.Groups[1].Value; Tests = $snapshot.Groups[2].Value; Stub = $snapshot.Groups[3].Value; Rendered = $snapshot.Groups[4].Value }
}

function New-JavaSource([string]$Path, [string]$ClassName, [object[]]$Segments) {
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('import java.util.*;'); $lines.Add("class $ClassName {")
    $map = [Collections.Generic.List[object]]::new()
    foreach ($segment in $Segments) {
        $codeLines = $segment.Code.Replace("`r`n", "`n").TrimEnd("`n") -split "`n"
        $map.Add([pscustomobject]@{ Start = $lines.Count + 1; Count = $codeLines.Count; File = $segment.File; Line = $segment.Line })
        foreach ($line in $codeLines) { $lines.Add($line) }
    }
    $lines.Add('public static void main(String[] args) { boolean enabled = false; assert enabled = true; if (!enabled) throw new IllegalStateException("Wymagane -ea"); verify(); }')
    $lines.Add('}')
    [IO.File]::WriteAllText($Path, ($lines -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
    return [pscustomobject]@{ Path = $Path; Name = [IO.Path]::GetFileName($Path); Map = $map.ToArray() }
}

function Format-JavaDiagnostic([string]$Output, $Source) {
    $resolve = {
        param($match)
        $generated = [int]$match.Groups[1].Value
        foreach ($segment in $Source.Map) {
            if ($generated -ge $segment.Start -and $generated -lt $segment.Start + $segment.Count) {
                return "$($segment.File):$($segment.Line + $generated - $segment.Start)"
            }
        }
        return "$($Source.Map[0].File):$($Source.Map[0].Line) [otoczenie generowane]"
    }
    $Output = [regex]::Replace($Output, [regex]::Escape($Source.Path) + ':(\d+)', $resolve)
    return [regex]::Replace($Output, [regex]::Escape($Source.Name) + ':(\d+)', $resolve)
}

function Invoke-JdkTool([string]$Executable, [string[]]$ToolArguments) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Executable; $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    foreach ($argument in $ToolArguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    try {
        if (!$process.Start()) { throw "Nie można uruchomić $Executable" }
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit(45000)) { $process.Kill($true); $process.WaitForExit(); throw "Timeout 45 s: $Executable" }
        return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult() }
    } finally { $process.Dispose() }
}

function Remove-ExerciseTemp([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ([IO.Path]::GetDirectoryName($resolved) -ne $tempRoot -or [IO.Path]::GetFileName($resolved) -notmatch '^coding-exercises-[0-9a-f]{32}$') { throw "Niebezpieczna ścieżka sprzątania: $resolved" }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
