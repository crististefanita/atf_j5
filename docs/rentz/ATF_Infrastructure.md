# Inventarul infrastructurii și spațiile de lucru

## Contract

[Registrul infrastructurii](../../config/infrastructure-workspaces.json) declară checkout-urile, directoarele sursei, colecțiile externe și tipurile de workspace. Un nume care pare ATF nu este o înregistrare; o zonă de lucru cere identitate, responsabil și scop verificabile.

Lansatoarele și controalele infrastructurii folosesc PowerShell 7.

| Zonă | Rol și limită |
|---|---|
| Grupul checkout-urilor | Numai proiectele ATF declarate în registru; nu primește staging sau rezultate. |
| Sursa Rentz | Modulele, configurația, documentația și scripturile declarate. |
| Depozitul extern | `rentz_back/.imp` lângă grupul checkout-urilor. Git identifică checkout-ul principal, astfel încât toate worktree-urile folosesc aceeași rădăcină permanentă. `RENTZ_DATA_ROOT` poate selecta numai această rădăcină sau un descendent al ei. |
| `distribution` | Artefacte publicate și dovezi de verificare a descărcării; conținutul se păstrează ca livrare externă și nu este workspace de build. |
| `infrastructure/workspaces` | Copii de compilare și fixture-uri cu descriptor `.atf-workspace.json`; identitatea directorului trebuie să coincidă cu descriptorul. |
| `.atf-signing` în checkout-ul principal | Cheia persistentă Rentz și credențiala DPAPI. Directorul are ACL privat, este ignorat de Git și rămâne la locul lui când curățăm datele generate. |
| `infrastructure/cache` | Dependențe reutilizabile; nu este sursă și nu devine rezultat de testare. |
| `infrastructure/governance` | Rezultate imuabile ale guvernanței, inclusiv proba negativă înaintea curățării. |
| `validation` | Probe de preflight, verificări de documentație și fixture-uri de validare păstrate pentru audit; sunt în afara sursei și nu sunt șterse automat. |

## Creare și verificare

[Allocatorul](../../scripts/InfrastructureWorkspaces.psm1) validează categoria și limitele fizice înainte de creare. O rădăcină care traversează un link este refuzată; depozitul nu poate fi în sursă sau în directorul temporar al sistemului.

[Testul live](../../scripts/test-infrastructure-governance.ps1) inventariază directoarele declarate, descriptorii și toate scripturile PowerShell descoperite în sursă. Familia istorică de compilare interzisă este verificată separat în directorul temporar al sistemului; testul nu declară că toate directoarele Windows sau ale altor aplicații aparțin ATF.

| Poartă | Comportament |
|---|---|
| Lansatoare active | Componentele, build-ul Android, arena și sanity-ul nativ refuză execuția dacă inventarul are abateri. |
| Destinație de produs | Trebuie să fie într-o colecție declarată din depozitul extern, fără ieșire prin linkuri. |
| Cache de build | Se creează sub `infrastructure/cache`; seeding-ul nu mută și nu rescrie dovezile istorice. |
| JVM de componente | Directorul temporar al procesului este în workspace-ul înregistrat, separat de sursa live. |
| Colecții offline | [Selectorul](../../scripts/TestCollections.psm1) citește registrul, iar lansatorul confruntă populația executată cu cea selectată; [calificarea](ATF_Qualification.md) separă Maven, Android JVM și controalele PowerShell. |
| Android JVM | [Lansatorul dedicat](../../scripts/test-rentz-android-jvm.ps1) copiază sursa calificării în workspace Android; nu produce APK și nu rezervă dispozitiv. |
| Browser | [Compilarea paginii](../../atf-ui-browser/Build-RentzPages.ps1) și [componentele adaptorului](../../atf-ui-browser/Test-RentzBrowserComponents.ps1) au intrări explicite în registru. Snapshot-ul componentelor include `atf-ui-browser`, iar toate rădăcinile Java, inclusiv compilatorul TeaVM și probele CLI, rămân în inventarul de surse și sub limita de opt fișiere pe pachet. |
| Calibrarea realizărilor | [Lansatorul offline](../../scripts/run-achievement-calibration.ps1) folosește catalogul și simulatorul compilate într-o rulare Android JVM admisă. Cronologia și statisticile partidelor se scriu în colecția `learning`. |

## Inventarul documentației

[Politica documentară](../../config/documentation-policy.json) separă rădăcinile active, indexurile și instantaneele istorice. [Contractul executabil](../../atf-tests/src/test/java/atf/rentz/governance/DocumentationContractTest.java) verifică documentele neclasificate, hash-urile arhivei și rutele modulelor către lansatoare și colecții înregistrate.

| Frontieră | Responsabilitate |
|---|---|
| Corpus înghețat | Producătorul componentelor copiază întregul `docs`, README-urile modulelor și indexul istoric; o copie incompletă nu califică inventarul. |
| Ghid activ | Stil, legături, scheme și afirmații confruntate cu sursa; rezultatele unei rulări rămân externe. |
| Istoric | Hash inițial și hash arhivat, cu ajustarea linkurilor relative; originalul brut și jurnalul mutării rămân în guvernanța externă. |
| Retenție | Dovezile externe nu se mută implicit, iar eliminarea cere revizuire explicită. |

## Rolurile scripturilor și citirea TEMP

- Descoperirea recursivă nu se limitează la lista producătorilor sau la directorul `scripts`; un script nou, inclusiv unul fără alocări, cere rol explicit în registru.
- Directoarele generate Maven/Gradle și metadatele checkout-ului au limite separate; un pachet de sursă numit `target` nu este exclus implicit.
- Inventarul refuză linkurile întâlnite fără a le urma.

| Declarație | Verificare |
|---|---|
| `producerScripts` | Script existent și analizabil; accesul direct la TEMP/TMP, API-urile temporare și numele de mediu neclarificate sunt refuzate. |
| `reviewedTempReaders` | Cale, rol, funcții și motiv explicite pentru citirea frontierei TEMP. Excepția nu se transmite automat unei funcții noi din același fișier. |
| Nume de mediu constant | O setare fără legătură cu TEMP rămâne permisă; o buclă peste o listă constantă cere nume netemporare și variabilă nereatribuită. |
| Script fără rol sau declarație fără fișier | Refuz; lista nu poate ascunde un punct de intrare nou sau o intrare rămasă fără sursă. |

[Controalele cu fixture-uri](../../atf-tests/tools/test-workspace-governance.ps1) confruntă omisiunile inventarului, API-ul de mediu, citirile legitime, funcțiile neaprobate și linkurile imbricate. Lansatorul standard al componentelor le rulează înaintea Maven; eșecul oprește rularea și păstrează diagnosticul extern.

- **Fixture Git.** Worktree-ul imbricat are o rădăcină compactă în spațiul înregistrat. Crearea metadatelor și materializarea commitului sunt separate pentru limita internă `GIT_DIR` din versiunile mai vechi de Git for Windows. [Git for Windows](https://gitforwindows.org/git-cannot-create-a-file-or-directory-with-a-long-path.html)
- **Cale lungă.** Comenzile necesare primesc `core.longpaths=true` local procesului. Proba verifică octeții unui fișier a cărui cale depășește 260 de caractere, rădăcina comună a datelor și locația semnării cu o rădăcină de artefacte configurată.

Analiza AST protejează formele declarate și recunoscute, nu reprezintă un sandbox pentru orice cod dinamic. Destinația fizică, proprietarul și verificarea la momentul scrierii rămân contracte obligatorii; motivul unei citiri TEMP nu autorizează alocarea în acel director.

## Curățare și probe

Sursa păstrează numai intrările întreținute: cod, configurație, documentație și biblioteci declarate. Datele generate, inclusiv un director istoric `.atf-data`, nu se reintroduc în checkout; allocatorul comun direcționează toate worktree-urile către depozitul extern. [Proba de inventar](../../atf-tests/tools/test-workspace-governance.ps1) verifică refuzul directorului istoric și revenirea aceluiași inventar după eliminare.

Semnarea are trei intrări persistente în `.atf-signing`: `rentz-release.p12`, `password.dpapi` și `identity.json`. Identitatea declară aliasul, locațiile și amprenta certificatului; build-ul verifică semnătura APK-ului față de această identitate. Exporturile DER și logurile de verificare se generează în rularea externă, apoi se retrag. Copia de recuperare păstrează aceiași octeți esențiali, iar o identitate istorică se arhivează separat de cheia activă.

1. Rulați testul pe starea reală și păstrați rezultatul eșuat înaintea ștergerii.
2. Identificați proprietarul, conținutul, referințele active și căile absolute; nu ștergeți prin wildcard sau după nume aproximativ.
3. Eliminați numai țintele autorizate și validate. Un director gol se elimină fără ștergere recursivă; apariția unui fișier nou oprește operația.
4. Rulați aceleași criterii și controalele negative, apoi păstrați rezultatul trecut.

Copiile istorice indicate de receipt-uri nu se rebotează și nu se șterg în masă pentru a obține verde. Datele și procesele altui proprietar se păstrează; restricția unei familii vechi nu este o permisiune de a curăța întregul director temporar.

- Regresia și buildul Android compilează în `infrastructure/workspaces`. Un `target` dintr-un astfel de spațiu poate conține clase pe care se bazează classpath-ul atestat al raportului sau al probei Android. Înainte de retragere, verificați receiptele, runtime-ul, worktree-urile Git și semnarea. [Retenția cache-urilor și a fixture-urilor](#retenția-cache-urilor-și-a-fixture-urilor)

- Lansatorul RAG alocă JVM-ului `java-scratch` în directorul rulării. La ieșire încearcă să elimine acest director și scrie `scratch-cleanup.json`, inclusiv dacă evaluatorul eșuează. Rezultatul curățării are propriul statut. [Lansatorul RAG](../../scripts/run-release-rag.ps1) · [Controalele runtime](../../atf-tests/tools/test-qualified-runtime.ps1)

### Verificare după curățare

Reluați RAG din worktree-ul commitului calificat, cu receiptele originale de componente, de build și de acceptanță nativă. Confirmați din nou porțile Extent pentru componente și Android, apoi verificați README-ul remote cu o descărcare autentificată proaspătă. O ediție deja publicată își păstrează manifestul și dovezile inițiale. [Porțile release-ului](ATF_Release.md#ordinea-calificării-android-curente)

### Inventarul țintelor Maven

- Furnizați căile exacte `target`, rădăcinile receiptelelor, comenzile proceselor active și căile worktree-urilor Git. Pentru țintele dintr-un workspace înregistrat, furnizați și rădăcina workspace-ului prin `WorkspaceByTarget`.
- `KEEP` indică o dependență, dovezi aflate în țintă sau un perimetru incomplet. `REVIEW` înseamnă că inventarul a terminat scanarea fără o dependență observată.
- Operatorul verifică din nou căile, referințele și procesele imediat înainte de ștergere. Salvează inventarul și rezultatul curățării cu rularea. [Inventarul read-only](../../scripts/BuildRetention.psm1) · [Controalele](../../atf-tests/tools/test-workspace-governance.ps1)

### Retenția cache-urilor și a fixture-urilor

Controlul runtime păstrează identitatea fixture-ului, ieșirea sintetică și cache-urile create pentru aceeași rulare. Inventarul tratează aceste căi ca un grup. O referință externă către orice cale păstrează grupul întreg.

Fișierele cu extensia APK sunt păstrate. Singurele excepții sunt fișierele de text din probele de descărcare, identificate prin poziția în fixture și prin SHA-256 cunoscut.

| Verdict | Criteriu |
|---|---|
| `REGENERABLE` | Receipt de control trecut, descriptor valid, fișiere inventariate și absența referințelor în perimetrul declarat. |
| `KEEP` | Referință din dovezi, proces, worktree, cache reutilizabil, binar semnat, cale legată sau scanare incompletă. |

Furnizați rădăcinile care conțin receiptele, capturile, rapoartele și descriptorii stagiilor active. Păstrați fișierul planului într-un director de audit din afara rădăcinilor scanate.

```powershell
.\scripts\run-runtime-retention.ps1 -ControlReceipt $controls -EvidenceRoot $evidenceRoots -PlanFile $plan
.\scripts\run-runtime-retention.ps1 -Apply -PlanFile $plan -RunRoot $audit -VerifyEvidence $readQualifiedRag
```

- Lansatorul recitește procesele, worktree-urile și dependențele. Compară hash-ul receipt-ului și inventarul pe fiecare cale înainte de mutare.
- Mutarea în carantină păstrează o hartă explicită a căilor. O recitire eșuată readuce directoarele la locațiile inițiale. Jurnalul indică și căile a căror restaurare a fost blocată.
- `$readQualifiedRag` recitește aceleași dovezi și întoarce `$true` după confirmarea celor două porți Extent. Un verdict `VERIFIED` permite retragerea carantinei. [Recitirea release-ului](#verificare-după-curățare) · [Probe offline](../../atf-tests/tools/test-runtime-retention.ps1)

## Dosarul selectat al livrării

Directorul `release` păstrează catalogul, documentele ediției active și manifestele cu digesturi. APK-ul și checksum-ul verificat se descarcă în `distribution/incoming/<versiune>`. Rapoartele, XML-urile, capturile și intrările atestate rămân în locațiile externe ale rulării. [Procesul APK](ATF_Release.md)

## Bootstrap și calificare executabilă

| Mecanism | Contract |
|---|---|
| Parser de sursă | Modulul de testare compilează parserul separat în workspace de guvernanță, cu JDK 21 și proces finit. Sursele aplicației sunt doar analizate, fără procesare de adnotări sau execuție; temporarele compilatorului aparțin aceluiași workspace. |
| [Planul colecției](../../scripts/TestCollections.psm1) | Se derivă din snapshot-ul copiat. Sursele și octeții compilați identifică bootstrap-ul; o schimbare a cache-ului este refuzată, fără recapturarea așteptării. |
| [Calificarea runtime-ului](ATF_Qualification.md#identitatea-runtime-ului-calificat) | Fixează separat inventarul binarelor și resurselor de pe classpath. Controalele folosesc fixture-uri proprii și nu rezervă dispozitive sau date de antrenare. |
