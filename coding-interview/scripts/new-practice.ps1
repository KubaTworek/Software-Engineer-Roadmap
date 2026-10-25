param(
    [int]$Count = 6, [int]$Seed = 42,
    [Parameter(Mandatory)][string]$OutputPath,
    [string[]]$Ids, [string[]]$Level,
    [Alias('KnownCategories')][string[]]$Categories,
    [string[]]$ExcludeIds, [string]$ExcludeFile,
    [switch]$ExcludeSolved, [switch]$Force,
    [string]$BankDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'exercises')
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ExerciseTools.ps1')
$moduleRoot = Split-Path -Parent $PSScriptRoot
$bank = @(Get-Exercises -Directory $BankDirectory)
$Level = @($Level | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$Categories = @($Categories | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$ExcludeIds = @($ExcludeIds | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$Ids = @($Ids | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($ExcludeFile) { $ExcludeIds += @(Get-Content -LiteralPath $ExcludeFile | ForEach-Object { ($_ -split '#',2)[0].Trim() } | Where-Object { $_ }) }
if (@($Level | Where-Object { $_ -cnotin @('L1','L2','L3') }).Count) { throw 'Poziom: L1, L2 lub L3.' }
if (@($Categories | Where-Object { $_ -cnotin $bank.Category }).Count) { throw 'Nieznana kategoria; użyj nazwy pliku bez numeru i rozszerzenia, np. hash-map.' }
foreach ($id in @($Ids) + @($ExcludeIds)) { if ($id -cnotin $bank.Id) { throw "Nieznane ID: $id" } }
$items = @($bank | Where-Object {
    (!$Level.Count -or $_.Level -cin $Level) -and (!$Categories.Count -or $_.Category -cin $Categories) -and
    $_.Id -cnotin $ExcludeIds -and (!$ExcludeSolved -or $_.Pending)
})
if ($Ids.Count) {
    if (@($Ids | Group-Object | Where-Object Count -gt 1).Count) { throw 'Powtórzone ID.' }
    if (@($Ids | Where-Object { $_ -cnotin $items.Id }).Count) { throw 'Wskazane ID nie spełnia filtrów lub jest wykluczone.' }
    $items = @($Ids | ForEach-Object { $id = $_; $items | Where-Object Id -CEQ $id }); $Count = $items.Count
}
if ($Count -lt 1 -or $Count -gt $items.Count) { throw "Count poza rozmiarem puli po filtrach: $($items.Count)." }
$random = [Random]::new($Seed)
for ($index = $items.Count-1; $index -gt 0; $index--) { $other = $random.Next($index+1); $swap = $items[$index]; $items[$index] = $items[$other]; $items[$other] = $swap }
$path = [IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $path) {
    if (!$Force) { throw 'Plik już istnieje; wybierz inną nazwę lub -Force.' }
    foreach ($copy in @(Get-Exercises -File $path)) {
        $origin = Get-PracticeOrigin $copy; $original = @($bank | Where-Object Id -CEQ $origin)
        if ($original.Count -ne 1 -or !$copy.Pending -or $copy.Title -cne $original[0].Title) { throw 'Odmowa nadpisania: zestaw zawiera własną pracę lub nieznane źródło.' }
        $snapshot = Get-PracticeSnapshot $copy
        $expectedTests = if ($snapshot) { $snapshot.Tests } else { Get-TextHash $original[0].Tests }
        $expectedStub = if ($snapshot) { $snapshot.Stub } else { Get-TextHash $original[0].Stub }
        $expectedContract = if ($snapshot) { $snapshot.Rendered } else { Get-TextHash (Get-PracticeContent $original[0]) }
        $localStub = $copy.Stub.Replace("TODO $($copy.Id)","TODO $origin")
        $localContract = (($copy.Body -split '(?m)^Kontekst:',2)[0]).Trim()
        if ((Get-TextHash $copy.Tests) -ne $expectedTests -or (Get-TextHash $localStub) -ne $expectedStub -or (Get-TextHash $localContract) -ne $expectedContract) { throw 'Odmowa nadpisania: lokalne zmiany treści, testów lub szkieletu. Użyj nowej nazwy.' }
    }
}
$fence = '```'; $parts = [Collections.Generic.List[string]]::new()
$parts.Add("# Trening mieszany`n`nNajpierw nazwij technikę i uzasadnij wybór. Źródło z kategorią jest ukryte pod każdym zadaniem.`n`nTo samodzielny zestaw do edycji: uzupełniaj metody w tym pliku. Z katalogu coding-interview:`n`n${fence}powershell`npwsh -File scripts/check-exercises.ps1 -PracticeFile '$($OutputPath.Replace("'", "''"))' -Exercise P01`n$fence`n`nZiarno doboru: $Seed. Importy java.util.* zapewnia skrypt. Testy nie stanowią dowodu złożoności.`n")
for ($index = 0; $index -lt $Count; $index++) {
    $item = $items[$index]; $neutral = 'P{0:00}' -f ($index+1)
    $content = Get-PracticeContent $item
    $relative = [IO.Path]::GetRelativePath([IO.Path]::GetDirectoryName($path),$item.File).Replace('\','/')
    $stub = $item.Stub.Replace("TODO $($item.Id)","TODO $neutral").TrimEnd(); $tests = $item.Tests.TrimEnd()
    $snapshot = "<!-- snapshot:contract=$(Get-ContractHash $item);tests=$(Get-TextHash $item.Tests);stub=$(Get-TextHash $item.Stub);rendered=$(Get-TextHash $content) -->"
    $parts.Add("## $neutral — $($item.Title)`n`n$content`n`nKontekst: szkielet do uzupełnienia.`n`n<!-- exercise:$neutral -->`n${fence}java`n$stub`n$fence`n`nKontekst: wykonywalne testy.`n`n<!-- tests:$neutral -->`n${fence}java`n$tests`n$fence`n`n<details>`n<summary>Pokaż kategorię i wskazówki</summary>`n`n<!-- origin:$($item.Id) -->`n$snapshot`n[Oryginał $($item.Id)]($relative). Otwórz po własnej analizie.`n`n</details>`n")
}
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path)) | Out-Null
[IO.File]::WriteAllText($path,($parts -join "`n"),[Text.UTF8Encoding]::new($false))
Write-Output "Zapisano $Count zadań z puli $($items.Count): $path"
