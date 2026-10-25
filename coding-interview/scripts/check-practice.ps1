param([string]$PracticeFile, [string]$BankDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'exercises'))
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ExerciseTools.ps1')
$moduleRoot = Split-Path -Parent $PSScriptRoot
$bank = @(Get-Exercises -Directory $BankDirectory)
$files = if ($PracticeFile) { @(Get-Item -LiteralPath $PracticeFile) } else { @(Get-ChildItem (Join-Path $moduleRoot 'practice') -Filter '*-session.md') }
if (!$files.Count) { throw 'Brak zestawów do kontroli.' }
$problems = 0; $checked = 0
foreach ($file in $files) {
    foreach ($copy in @(Get-Exercises -File $file.FullName)) {
        $checked++; $origin = Get-PracticeOrigin $copy; $snapshot = Get-PracticeSnapshot $copy
        $originals = @($bank | Where-Object Id -CEQ $origin)
        $reasons = [Collections.Generic.List[string]]::new()
        if (!$snapshot -or $originals.Count -ne 1) { $reasons.Add('MISSING_PROVENANCE') }
        else {
            $original = $originals[0]
            if ((Get-ContractHash $original) -ne $snapshot.Contract) { $reasons.Add('SOURCE_CONTRACT_CHANGED') }
            if ((Get-TextHash $original.Tests) -ne $snapshot.Tests) { $reasons.Add('SOURCE_TESTS_CHANGED') }
            if ((Get-TextHash $original.Stub) -ne $snapshot.Stub) { $reasons.Add('SOURCE_STUB_CHANGED') }
            if ((Get-TextHash $copy.Tests) -ne $snapshot.Tests) { $reasons.Add('LOCAL_TESTS_CHANGED') }
            $content = (($copy.Body -split '(?m)^Kontekst:',2)[0]).Trim()
            if ((Get-TextHash $content) -ne $snapshot.Rendered -or ($copy.Title -cne $original.Title -and (Get-ContractHash $original) -eq $snapshot.Contract)) { $reasons.Add('LOCAL_CONTRACT_CHANGED') }
            if ($copy.Pending -and (Get-TextHash $copy.Stub.Replace("TODO $($copy.Id)","TODO $origin")) -ne $snapshot.Stub) { $reasons.Add('LOCAL_SKELETON_CHANGED') }
        }
        if ($reasons.Count) { $problems++; Write-Output "$($file.FullName):$($copy.StubLine) $($copy.Id) DRIFT: $($reasons -join ', ')" }
        else { Write-Output "$($file.Name) $($copy.Id) SYNC OK$(if (!$copy.Pending) { ' (własne rozwiązanie zachowane)' })" }
    }
}
Write-Output "Practice sync: CHECKED=$checked DRIFT=$problems (kontrola tylko do odczytu)"
if ($problems) { throw 'Wykryto rozjazd treningu. Żaden plik nie został zmieniony.' }
