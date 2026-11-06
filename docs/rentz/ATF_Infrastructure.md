# Inventarul infrastructurii și spațiile de lucru

## Contract

[Registrul infrastructurii](../../config/infrastructure-workspaces.json) declară checkout-urile, directoarele sursei, colecțiile externe și tipurile de workspace. Un nume care pare ATF nu este o înregistrare; o zonă de lucru cere identitate, responsabil și scop verificabile.

Lansatoarele și controalele infrastructurii folosesc PowerShell 7.

| Zonă | Rol și limită |
|---|---|
| Grupul checkout-urilor | Numai proiectele ATF declarate în registru; nu primește staging sau rezultate. |
| Sursa Rentz | Modulele, configurația, documentația și scripturile declarate. |
| Depozitul extern | `RENTZ_DATA_ROOT`, implicit directorul `rentz_back` de lângă grupul checkout-urilor; colecții explicite de APK-uri, dovezi și învățare. |
| `infrastructure/workspaces` | Copii de compilare și fixture-uri cu descriptor `.atf-workspace.json`; identitatea directorului trebuie să coincidă cu descriptorul. |
| `infrastructure/cache` | Dependențe reutilizabile; nu este sursă și nu devine rezultat de testare. |
| `infrastructure/governance` | Rezultate imuabile ale guvernanței, inclusiv proba negativă înaintea curățării. |

## Creare și verificare

[Allocatorul](../../scripts/InfrastructureWorkspaces.psm1) validează categoria și limitele fizice înainte de creare. O rădăcină care traversează un link este refuzată; depozitul nu poate fi în sursă sau în directorul temporar al sistemului.

[Testul live](../../scripts/test-infrastructure-governance.ps1) inventariază directoarele declarate, descriptorii și toate scripturile PowerShell descoperite în sursă. Familia istorică de compilare interzisă este verificată separat în directorul temporar al sistemului; testul nu declară că toate directoarele Windows sau ale altor aplicații aparțin ATF.

| Poartă | Comportament |
|---|---|
| Lansatoare active | Componentele, build-ul Android, arena și sanity-ul nativ refuză execuția dacă inventarul are abateri. |
| Destinație de produs | Trebuie să fie într-o colecție declarată din depozitul extern, fără ieșire prin linkuri. |
| Cache de build | Se creează sub `infrastructure/cache`; seeding-ul nu mută și nu rescrie dovezile istorice. |
| JVM de componente | Directorul temporar al procesului este în workspace-ul înregistrat, separat de sursa live. |

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
