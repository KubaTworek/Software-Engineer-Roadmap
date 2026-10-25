param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ExerciseTools.ps1')
$work = Join-Path ([IO.Path]::GetTempPath()) ('coding-exercises-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($work) | Out-Null
$fence = '```'; $shell = Join-Path $PSHOME $(if ($IsWindows) { 'pwsh.exe' } else { 'pwsh' })
try {
    $bank = Join-Path $work 'bank'; $references = Join-Path $work 'references'
    [IO.Directory]::CreateDirectory($bank) | Out-Null; [IO.Directory]::CreateDirectory($references) | Out-Null
    $hints = (1..3 | ForEach-Object { "<details><summary>Wskazówka $_</summary>Wskazówka.</details>" }) -join "`n"
    $body = "## FIT-01 — sztuczny kontrakt`n`n**Poziom:** L1`n**Wymagania:** brak.`n$hints`n`n<!-- exercise:FIT-01 -->`n${fence}java`nstatic int answer() { throw new UnsupportedOperationException(`"TODO FIT-01`"); }`n$fence`n`n<!-- tests:FIT-01 -->`n${fence}java`nstatic void verify() { assert answer()==7; }`n$fence`n"
    [IO.File]::WriteAllText((Join-Path $bank '01-fixture.md'),$body,[Text.UTF8Encoding]::new($false))
    $checker = Join-Path $PSScriptRoot 'check-test-quality.ps1'
    foreach ($case in @(
        @{ Good = 'return 7;'; Bad = 'return 8;'; Success = $true; Pattern = 'REFERENCES=1/1 KILLED=1 SURVIVED=0 ERRORS=0' },
        @{ Good = 'return 7;'; Bad = 'return 7;'; Success = $false; Pattern = 'REFERENCES=1/1 KILLED=0 SURVIVED=1 ERRORS=0' },
        @{ Good = 'return 7;'; Bad = 'return ;'; Success = $false; Pattern = 'REFERENCES=1/1 KILLED=0 SURVIVED=0 ERRORS=1' },
        @{ Good = 'return 8;'; Bad = 'return 9;'; Success = $false; Pattern = 'REFERENCES=0/1 KILLED=0 SURVIVED=0 ERRORS=1' }
    )) {
        $reference = "<!-- reference:FIT-01 -->`n${fence}java`nstatic int answer() { $($case.Good) }`n$fence`n`n<!-- mutant:FIT-01:wariant -->`n${fence}java`nstatic int answer() { $($case.Bad) }`n$fence`n"
        [IO.File]::WriteAllText((Join-Path $references 'fixture.md'),$reference,[Text.UTF8Encoding]::new($false))
        $run = Invoke-JdkTool $shell @('-NoProfile','-File',$checker,'-JdkHome',$JdkHome,'-BankDirectory',$bank,'-VerificationDirectory',$references,'-DebugDirectory',(Join-Path $work 'none'))
        if (($run.ExitCode -eq 0) -ne $case.Success -or $run.Output -notmatch $case.Pattern) { throw "Niepoprawna ocena jakości testów`n$($run.Output)" }
        Write-Output "OK: $($case.Pattern)"
    }
} finally { Remove-ExerciseTemp $work }
