# Biblioteci ATF

## Pe scurt

Java 21 și Maven; cinci JAR-uri thin, cu tranzitivele din POM. Ghidurile sunt autonome față de sursele producătorului.

| Ghid | Artefact, grup `atf.api` | Utilizare |
|---|---|---|
| [Core](README.Core.md) | `atf-core` | HTTP, configurație, JSON și lifecycle. |
| [Reporting](README.Reporting.md) | `atf-reporting` | Extent/fallback și dovezi auditabile. |
| [Console](README.Console.md) | `atf-console` | Diagnostic Maven și IntelliJ. |
| [Mock](README.Mock.md) | `atf-mock` | Componente și contracte HTTP offline. |
| [Test Kit](README.TestKit.md) | `atf-test-kit` · audit și lifecycle JUnit neutru | Clasificare, comparații și suportul testelor. |

## Integrare

1. Într-un workspace creat prin [infrastructura proiectului](../docs/rentz/ATF_Infrastructure.md), `mvn -B -ntp -N validate` instalează perechile din lib în repository-ul Maven selectat.
2. Declarați dependența în modulul apelant; coordonatele și versiunea sunt în POM-ul alăturat JAR-ului.
3. IntelliJ folosește același JDK și repository Maven; reîncărcați POM-ul rădăcină.

<details>
<summary>Consumer nou, fără reactor ATF</summary>

Copiați perechile JAR/POM în `lib` în proiectul nou. Din rădăcina copiei de lucru temporare, setați un repository Maven dedicat cu o cale absolută. Pentru rulări J5, folosiți workspace-ul și cache-ul înregistrate conform [infrastructurii proiectului](../docs/rentz/ATF_Infrastructure.md).

```powershell
$consumerMavenRepo = '<cale-absoluta-catre-cache-ul-Maven-dedicat>'
Get-ChildItem lib\*.pom | ForEach-Object {
    mvn "-Dmaven.repo.local=$consumerMavenRepo" org.apache.maven.plugins:maven-install-plugin:3.2.0:install-file "-Dfile=$([IO.Path]::ChangeExtension($_.FullName,'jar'))" "-DpomFile=$($_.FullName)" -DgeneratePom=false
    if ($LASTEXITCODE) { throw "Instalare eșuată: $($_.Name)" }
}
```

Declarați, de exemplu, `atf.api:atf-reporting:<versiunea din POM>` în dependențe. Folosiți scope `compile` pentru suport în `src/main` și `test` pentru `src/test`. Selectați același repository la comenzile Maven ulterioare și la importul IntelliJ.

</details>

## Identitate și verificare

- O singură pereche JAR/POM per bibliotecă, indexul și cele cinci ghiduri, direct în lib.
- În ATF, pom.xml selectează versiunile; library-provenance.json păstrează hash-urile și originea.
- [Poarta adopției](../docs/rentz/ATF_Qualification.md#poarta-bibliotecilor) verifică perechile, proveniența și CodeSource; fiecare consumator califică propriul profil și classpath într-un workspace înregistrat.

## Alegerea verificării minime

| Bibliotecă | Punctul de pornire | Contractul observabil |
|---|---|---|
| Core | [Exemplul minim](README.Core.md#exemplu-minim) | Construirea URL-ului și păstrarea preciziei JSON. |
| Reporting | [Exemplul minim](README.Reporting.md#exemplu-minim) | Publicarea încheiată, urmată de propagarea comparației eșuate. |
| Console | [Exemplul minim](README.Console.md#exemplu-minim) | Mesaj vizibil sau consolă dezactivată, după politica explicită. |
| Mock | [Exemplul minim](README.Mock.md#exemplu-minim) | Configurația și cererea produc răspunsul ori fallback-ul declarat. |
| Test Kit | [Exemplul minim](README.TestKit.md#exemplu-minim) | Comparație evaluată o dată, PASS/FAIL și cauza JUnit păstrate. |

Verificați întâi compilarea pe perechile fizice JAR/POM, apoi comportamentul fragmentului. Contractele aplicației, transportul real și prezentarea interactivă se califică separat în consumer.

## Mentenanță și feedback

Codul, testele native și feedback-ul bibliotecilor aparțin `atf_j0` (`atf_j0/docs/feedback/README.md`). Remedierea și regresia în J0 preced sincronizarea și acceptanța locală; feedback-ul include JAR/hash, reproducere și așteptat/observat.
