# Inventarul infrastructurii și spațiile de lucru

## Contract

[Registrul infrastructurii](../../config/infrastructure-workspaces.json) declară checkout-urile, directoarele sursei, colecțiile externe și tipurile de workspace. Un nume care pare ATF nu este o înregistrare; o zonă de lucru cere identitate, responsabil și scop verificabile.

Lansatoarele și controalele infrastructurii folosesc PowerShell 7.

| Zonă | Rol și limită |
|---|---|
| Grupul checkout-urilor | Numai proiectele ATF declarate în registru; nu primește staging sau rezultate. |
| Sursa Rentz | Modulele, configurația, documentația și scripturile declarate. |
| Depozitul extern | `RENTZ_DATA_ROOT`, implicit directorul `rentz_back` de lângă grupul checkout-urilor. Fără override, rădăcina se calculează din Git common directory al checkout-ului; worktree-urile imbricate folosesc aceeași rădăcină ca sursa canonică. |
| `distribution` | Artefacte publicate și dovezi de verificare a descărcării; conținutul se păstrează ca livrare externă și nu este workspace de build. |
| `infrastructure/workspaces` | Copii de compilare și fixture-uri cu descriptor `.atf-workspace.json`; identitatea directorului trebuie să coincidă cu descriptorul. |
| `infrastructure/signing` | Cheia persistentă Rentz și credențiala DPAPI, cu ACL privat; nu se curăță ca un cache și nu se copiază în release. |
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

Analiza AST protejează formele declarate și recunoscute, nu reprezintă un sandbox pentru orice cod dinamic. Destinația fizică, proprietarul și verificarea la momentul scrierii rămân contracte obligatorii; motivul unei citiri TEMP nu autorizează alocarea în acel director.

## Curățare și probe

1. Rulați testul pe starea reală și păstrați rezultatul eșuat înaintea ștergerii.
2. Identificați proprietarul, conținutul, referințele active și căile absolute; nu ștergeți prin wildcard sau după nume aproximativ.
3. Eliminați numai țintele autorizate și validate. Un director gol se elimină fără ștergere recursivă; apariția unui fișier nou oprește operația.
4. Rulați aceleași criterii și controalele negative, apoi păstrați rezultatul trecut.

Copiile istorice indicate de receipt-uri nu se rebotează și nu se șterg în masă pentru a obține verde. Datele și procesele altui proprietar se păstrează; restricția unei familii vechi nu este o permisiune de a curăța întregul director temporar.

- Regresia și buildul Android compilează în `infrastructure/workspaces`. Un `target` dintr-un astfel de spațiu poate conține clase pe care se bazează classpath-ul atestat al raportului sau al probei Android. Înainte de retragere, verificați receiptele, runtime-ul, worktree-urile Git și semnarea. [Retenția spațiilor de lucru în feedback](../feedback/ATF_Release_RAG.md#retenția-spațiilor-de-lucru)

- Lansatorul RAG alocă JVM-ului `java-scratch` în directorul rulării. La ieșire încearcă să elimine acest director și scrie `scratch-cleanup.json`, inclusiv dacă evaluatorul eșuează. Rezultatul curățării are propriul statut. [Lansatorul RAG](../../scripts/run-release-rag.ps1) · [Controalele runtime](../../atf-tests/tools/test-qualified-runtime.ps1)

## Dosarul selectat al livrării

Directorul sursă `release` este catalogul documentar și copia selectată a livrării, nu o destinație pentru rulările curente. [Procesul APK](ATF_Release.md) permite numai promovarea explicită a dovezilor calificate; toate rulările și build-urile intermediare folosesc în continuare colecțiile externe înregistrate.

## Bootstrap și calificare executabilă

| Mecanism | Contract |
|---|---|
| Parser de sursă | Modulul de testare compilează parserul separat în workspace de guvernanță, cu JDK 21 și proces finit. Sursele aplicației sunt doar analizate, fără procesare de adnotări sau execuție; temporarele compilatorului aparțin aceluiași workspace. |
| [Planul colecției](../../scripts/TestCollections.psm1) | Se derivă din snapshot-ul copiat. Sursele și octeții compilați identifică bootstrap-ul; o schimbare a cache-ului este refuzată, fără recapturarea așteptării. |
| [Calificarea runtime-ului](ATF_Qualification.md#identitatea-runtime-ului-calificat) | Fixează separat inventarul binarelor și resurselor de pe classpath. Controalele folosesc fixture-uri proprii și nu rezervă dispozitive sau date de antrenare. |
