# Wspólne reguły odczytu dokumentów. Funkcje nie zmieniają plików.
Set-StrictMode -Version Latest

function Get-QuestionSections([string]$Content) {
    $heads = [Collections.Generic.List[object]]::new()
    $fence = $null
    foreach ($line in [regex]::Matches($Content, '[^\n]+(?:\n|$)|\n')) {
        $value = $line.Value.TrimEnd("`r", "`n")
        if ($value -match '^\s*(~{3,}|\x60{3,})') {
            if ($null -eq $fence) { $fence = $Matches[1][0] }
            elseif ($fence -eq $Matches[1][0]) { $fence = $null }
            continue
        }
        if ($null -eq $fence -and $value -match '^## ([A-Z]+-\d+)\. (.+)$') {
            $heads.Add([pscustomobject]@{ Id=$Matches[1]; Title=$Matches[2]; Heading=$value; Start=$line.Index; BodyStart=$line.Index+$line.Length })
        }
    }
    for ($i=0; $i -lt $heads.Count; $i++) {
        $end = if ($i+1 -lt $heads.Count) { $heads[$i+1].Start } else { $Content.Length }
        [pscustomobject]@{ Id=$heads[$i].Id; Title=$heads[$i].Title; Heading=$heads[$i].Heading; Start=$heads[$i].Start; Body=$Content.Substring($heads[$i].BodyStart,$end-$heads[$i].BodyStart) }
    }
}

function Get-HeadingSlug([string]$Heading) {
    $Heading.ToLowerInvariant() -replace '[^\p{L}\p{N}_\-\s]', '' -replace '\s', '-'
}

function Get-ExampleDescription([string]$Kind) {
    switch ($Kind) {
        'java-members' { 'Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.' }
        'java-statements' { 'Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.' }
        'java-members-negative' { 'Java 21 — kompilowalny kontrprzykład błędnej logiki; skrypt nie uruchamia tego kodu jako poprawnego rozwiązania.' }
        'java-sketch' { 'Szkic Java — wymaga wskazanych pól lub metod otoczenia; poza automatyczną kompilacją.' }
        'spring-sketch' { 'Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.' }
        'jpa-sketch' { 'Szkic JPA/Spring Data — wymaga encji, repozytorium lub persistence context; poza automatyczną kompilacją.' }
        'jdbc-sketch' { 'Szkic JDBC — wymaga skonfigurowanego DataSource i bazy; poza automatycznym wykonaniem.' }
        'sql-template' { 'Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.' }
        'jpql-template' { 'Szablon JPQL — nazwy oznaczają encje i relacje, nie tabele SQL; wymaga skonfigurowanego JPA.' }
        'http-contract' { 'Przykład kontraktu HTTP; wymaga odpowiadającego API, nie jest automatycznie wysyłanym żądaniem.' }
        'properties-fragment' { 'Fragment konfiguracji properties; wymaga zgodnej wersji biblioteki i otoczenia; bez testu uruchomienia.' }
        'yaml-fragment' { 'Fragment konfiguracji YAML; wymaga wskazanego zasobu/serwera; bez testu wdrożenia.' }
        'shell-command' { 'Przykładowe polecenia — PID, ścieżki i środowisko należy dopasować; skrypt ich nie wykonuje.' }
        'text-model' { 'Model lub przebieg operacji zapisany tekstowo; nie jest programem do uruchomienia.' }
        'mermaid-diagram' { 'Diagram Mermaid opisujący model; nie dowodzi wykonania ani gotowości wdrożenia.' }
        'json-contract' { 'Przykładowy kontrakt JSON; skrypt sprawdza składnię, nie zgodność z dowolnym API ani regułami domeny.' }
        'synthetic-diagnostic' { 'Syntetyczny, skrócony materiał diagnostyczny; dane ilustracyjne, nie wynik pomiaru repozytorium.' }
        default { throw "Nieznany kontekst przykładu: $Kind" }
    }
}

function Get-DocumentExamples([string]$Content, [string]$File) {
    $openFence = $null
    foreach ($line in ($Content -split "`n")) {
        if ($line -match '^\s*(~{3,}|\x60{3,})(.*)$') {
            $fenceMarker=$Matches[1]
            $fenceSuffix=$Matches[2].Trim()
            if ($null -eq $openFence) {
                if ($fenceSuffix -notmatch '^[a-z]+$') { throw "${File}: blok przykładu musi mieć jawny język" }
                $openFence=$fenceMarker
            } elseif ($fenceMarker -eq $openFence -and !$fenceSuffix) {
                $openFence=$null
            }
        }
    }
    if ($null -ne $openFence) { throw "${File}: niezamknięty blok przykładu" }
    $pattern = '(?ms)^(?<fence>~{3,}|\x60{3,})(?<language>[a-z]+)[ \t]*\n(?<code>.*?)^\k<fence>[ \t]*$'
    foreach ($block in [regex]::Matches($Content, $pattern)) {
        $prefix = $Content.Substring(0,$block.Index)
        $annotation = [regex]::Match($prefix, '(?s)<!-- example:([A-Z]+-\d+-\d+) kind:([a-z-]+) -->\n\*\*Kontekst:\*\* ([^\n]+)\n\n$')
        if (!$annotation.Success) { throw "${File}: blok $($block.Groups['language'].Value) nie ma jednoznacznego kontekstu" }
        $kind = $annotation.Groups[2].Value
        if ($annotation.Groups[3].Value -cne (Get-ExampleDescription $kind)) { throw "${File}: nieaktualny opis kontekstu $($annotation.Groups[1].Value)" }
        $language = $block.Groups['language'].Value
        $expected = switch -Regex ($kind) {
            '^java-|^(spring|jpa|jdbc)-sketch$' { 'java'; break }
            '^sql-template$' { 'sql'; break }
            '^jpql-template$' { 'jpql'; break }
            '^http-contract$' { 'http'; break }
            '^properties-fragment$' { 'properties'; break }
            '^yaml-fragment$' { 'yaml'; break }
            '^shell-command$' { 'shell'; break }
            '^(text-model|synthetic-diagnostic)$' { 'text'; break }
            '^mermaid-diagram$' { 'mermaid'; break }
            '^json-contract$' { 'json'; break }
            default { throw "Brak języka dla $kind" }
        }
        if ($language -ne $expected) { throw "${File}: $($annotation.Groups[1].Value) ma język $language zamiast $expected" }
        [pscustomobject]@{ Id=$annotation.Groups[1].Value; Kind=$kind; Language=$language; Code=$block.Groups['code'].Value; File=$File }
    }
}
