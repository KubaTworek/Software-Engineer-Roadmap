param(
    [string]$JdkHome = $env:JAVA_HOME, [string]$Exercise,
    [string]$BankDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'exercises'),
    [string]$VerificationDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'verification'),
    [string]$DebugDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'debugging')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ExerciseTools.ps1')
$moduleRoot = Split-Path -Parent $PSScriptRoot
$bank = @(Get-Exercises -Directory $BankDirectory)
if (Test-Path -LiteralPath $DebugDirectory) {
    foreach ($file in Get-ChildItem $DebugDirectory -Filter '*-exercises.md') { $bank += @(Get-Exercises -File $file.FullName) }
}
$blocks = [Collections.Generic.List[object]]::new()
foreach ($file in Get-ChildItem $VerificationDirectory -Filter '*.md') {
    $body = [IO.File]::ReadAllText($file.FullName).Replace("`r`n", "`n")
    foreach ($match in [regex]::Matches($body, '(?ms)^<!-- (?<kind>reference|mutant):(?<id>[A-Z]+-\d{2})(?::(?<name>[a-z0-9-]+))? -->\n```java\n(?<code>.*?)^```[ \t]*(?=\n|\z)')) {
        $blocks.Add([pscustomobject]@{ Kind = $match.Groups['kind'].Value; Id = $match.Groups['id'].Value; Name = $match.Groups['name'].Value; Code = $match.Groups['code'].Value; File = $file.FullName; Line = ($body.Substring(0,$match.Groups['code'].Index) -split "`n").Count })
    }
}
$references = @($blocks | Where-Object Kind -EQ 'reference')
if (@($blocks | Where-Object { $_.Id -cnotin $bank.Id }).Count -or @($blocks | Group-Object Kind,Id,Name | Where-Object Count -gt 1).Count) { throw 'Nieznany lub powtórzony blok kontroli testów.' }
if (@($references | Group-Object Id | Where-Object Count -ne 1).Count -or @($bank.Id | Where-Object { $_ -cnotin $references.Id }).Count -or @($references.Id | Where-Object { $_ -cnotin $bank.Id }).Count) { throw 'Referencje muszą pokrywać dokładnie cały bank i zadania diagnostyczne.' }
if ($Exercise -and $Exercise -cnotin $bank.Id) { throw "Nieznane zadanie $Exercise" }
$selected = if ($Exercise) { @($bank | Where-Object Id -CEQ $Exercise) } else { $bank }
$javaName = if ($IsWindows) { 'java.exe' } else { 'java' }; $javacName = if ($IsWindows) { 'javac.exe' } else { 'javac' }
$java = if ($JdkHome) { Join-Path $JdkHome "bin/$javaName" } else { (Get-Command $javaName -ErrorAction Stop).Source }
$javac = if ($JdkHome) { Join-Path $JdkHome "bin/$javacName" } else { (Get-Command $javacName -ErrorAction Stop).Source }
$work = Join-Path ([IO.Path]::GetTempPath()) ('coding-exercises-' + [guid]::NewGuid().ToString('N')); [IO.Directory]::CreateDirectory($work) | Out-Null
$passed = 0; $killed = 0; $survived = 0; $errors = 0
try {
    foreach ($item in $selected) {
        $cases = @($blocks | Where-Object Id -CEQ $item.Id | Sort-Object { if ($_.Kind -eq 'reference') { 0 } else { 1 } })
        $referencePassed = $false
        foreach ($case in $cases) {
            if ($case.Kind -eq 'mutant' -and !$referencePassed) { continue }
            $source = Join-Path $work "$($item.ClassName).java"
            $sourceInfo = New-JavaSource $source $item.ClassName @(
                @{ Code = $case.Code; File = $case.File; Line = $case.Line },
                @{ Code = $item.Tests; File = $item.File; Line = $item.TestsLine }
            )
            $compiled = Invoke-JdkTool $javac @('--release','21','-encoding','UTF-8','-d',$work,$source)
            if ($compiled.ExitCode -ne 0) { $errors++; Write-Output "$($item.Id) $($case.Kind) COMPILE ERROR`n$(Format-JavaDiagnostic $compiled.Output $sourceInfo)"; continue }
            $run = Invoke-JdkTool $java @('-ea','-cp',$work,$item.ClassName)
            if ($case.Kind -eq 'reference') {
                if ($run.ExitCode -eq 0) { $passed++; $referencePassed = $true; Write-Output "$($item.Id) REFERENCE PASS" }
                else { $errors++; Write-Output "$($item.Id) REFERENCE FAIL`n$(Format-JavaDiagnostic $run.Output $sourceInfo)" }
            } elseif ($run.ExitCode -ne 0 -and $run.Output -match 'java.lang.AssertionError') {
                $killed++; Write-Output "$($item.Id) MUTANT KILLED: $($case.Name)"
            } elseif ($run.ExitCode -eq 0) { $survived++; Write-Output "$($item.Id) MUTANT SURVIVED: $($case.Name)" }
            else { $errors++; Write-Output "$($item.Id) MUTANT ERROR (nie asercja)`n$(Format-JavaDiagnostic $run.Output $sourceInfo)" }
        }
    }
    Write-Output "Test quality: REFERENCES=$passed/$($selected.Count) KILLED=$killed SURVIVED=$survived ERRORS=$errors"
    if ($errors -or $survived -or $passed -ne $selected.Count) { throw 'Kontrola jakości testów nie przeszła.' }
} finally { Remove-ExerciseTemp $work }
