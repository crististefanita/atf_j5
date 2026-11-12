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
