# Livrarea și distribuția Rentz Lab

## Contract

O livrare identifică exact APK-ul semnat, sursele construirii, selecțiile trecute și copia primită de utilizator. Publicarea GitHub și folderul documentar sunt suprafețe distincte.

## Stări și responsabilități

| Stare | Criteriu |
|---|---|
| DRAFT | Metadate și documente inspectabile; fără binar sau descărcare calificată. |
| PREPARED | Regresie offline și Android JVM trecute, build Release semnat, integritate verificată și dosar coerent. |
| PUBLISHED | Tag fixat pe sursele calificate, assets confruntate și descărcare integrală verificată în canalul declarat. |

```mermaid
flowchart LR
    SOURCE[Snapshot identificat] -->|testează fără dispozitiv| CHECK[Componente și guvernanță]
    CHECK -->|permite build izolat| APK[APK Release semnat]
    APK -->|verifică identitatea| READY[Dosar calificat]
    READY -->|publică draftul confruntat| RELEASE[GitHub Release]
    RELEASE -->|descarcă și confruntă octeții| FILE[Copie completă]
```

<details>
<summary>Legendă: surse, dovezi și publicare</summary>

Tagul identifică sursele APK-ului. Commit-ul dovezilor poate fi ulterior; corecția unei prezentări nu mută tagul și nu înlocuiește binarul publicat. O descărcare API sau nativă nu califică automat navigarea vizuală din browser.

</details>

## Ordinea calificării

Din rădăcina proiectului, în PowerShell 7, cu JDK 21, Maven, SDK Android și cache-urile pregătite:

```powershell
.\scripts\run-rentz-components.ps1 -Collection regression
.\scripts\Initialize-RentzSigning.ps1
.\scripts\build-rentz-android.ps1 -RunRoot <director-extern-nou> -ComponentRun <regresie-trecuta> -Configuration Release
```

- **Cache concurent:** folosiți `-MavenRepository <cache-dedicat>` la calificare când alte procese pot încărca aceleași JAR-uri; calea rămâne în `infrastructure/cache`. Nu opriți procesele altui proprietar și nu transformați eroarea de înlocuire într-un rezultat verde.
- **Izolare:** staging-ul este workspace-ul temporar înregistrat de J5, în afara sursei și a TEMP-ului Windows. Rulările intermediare rămân în depozitul extern; numai dosarul selectat se copiază în `release/<versiune>`.
- **Teste:** se păstrează populațiile Maven și Android JVM, fără FAIL, erori sau skip-uri; o selecție scurtă nu se redenumește regresie. Testele de guvernanță și controalele negative ale descărcării se execută offline înainte de publicare.
- **Build:** verifică sursa calificată, pachetul, versiunea, SDK-urile, caracterul nedebuggable, certificatul și alinierea. Numele final este `RentzLab-<versiune>.apk`.
- **Produs:** o regresie E2E completă nu se repetă la fiecare publicare. Sanity-ul nativ se selectează numai pentru comportamentul Android modificat și are propria rezervare și dovadă; lipsa lui nu devine PASS.
- **Date:** primul APK semnat are o identitate de cheie separată de prototipurile Debug. Nu dezinstalați automat un prototip cu date pentru a forța instalarea release-ului; migrarea datelor este o operație distinctă.

## Semnare și păstrarea cheii

- Cheia Rentz este persistentă în zona privată `infrastructure/signing` a depozitului extern; nu aparține cache-ului, sursei sau dosarului release.
- Parola aleatoare este protejată cu Windows DPAPI pentru utilizatorul curent. Procesul Java o primește prin mediu, fără argumente, loguri sau valori în Gradle.
- Inițializarea refuză o identitate parțială și nu rotește automat cheia. Pierderea cheii afectează actualizările: păstrați backup-ul cheii și o recuperare securizată a credențialei, inclusiv pentru schimbarea contului sau a calculatorului.
- [Contractul Android de semnare](https://developer.android.com/studio/publish/app-signing) deține compatibilitatea actualizărilor; `targetSdk` nu demonstrează singur funcționarea pe toate versiunile Android.

## Dosar și raportare

| Fișier | Rol |
|---|---|
| release/index.json | Versiunea curentă și repository-ul; un singur dosar activ. |
| release-validation.json | Identitatea binarului, starea, calificarea și inventarul hash-urilor. |
| README și release-notes | Descărcare și capabilități confirmate; testele se explică prin legătura către reports. |
| reports/README | Scopul fiecărei selecții, proveniența, suprapunerile și limitele ei. |
| HTML Extent | Comparații capturate, diagnostice originale și corpuri inline; folderele cu copii redundante nu sunt livrabilul de audit. |
| XML și receipt | Numărare independentă și identitatea intrărilor; nu substituie expected/actual necapturat. |

[Atributele Git](https://git-scm.com/docs/gitattributes) dezactivează conversia text pentru intrările APK și dosarul release. Înainte de push se confruntă octeții din Git cu inventarul, separat de copia din workspace.

## Publicare și descărcare

1. Comiteți sursele calificate și fixați tagul; nu publicați un APK construit din alte intrări.
2. Creați draftul prin [API-ul GitHub Releases](https://docs.github.com/en/rest/releases/releases), cu APK și checksum. Salvați imediat ID-ul draftului în rezultatul extern.
3. La o reluare, recitiți obiectul după ID, asset-urile și hash-urile. Un 404 după tag nu justifică recrearea sau upload-ul repetat fără reconciliere.
4. Publicați doar populația exactă de assets și marcați ediția curentă drept latest. Binarul publicat rămâne stabil; o schimbare a sa cere versiune nouă.
5. Verificați README → release curent → asset: bytes efectivi, SHA-256 și toate CRC-urile. Un răspuns 404 salvat cu extensia APK rămâne eroare.
6. Păstrați distinct accesul autentificat, accesul fără sesiune și blocarea browserului. Tokenul API nu se transmite originilor CDN; o probă Android poate consuma numai redirectul temporar al asset-ului, fără expunerea lui în rapoarte.
7. Rulați intrarea explicită de acceptanță a distribuției cu classpath-ul calificat și tokenul numai în mediu. Nu o includeți implicit în regresia offline.
8. Actualizați starea, README-ul principal și legătura notelor către commit-ul dovezilor. Secțiunea Jocul are doar release-ul curent și descărcarea directă; catalogul tehnic rămâne în Documentație.

[Calificarea componentelor](ATF_Qualification.md) · [Protocolul Android](ATF_Android.md) · [Catalogul livrării](../../release/README.md)

## Acceptanța live a descărcării

Din rădăcina proiectului, după publicare, cu JDK 21 și descriptorul componentelor trecute:

```powershell
$runtime = Get-Content -Raw <ComponentRun>/native-runtime.json | ConvertFrom-Json
& java -cp $runtime.classpath atf.rentz.distribution.ReleaseDownloadAcceptance (Get-Location).Path <director-extern-nou>
```

- Tokenul se citește din `GH_TOKEN` sau `GITHUB_TOKEN`; nu se transmite ca argument și nu intră în rapoarte.
- Intrarea cere starea PUBLISHED și identitatea curentă verificată; produce șase comparații efective și diagnostic FAIL la eroare, fără fallback la o versiune anterioară.
- Componentele offline califică separat oracolul comparațiilor, metadata și transportul loopback. Raportul live rămâne distinct de navigarea browserului și de instalarea Android.
