param([string]$JdkHome = $env:JAVA_HOME, [string]$Exercise, [switch]$RunAll, [switch]$RunSolved, [string]$PracticeFile)
$ErrorActionPreference = 'Stop'
if (@(@($Exercise, $RunAll, $RunSolved) | Where-Object { $_ }).Count -gt 1) { throw 'Wybierz jeden tryb: -Exercise, -RunAll lub -RunSolved.' }
. (Join-Path $PSScriptRoot 'ExerciseTools.ps1')
$moduleRoot = Split-Path -Parent $PSScriptRoot
$exercises = @(Get-Exercises -Directory (Join-Path $moduleRoot 'exercises') -File $PracticeFile)
if ($Exercise -and $Exercise -cnotin $exercises.Id) { throw "Nieznane zadanie '$Exercise'." }
$selected = if ($Exercise) { @($exercises | Where-Object Id -CEQ $Exercise) } else { $exercises }
$javaName = if ($IsWindows) { 'java.exe' } else { 'java' }
$javacName = if ($IsWindows) { 'javac.exe' } else { 'javac' }
$java = if ($JdkHome) { Join-Path $JdkHome "bin/$javaName" } else { (Get-Command $javaName -ErrorAction Stop).Source }
$javac = if ($JdkHome) { Join-Path $JdkHome "bin/$javacName" } else { (Get-Command $javacName -ErrorAction Stop).Source }
$work = Join-Path ([IO.Path]::GetTempPath()) ('coding-exercises-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($work) | Out-Null
$report = [Collections.Generic.List[object]]::new()
try {
    foreach ($item in $selected) {
        $run = [bool]($Exercise -or $RunAll -or ($RunSolved -and !$item.Pending))
        $source = Join-Path $work "$($item.ClassName).java"
        $sourceInfo = New-JavaSource $source $item.ClassName @(
            @{ Code = $item.Stub; File = $item.File; Line = $item.StubLine },
            @{ Code = $item.Tests; File = $item.File; Line = $item.TestsLine }
        )
        $compiled = Invoke-JdkTool $javac @('--release', '21', '-encoding', 'UTF-8', '-d', $work, $source)
        if ($compiled.ExitCode -ne 0) {
            $report.Add([pscustomobject]@{ Id = $item.Id; Status = 'FAIL' })
            Write-Output "$($item.Id) FAIL (kompilacja)`n$(Format-JavaDiagnostic $compiled.Output $sourceInfo)"
            continue
        }
        if ($run) {
            $executed = Invoke-JdkTool $java @('-ea', '-cp', $work, $item.ClassName)
            if ($executed.ExitCode -ne 0) {
                $status = if ($item.Pending -and $executed.Output -match 'UnsupportedOperationException: TODO') { 'TODO' } else { 'FAIL' }
                $report.Add([pscustomobject]@{ Id = $item.Id; Status = $status })
                Write-Output "$($item.Id) $status`n$(Format-JavaDiagnostic $executed.Output $sourceInfo)"
            } else {
                $report.Add([pscustomobject]@{ Id = $item.Id; Status = 'PASS' })
                Write-Output "$($item.Id) PASS"
            }
        } else {
            $status = if ($item.Pending) { 'TODO' } else { 'COMPILED' }
            $report.Add([pscustomobject]@{ Id = $item.Id; Status = $status })
            Write-Output "$($item.Id) $status (kompilacja OK; testów nie wykonano)"
        }
    }
    $counts = @{}
    foreach ($status in @('PASS', 'FAIL', 'TODO', 'COMPILED')) { $counts[$status] = @($report | Where-Object Status -EQ $status).Count }
    Write-Output "Raport: PASS=$($counts.PASS) FAIL=$($counts.FAIL) TODO=$($counts.TODO) COMPILED=$($counts.COMPILED)"
    if ($counts.FAIL -or (($RunAll -or $Exercise) -and $counts.TODO)) { throw 'Nie wszystkie żądane testy przeszły; szczegóły w raporcie.' }
    if ($RunSolved -and !$counts.PASS) { Write-Output 'Nie wykonano testów zachowania: brak metod bez TODO.' }
} finally { Remove-ExerciseTemp $work }
