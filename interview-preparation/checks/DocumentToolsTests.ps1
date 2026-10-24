$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'DocumentTools.ps1')
function Assert-ParserFailure([string]$Text,[string]$Expected) {
    try { $null=@(Get-DocumentExamples $Text 'fixture.md') }
    catch {
        if ($_.Exception.Message -notlike "*$Expected*") { throw "Nieoczekiwany błąd parsera: $_" }
        return
    }
    throw "Nie odrzucono niepoprawnego przykładu: $Expected"
}
$label=Get-ExampleDescription 'java-members'
$valid="## TEST-01. Przykład`n`n<!-- example:TEST-01-1 kind:java-members -->`n**Kontekst:** $label`n`n~~~java`nstatic int value() { return 1; }`n~~~`n"
$examples=@(Get-DocumentExamples $valid 'fixture.md')
if ($examples.Count -ne 1 -or $examples[0].Id -ne 'TEST-01-1' -or !$examples[0].Code.Contains('return 1;')) { throw 'Nie odczytano poprawnego bloku' }
Assert-ParserFailure ($valid.Substring(0,$valid.LastIndexOf('~~~'))) 'niezamknięty'
Assert-ParserFailure "~~~java`nint x = 1;`n~~~`n" 'nie ma jednoznacznego kontekstu'
Assert-ParserFailure ($valid.Replace($label,'inny kontekst')) 'nieaktualny opis'
Assert-ParserFailure ($valid.Replace('~~~java','~~~sql')) 'zamiast java'
$withFakeHeading="## TEST-01. A`n`n~~~text`n## FAKE-01. To dane wewnątrz bloku`n~~~`n`n## TEST-02. B`n"
$questions=@(Get-QuestionSections $withFakeHeading)
if ($questions.Count -ne 2 -or $questions[1].Id -ne 'TEST-02') { throw 'Nagłówek wewnątrz kodu potraktowano jako pytanie' }
Write-Output 'parser: 6 regression cases; OK'
