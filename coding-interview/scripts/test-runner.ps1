param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ExerciseTools.ps1')
$work = Join-Path ([IO.Path]::GetTempPath()) ('coding-exercises-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($work) | Out-Null
$shell = Join-Path $PSHOME $(if ($IsWindows) { 'pwsh.exe' } else { 'pwsh' })
$runner = Join-Path $PSScriptRoot 'check-exercises.ps1'
$fence = '```'
function Fixture([string]$Id, [string]$Implementation) {
    return "## $Id — sztuczny kontrakt testowy`n`n**Poziom:** L1`n**Wymagania:** brak.`n`n<!-- exercise:$Id -->`n${fence}java`nstatic int answer() { $Implementation }`n$fence`n`n<!-- tests:$Id -->`n${fence}java`nstatic void verify() { assert answer() == 7; }`n$fence`n"
}
function Expect([string[]]$Mode, [bool]$Success, [string]$Pattern) {
    $arguments = @('-NoProfile', '-File', $runner, '-JdkHome', $JdkHome, '-PracticeFile', $fixture) + $Mode
    $result = Invoke-JdkTool $shell $arguments
    $script:runnerLastOutput = $result.Output
    if (($result.ExitCode -eq 0) -ne $Success -or $result.Output -notmatch $Pattern) { throw "Błędny wynik trybu $Mode`n$($result.Output)" }
    Write-Output "OK: $($Mode -join ' ') / $Pattern"
}
try {
    $fixture = Join-Path $work 'fixture.md'
    $base = (Fixture 'P01' 'return 7;') + "`n" + (Fixture 'P02' 'return 8;') + "`n" + (Fixture 'P03' 'throw new UnsupportedOperationException("TODO P03");')
    [IO.File]::WriteAllText($fixture, $base, [Text.UTF8Encoding]::new($false))
    Expect @() $true 'PASS=0 FAIL=0 TODO=1 COMPILED=2'
    Expect @('-RunSolved') $false 'PASS=1 FAIL=1 TODO=1 COMPILED=0'
    Expect @('-RunAll') $false 'PASS=1 FAIL=1 TODO=1 COMPILED=0'
    Expect @('-Exercise', 'P01') $true 'PASS=1 FAIL=0 TODO=0 COMPILED=0'
    Expect @('-Exercise', 'P02') $false 'AssertionError'
    $item = @(Get-Exercises -File $fixture | Where-Object Id -EQ 'P02')[0]
    if ($script:runnerLastOutput -notmatch ([regex]::Escape("verify($fixture`:$($item.TestsLine))"))) { throw 'Błędna linia Markdown dla asercji.' }
    Expect @('-Exercise', 'P03') $false 'PASS=0 FAIL=0 TODO=1 COMPILED=0'
    Expect @('-Exercise', 'UNKNOWN') $false 'Nieznane zadanie'
    [IO.File]::WriteAllText($fixture, $base + "`n" + (Fixture 'P04' 'return ;'), [Text.UTF8Encoding]::new($false))
    Expect @('-Exercise', 'P01') $true 'PASS=1 FAIL=0 TODO=0 COMPILED=0'
    Expect @() $false 'PASS=0 FAIL=1 TODO=1 COMPILED=2'
    Expect @('-Exercise', 'P04') $false 'FAIL=1'
    $item = @(Get-Exercises -File $fixture | Where-Object Id -EQ 'P04')[0]
    if ($script:runnerLastOutput -notmatch ([regex]::Escape("$fixture`:$($item.StubLine)"))) { throw 'Błędna linia Markdown dla kompilacji.' }
    [IO.File]::WriteAllText($fixture, $base + "`n" + (Fixture 'P01' 'return 7;'), [Text.UTF8Encoding]::new($false))
    Expect @() $false 'Pusty bank'
    $generator = Join-Path $PSScriptRoot 'new-practice.ps1'
    $practice = Join-Path $work 'practice.md'
    & $generator -Count 3 -Seed 123 -OutputPath $practice | Out-Null
    $first = [IO.File]::ReadAllText($practice)
    & $generator -Count 3 -Seed 123 -OutputPath $practice -Force | Out-Null
    if ([IO.File]::ReadAllText($practice) -cne $first) { throw 'Generator nie jest powtarzalny.' }
    $visible = [regex]::Replace($first, '(?s)<details>.*?</details>', '')
    if ($visible -cmatch '[A-Z]+-\d{2}') { throw 'Kategoria wyciekła przez identyfikator do jawnej części treningu.' }
    $mixed = @(Get-Exercises -File $practice)
    if ($mixed.Count -ne 3 -or @($mixed | Where-Object { !$_.Pending }).Count) { throw 'Błędny szkielet treningu.' }
    $rejected = $false
    try { & $generator -Count 1 -OutputPath $practice | Out-Null } catch { $rejected = $true }
    if (!$rejected) { throw 'Generator nadpisał istniejącą pracę bez -Force.' }
    Write-Output 'OK: generator powtarzalny, neutralne ID, ochrona przed nadpisaniem.'
} finally { Remove-ExerciseTemp $work }
