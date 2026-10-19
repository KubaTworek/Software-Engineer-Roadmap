param([string]$SystemRoot = (Get-Location).Path, [switch]$SelfTest)
$ErrorActionPreference = 'Stop'

function Assert-ContractReport([xml]$Report, [string]$Label) {
    $suite = $Report.testsuite
    if (!$suite -or [int]$suite.tests -le 0 -or [int]$suite.skipped -gt 0 -or
            [int]$suite.failures -gt 0 -or [int]$suite.errors -gt 0) {
        throw "Infrastructure contract not proven: $Label (missing, empty, skipped or failed suite)"
    }
}

if ($SelfTest) {
    Assert-ContractReport ([xml]'<testsuite tests="2" skipped="0" failures="0" errors="0"/>') 'success'
    foreach ($fixture in @(
        '<testsuite tests="0" skipped="0" failures="0" errors="0"/>',
        '<testsuite tests="2" skipped="2" failures="0" errors="0"/>',
        '<testsuite tests="2" skipped="0" failures="1" errors="0"/>',
        '<testsuite tests="2" skipped="0" failures="0" errors="1"/>')) {
        $rejected = $false
        try { Assert-ContractReport ([xml]$fixture) 'negative fixture' } catch { $rejected = $true }
        if (!$rejected) { throw "Report validator accepted invalid fixture: $fixture" }
    }
    Write-Output 'Infrastructure report validator: success + four negative cases OK'
    return
}

$sourceTests = @(Get-ChildItem $SystemRoot -Recurse -Filter '*.java' | Where-Object {
    $_.FullName -notmatch '[\\/](target|build)[\\/]' -and
    $_.FullName -match '[\\/]src[\\/](test|infrastructure-test)[\\/]' -and
    ($_.BaseName -cmatch 'Infrastructure(Test|IT)$|IT$')
})
if (!$sourceTests.Count) { throw "No infrastructure test sources in $SystemRoot" }
$reports = @(Get-ChildItem $SystemRoot -Recurse -Filter 'TEST-*.xml' | Where-Object {
    $_.FullName -match '[\\/](surefire|failsafe)-reports[\\/]'
})
foreach ($test in $sourceTests) {
    $report = @($reports | Where-Object { $_.Name.EndsWith('.' + $test.BaseName + '.xml') })
    if (!$report.Count) { throw "No executed report for $($test.BaseName)" }
    foreach ($file in $report) { Assert-ContractReport ([xml][IO.File]::ReadAllText($file.FullName)) $file.FullName }
}
Write-Output "Infrastructure reports: $($sourceTests.Count) suites, none missing or skipped"
