$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ExerciseTools.ps1')
$work = Join-Path ([IO.Path]::GetTempPath()) ('coding-exercises-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($work) | Out-Null
$generator = Join-Path $PSScriptRoot 'new-practice.ps1'; $checker = Join-Path $PSScriptRoot 'check-practice.ps1'
$fence = '```'
function Make-Fixture([string]$Id, [string]$Level, [bool]$Solved) {
    $implementation = if ($Solved) { 'return 7;' } else { 'throw new UnsupportedOperationException("TODO '+$Id+'");' }
    $hints = (1..3 | ForEach-Object { "<details>`n<summary>Wskazówka $_</summary>`n`nObserwacja $_.`n`n</details>" }) -join "`n`n"
    return "## $Id — kontrakt do samotestu`n`n**Poziom:** $Level`n**Wymagania:** brak.`n`n**Treść i kontrakt.** Zwróć siedem.`n`n$hints`n`nKontekst: szkielet.`n`n<!-- exercise:$Id -->`n${fence}java`nstatic int answer() { $implementation }`n$fence`n`nKontekst: testy.`n`n<!-- tests:$Id -->`n${fence}java`nstatic void verify() { assert answer()==7; }`n$fence`n"
}
function Expect-Failure([scriptblock]$Action) {
    $failed = $false; try { & $Action | Out-Null } catch { $failed = $true }
    if (!$failed) { throw 'Oczekiwano odrzucenia operacji.' }
}
try {
    $bank = Join-Path $work 'bank'; [IO.Directory]::CreateDirectory($bank) | Out-Null
    $alpha = Join-Path $bank '01-alpha.md'; $beta = Join-Path $bank '02-beta.md'
    $alphaText = (Make-Fixture 'FIT-01' 'L1' $false) + "`n" + (Make-Fixture 'FIT-02' 'L2' $false) + "`n" + (Make-Fixture 'FIT-03' 'L3' $true)
    [IO.File]::WriteAllText($alpha,$alphaText,[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($beta,(Make-Fixture 'FIT-04' 'L1' $false),[Text.UTF8Encoding]::new($false))
    $file = Join-Path $work 'filtered.md'; $exclusions = Join-Path $work 'exclude.txt'
    [IO.File]::WriteAllText($exclusions,"# znane zadanie`nFIT-01`n")
    & $generator -BankDirectory $bank -Count 1 -Level 'L1,L2' -Categories alpha -ExcludeFile $exclusions -OutputPath $file | Out-Null
    $copy = @(Get-Exercises -File $file)[0]
    if ((Get-PracticeOrigin $copy) -cne 'FIT-02') { throw 'Filtry poziomu, kategorii lub pliku wykluczeń nie zadziałały.' }
    & $checker -BankDirectory $bank -PracticeFile $file | Out-Null
    $before = [IO.File]::ReadAllText($file)
    & $generator -BankDirectory $bank -Ids FIT-02 -OutputPath $file -Force | Out-Null
    Expect-Failure { & $generator -BankDirectory $bank -Count 2 -Level L1 -Categories alpha -OutputPath (Join-Path $work 'too-many.md') }
    Expect-Failure { & $generator -BankDirectory $bank -Count 1 -Level L4 -OutputPath (Join-Path $work 'bad-level.md') }
    Expect-Failure { & $generator -BankDirectory $bank -Count 1 -Categories unknown -OutputPath (Join-Path $work 'bad-category.md') }
    Expect-Failure { & $generator -BankDirectory $bank -Ids FIT-01 -ExcludeIds FIT-01 -OutputPath (Join-Path $work 'conflict.md') }
    $only = Join-Path $work 'unsolved.md'
    & $generator -BankDirectory $bank -Count 3 -ExcludeSolved -OutputPath $only | Out-Null
    if (@(Get-Exercises -File $only | Where-Object { !$_.Pending }).Count) { throw 'Nie wykluczono rozwiązanych metod.' }
    $baseline = [IO.File]::ReadAllText($file)
    [IO.File]::WriteAllText($alpha,$alphaText.Replace('assert answer()==7','assert answer()==8'),[Text.UTF8Encoding]::new($false))
    Expect-Failure { & $checker -BankDirectory $bank -PracticeFile $file }
    if ([IO.File]::ReadAllText($file) -cne $baseline) { throw 'Kontrola rozjazdu zmieniła plik.' }
    [IO.File]::WriteAllText($alpha,$alphaText,[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($file,$baseline.Replace('assert answer()==7','assert answer()==8'),[Text.UTF8Encoding]::new($false))
    Expect-Failure { & $checker -BankDirectory $bank -PracticeFile $file }
    Expect-Failure { & $generator -BankDirectory $bank -Ids FIT-02 -OutputPath $file -Force }
    [IO.File]::WriteAllText($file,$baseline.Replace('throw new UnsupportedOperationException("TODO P01");','return 7;'),[Text.UTF8Encoding]::new($false))
    $solved = [IO.File]::ReadAllText($file)
    & $checker -BankDirectory $bank -PracticeFile $file | Out-Null
    Expect-Failure { & $generator -BankDirectory $bank -Ids FIT-02 -OutputPath $file -Force }
    if ([IO.File]::ReadAllText($file) -cne $solved) { throw 'Nadpisano rozwiązanie.' }
    Write-Output 'OK: filtry, wykluczenia, rozjazd źródła i kopii, kontrola tylko do odczytu, ochrona rozwiązania nawet przy -Force.'
} finally { Remove-ExerciseTemp $work }
