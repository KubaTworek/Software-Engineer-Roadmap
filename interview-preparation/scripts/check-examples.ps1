param(
    [string]$JdkHome = $env:JAVA_HOME,
    [switch]$DocumentsOnly
)
$ErrorActionPreference = 'Stop'
$moduleRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $moduleRoot 'checks/DocumentTools.ps1')
& (Join-Path $moduleRoot 'checks/DocumentToolsTests.ps1')
& (Join-Path $PSScriptRoot 'update-navigation.ps1') -Check

$chapters = @(Get-ChildItem -LiteralPath $moduleRoot -Filter '*.md' | Where-Object Name -Match '^\d' | Sort-Object Name)
$questions = [Collections.Generic.List[string]]::new()
$examples = [Collections.Generic.List[object]]::new()
$followups = 0
foreach ($file in (Get-ChildItem -LiteralPath $moduleRoot -Filter '*.md')) {
    $body = [IO.File]::ReadAllText($file.FullName).Replace("`r`n","`n")
    if ($body -match '[\uFFFD]' -or $body -match '(?m)^(<<<<<<<|=======|>>>>>>>)' -or $body -match '(?m)[\t ]+$') { throw "Błąd tekstu/formatowania: $($file.Name)" }
    $sections = @(Get-QuestionSections $body)
    if ($file.Name -match '^\d') {
        for ($i=0; $i -lt $sections.Count; $i++) {
            $id=$sections[$i].Id
            if ([int]($id -split '-')[-1] -ne ($i+1)) { throw "Nieciągła numeracja: $id" }
            $questions.Add($id)
        }
        $followups += [regex]::Matches($body,'<!-- follow-up:[A-Z]+-\d+ -->').Count
        if ($body -notmatch '<!-- labs:start -->') { throw "Brak bezpośrednich odsyłaczy do laboratoriów: $($file.Name)" }
    }
    $found = @(Get-DocumentExamples $body $file.Name)
    $markers = [regex]::Matches($body,'<!-- example:').Count
    if ($markers -ne $found.Count) { throw "Osieroczone oznaczenie przykładu: $($file.Name)" }
    foreach ($example in $found) {
        $owner = $example.Id -replace '-\d+$',''
        if ($owner -notin $sections.Id) { throw "Przykład poza odpowiedzią $owner w $($file.Name)" }
        if ($example.Kind -eq 'json-contract') { $null = ConvertFrom-Json -InputObject $example.Code -ErrorAction Stop }
        $examples.Add($example)
    }
}
if (@($questions | Group-Object | Where-Object Count -gt 1).Count) { throw 'Powtórzony identyfikator pytania' }
if (@($examples | Group-Object Id | Where-Object Count -gt 1).Count) { throw 'Powtórzony identyfikator przykładu' }
$readme=[IO.File]::ReadAllText((Join-Path $moduleRoot 'README.md'))
$counts=[regex]::Match($readme,'\*\*(\d+) pytań i odpowiedzi w (\d+) rozdziałach\*\*')
if (!$counts.Success -or [int]$counts.Groups[1].Value -ne $questions.Count -or [int]$counts.Groups[2].Value -ne $chapters.Count) { throw 'Nieaktualna liczba pytań/rozdziałów w README' }
$compiled=@($examples | Where-Object Kind -In @('java-members','java-statements','java-members-negative'))
Write-Output "documents: $($questions.Count) questions, $($examples.Count) contextual examples, $followups follow-ups; OK"
foreach ($group in ($examples | Group-Object Kind | Sort-Object Name)) { Write-Output "  $($group.Name): $($group.Count)" }
if ($DocumentsOnly) {
    Write-Output 'Java: pominięta jawnie przez -DocumentsOnly; nie wykonano kompilacji ani testów zachowania.'
    return
}

function Invoke-JdkTool([string]$Executable, [string[]]$ToolArguments, [int]$TimeoutSeconds=45) {
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=$Executable
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    foreach ($argument in $ToolArguments) { $start.ArgumentList.Add($argument) }
    $process=[Diagnostics.Process]::new()
    $process.StartInfo=$start
    try {
        if (!$process.Start()) { throw "Nie można uruchomić $Executable" }
        $stdout=$process.StandardOutput.ReadToEndAsync()
        $stderr=$process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit($TimeoutSeconds*1000)) {
            $process.Kill($true)
            $process.WaitForExit()
            throw "Przekroczono czas $TimeoutSeconds s: $Executable"
        }
        $output=$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult()
        if ($output.Trim()) { Write-Output $output.TrimEnd() }
        if ($process.ExitCode -ne 0) { throw "$Executable zakończył się kodem $($process.ExitCode)" }
    } finally { $process.Dispose() }
}

$javaName=if ($IsWindows) { 'java.exe' } else { 'java' }
$javacName=if ($IsWindows) { 'javac.exe' } else { 'javac' }
if ($JdkHome) {
    $java=Join-Path $JdkHome "bin/$javaName"
    $javac=Join-Path $JdkHome "bin/$javacName"
} else {
    $java=(Get-Command $javaName -ErrorAction Stop).Source
    $javac=(Get-Command $javacName -ErrorAction Stop).Source
}
if (!(Test-Path -LiteralPath $java) -or !(Test-Path -LiteralPath $javac)) { throw 'Potrzebny jest JDK 21+; ustaw JAVA_HOME lub podaj -JdkHome.' }
Invoke-JdkTool $java @('-version')

$tempRoot=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$work=[IO.Path]::GetFullPath((Join-Path $tempRoot ('interview-examples-'+[guid]::NewGuid().ToString('N'))))
[IO.Directory]::CreateDirectory($work) | Out-Null
try {
    $sources=[Collections.Generic.List[string]]::new()
    foreach ($example in $compiled) {
        $parts=$example.Id -split '-'
        $className='Snippet_'+$parts[0]+'_'+$parts[1]
        if ([int]$parts[2] -gt 1) { $className+='_'+$parts[2] }
        $inside=if ($example.Kind -eq 'java-statements') { "static void example() throws Exception {`n$($example.Code)`n}" } else { $example.Code }
        $source=Join-Path $work "$className.java"
        [IO.File]::WriteAllText($source,"class $className {`n$inside`n}`n",[Text.UTF8Encoding]::new($false))
        $sources.Add($source)
    }
    $sources.Add((Join-Path $moduleRoot 'checks/InterviewSmoke.java'))
    $argFile=Join-Path $work 'javac-args.txt'
    $arguments=@('--release','21','-encoding','UTF-8','-d',('"'+$work.Replace('\','/')+'"')) + @($sources | ForEach-Object { '"'+$_.Replace('\','/')+'"' })
    [IO.File]::WriteAllText($argFile,($arguments -join "`n"),[Text.UTF8Encoding]::new($false))
    Invoke-JdkTool $javac @("@$argFile")
    Write-Output "Java: $($compiled.Count) fragmentów wyodrębnionych bezpośrednio z Markdown skompilowano z --release 21."
    Invoke-JdkTool $java @('-ea','-cp',$work,'InterviewSmoke')
    Write-Output 'Szkice Spring/JPA/JDBC/SQL, polecenia i wdrożenia: jawnie poza wykonaniem tego skryptu.'
} finally {
    # Usuwamy tylko utworzony przez to wywołanie katalog roboczy w systemowym TEMP.
    $comparison=if ($IsWindows) { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
    $parent=[IO.Path]::GetDirectoryName($work).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ($parent.Equals($tempRoot.TrimEnd([IO.Path]::DirectorySeparatorChar),$comparison) -and [IO.Path]::GetFileName($work) -match '^interview-examples-[0-9a-f]{32}$') {
        Remove-Item -LiteralPath $work -Recurse -Force
    } else { throw "Odmowa usunięcia nieoczekiwanej ścieżki: $work" }
}
