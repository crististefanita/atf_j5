# Standardul de documentație Rentz

## Pe scurt

Un README orientează cititorul către contractul care deține comportamentul. Primul ecran explică scopul, intrarea uzuală și limita de responsabilitate. Toate ghidurile active se pot citi dintr-un checkout Rentz.

## Organizare și stil

| Material | Ce conține | Unde se păstrează |
|---|---|---|
| README de modul | Scop, graniță, puncte de intrare și verificare | Lângă modul |
| Ghid normativ | Mecanism, invariante, excepții și testul care le verifică | [Ghidurile Rentz](README.md) |
| Feedback | Lacună deschisă, efect, responsabil și criteriu observabil de închidere | [Registrul activ](../feedback/README.md) |
| Dovezi ale unei rulări | Commit, selecție, rezultat, raport și amprente | [Depozitul extern](ATF_Infrastructure.md) |
| Snapshot versionat de release | Dovezile unei ediții publicate, legate de payload prin hash | `release/<versiune>/` |

Folosește un titlu principal, secțiuni în ordine și blocuri de cod tipizate. O diagramă răspunde la o singură întrebare. Legenda explică muchiile, responsabilitățile și limitele. Pentru detalii secundare folosește tabele scurte sau `<details>`.

## Revizuire și guvernanță

1. Confruntă regula și comanda documentată cu sursa și verificarea responsabilă.
2. Leagă codul, exemplele și ancorele prin căi relative din proiect. Adresele web pot identifica surse publicate.
3. Păstrează mecanismele stabilizate în ghidul responsabil. Închide feedbackul după ce proba identifică sursa și criteriul.
4. Rulează regresia offline. Validatorii locali verifică inventarul, structura, diagramele, densitatea textului, căile și ancorele. [Calificarea offline](ATF_Qualification.md#guvernanța-permanentă)

Guvernanța refuză căile care ies din checkout, inclusiv când ținta există pe mașina autorului. Instantaneele istorice au inventar și hash proprii. Probele mutante verifică și un document valid în aceeași rulare. [Politica documentelor](../../config/documentation-policy.json) · [Controalele](../../atf-tests/src/test/java/atf/rentz/governance/documentation/DocumentationControlsTest.java)

Validatorii de stil și relocabilitate acoperă ghidurile active. Snapshot-urile din `release/<versiune>/` rămân imuabile după publicare și sunt validate prin hash-ul payloadului din catalogul release-ului; nu se rescriu pentru a satisface reguli editoriale introduse ulterior.

## Verificarea afirmațiilor

| Afirmație | Sursă de adevăr | Control local |
|---|---|---|
| Module și dependențe | [Reactorul Maven](../../pom.xml) și [modulele Gradle](../../rentz-lab-app/settings.gradle) | Inventarul sursei și verificările de adopție binară. |
| Comandă și selecție offline | [Colecțiile declarate](../../config/test-collections.json) și [lansatorul](../../scripts/run-rentz-components.ps1) | Contractul documentar verifică lansatoarele declarate. |
| Numărul mâinilor și ordinea nativă | [Runnerul Android](../../atf-tests/tools/run-shared-ui-sanity.ps1) și [verificatorul nativ](../../atf-tests/src/main/java/atf/rentz/distribution/evidence/NativeUiEvidence.java) | [Verificarea factuală](../../atf-tests/src/test/java/atf/rentz/governance/documentation/factual/DocumentationNativeFlowTest.java) confruntă afirmația din README și ghiduri cu numărul și primul contract din sursă. |
| Linkul APK și ediția activă | [Catalogul ediției](../../release/index.json) și [verificatorul README](../../scripts/verify-release-readme.ps1) | Catalogul verifică forma locală, iar poarta post-publicare confruntă README-ul remote și descărcarea. |

Folosește o singură afirmație vizibilă cu eticheta **Traseul Android de release** în README-ul principal, README-ul testelor și ghidurile Android, release și strategie. Celelalte explicații trimit la contractul de release. Probele resping o afirmație absentă, duplicată, greșită sau ascunsă într-un exemplu de cod.
