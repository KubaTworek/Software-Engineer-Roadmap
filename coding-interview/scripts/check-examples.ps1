param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ExerciseTools.ps1')
$moduleRoot = Split-Path -Parent $PSScriptRoot
$cards = @(Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'patterns') -Filter '*.md' | Sort-Object Name)
if (!$cards.Count) { throw 'Brak kart algorytmicznych.' }
$javaName = if ($IsWindows) { 'java.exe' } else { 'java' }; $javacName = if ($IsWindows) { 'javac.exe' } else { 'javac' }
$java = if ($JdkHome) { Join-Path $JdkHome "bin/$javaName" } else { (Get-Command $javaName -ErrorAction Stop).Source }
$javac = if ($JdkHome) { Join-Path $JdkHome "bin/$javacName" } else { (Get-Command $javacName -ErrorAction Stop).Source }
$work = Join-Path ([IO.Path]::GetTempPath()) ('coding-exercises-' + [guid]::NewGuid().ToString('N')); [IO.Directory]::CreateDirectory($work) | Out-Null
try {
    $sources = [Collections.Generic.List[string]]::new(); $maps = [Collections.Generic.List[object]]::new(); $classes = [Collections.Generic.List[string]]::new()
    foreach ($card in $cards) {
        $body = [IO.File]::ReadAllText($card.FullName).Replace("`r`n","`n")
        $blocks = [regex]::Matches($body,'(?ms)^```java\n(.*?)^```[ \t]*(?=\n|\z)')
        if ($blocks.Count -ne 2) { throw "$($card.Name): wymagane dwa bloki java." }
        foreach ($heading in @('Problem i kontrakt','Od brute force do niezmiennika','Przykład krok po kroku','Kod Java','Koszt i granice techniki','Kontrprzykład i scenariusze','Ćwiczenie')) {
            if (!$body.Contains("## $heading")) { throw "$($card.Name): brak sekcji $heading" }
        }
        $className = 'Snippet_' + ($card.BaseName -replace '[^a-zA-Z0-9_]','_')
        $source = Join-Path $work "$className.java"
        $segments = @($blocks | ForEach-Object { @{ Code = $_.Groups[1].Value; File = $card.FullName; Line = ($body.Substring(0,$_.Groups[1].Index) -split "`n").Count } })
        $maps.Add((New-JavaSource $source $className $segments)); $sources.Add($source); $classes.Add($className)
    }
    $argFile = Join-Path $work 'javac-args.txt'
    $arguments = @('--release','21','-encoding','UTF-8','-d',('"'+$work.Replace('\','/')+'"')) + @($sources | ForEach-Object { '"'+$_.Replace('\','/')+'"' })
    [IO.File]::WriteAllText($argFile,($arguments -join "`n"),[Text.UTF8Encoding]::new($false))
    $compiled = Invoke-JdkTool $javac @("@$argFile")
    if ($compiled.ExitCode -ne 0) {
        $output = $compiled.Output; foreach ($map in $maps) { $output = Format-JavaDiagnostic $output $map }
        Write-Output $output; throw 'Kompilacja kart nie przeszła.'
    }
    for ($i=0; $i -lt $classes.Count; $i++) {
        $run = Invoke-JdkTool $java @('-ea','-cp',$work,$classes[$i])
        if ($run.ExitCode -ne 0) { Write-Output (Format-JavaDiagnostic $run.Output $maps[$i]); throw "Testy $($classes[$i]) nie przeszły." }
        Write-Output "$($classes[$i]) OK"
    }
    $forbidden = @(Get-ChildItem -LiteralPath $moduleRoot -Recurse -File | Where-Object { $_.Extension -in @('.java','.class','.jar') -or $_.Name -in @('pom.xml','build.gradle','build.gradle.kts') })
    if ($forbidden.Count) { throw 'Źródła lub artefakty aplikacji w module dokumentacyjnym.' }
    Write-Output "OK: $($cards.Count) kart, kompilacja Java 21 i wszystkie verify() z asercjami."
} finally { Remove-ExerciseTemp $work }
