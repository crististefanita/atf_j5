# Strategia de testare Rentz

## Pe scurt

Verificăm fiecare cerință la cel mai mic nivel care poate observa efectul ei. Regresia offline acoperă regulile, proiecțiile, starea salvată și integritatea dovezilor. Acceptanța Android verifică traseele care cer un dispozitiv și un APK identificat.

[Componente și oracole](ATF_Components.md) · [Colecții și guvernanță](ATF_Qualification.md) · [Acceptanță Android](ATF_Android.md) · [Standardul de documentație](ATF_Documentation.md)

## Alegerea nivelului

| Nivel | Ce verifică în Rentz | Unde rulează |
|---|---|---|
| Unitar | Regula izolată, calculul sau validarea unei intrări controlate. | [Motorul](../../rentz-lab-app/game-core/README.md) și suitele JVM ale modulului responsabil. |
| Componentă | Contractul motorului, al proiecției UI, al codec-ului sau al adaptorului de raportare, cu date controlate și rezultat observabil. | Maven și Android JVM prin [colecția de regresie](ATF_Qualification.md#colecții-și-populații). |
| Integrare | Jocul simulat cu motorul și politicile reale, refacerea stării din jurnal sau reconcilierea rapoartelor cu dovezile originale. | [Arena offline](ATF_Learning.md) și [calificarea](ATF_Qualification.md). |
| E2E Android | Acțiunea utilizatorului și efectul vizibil în APK, inclusiv geometria nativă, gesturile, decontarea și reluarea după repornire. | [Traseul de release](ATF_Release.md#ordinea-calificării-android-curente) și [probele focalizate](ATF_Android.md). |

## Când mutăm o verificare

1. Identifică invariantul, intrările, calea exercitată și oracolul pentru fiecare aserțiune nativă.
2. Mută verificarea deterministă la componenta care deține comportamentul. Verifică un caz care ar eșua dacă regula dispare și un caz valid vecin.
3. Păstrează pe dispozitiv interacțiunea reprezentativă și verificările care depind de randare, navigare sau lifecycle Android.
4. Actualizează fixture-ul RAG și [controlul schimbării testelor](ATF_Qualification.md#revizuirea-unei-schimbări-de-test) împreună cu selecția. Confirmă ambele granițe înaintea calificării unei ediții.

| Invariant | Verificare la componentă | Dovadă păstrată în Android |
|---|---|---|
| Catalogul Greu conține obiectivele corecte în ordine | [AchievementFullCatalogComponentTest](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/achievements/AchievementFullCatalogComponentTest.java) confruntă populația, ordinea, etichetele RO/EN și identitățile rândurilor cu catalogul. | Traseul deschide Antrenament, aplică filtre prin dialog, verifică starea goală, deschide un rezultat și revine după resetare. |
| Help prezintă premiile Rentz pentru locurile reale | [RentzScoreContentTest](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/score/RentzScoreContentTest.java) verifică matricea de premiere pentru 3–6 jucători și descrierile RO și EN. | Sanity-ul focalizat verifică deschiderea, afișarea și accesibilitatea unor premii reprezentative, apoi revenirea în meniu. |
| Titlul se acordă numai după o partidă confirmată | [GameplayAchievementComponentTest](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/achievements/GameplayAchievementComponentTest.java) confruntă cheia salvată pentru o partidă în curs și pentru locul întâi confirmat. [SessionControllerTest](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/lifecycle/SessionControllerTest.java) verifică refuzul și după reluarea unei mâini jucate. | Traseul dovedește creditul manual, progresul asistat și păstrarea progresului după repornire. |

## Alegerea probelor Android

| Scenariu | Regresie fără dispozitiv | Când adaug proba Android |
|---|---|---|
| Trei contracte consecutive | Tranzițiile de actor și punctajul se verifică prin observații simulate. Replay-ul independent verifică comenzile și rezultatele. | Calificarea obligatorie înaintea release-ului verifică gesturile, geometria și reluarea în același APK. |
| Mese de 3, 5 și 6 locuri | JVM verifică geometria și leagă premiile de ordinea observabilă a terminării. Ierarhiile simulate verifică poziția marcajului și resping chenare suprapuse sau locuri duplicate la decontare. | `-SeatExtremes` verifică poziția randată și prima atingere în Rentz și Levate. [Controale cu mock-uri](../../atf-tests/tools/test-ui-selectors.ps1) |
| Final de mână Rentz la 3, 5 și 6 locuri | Mock-urile resping un actor grăbit, o comandă dublă, o continuare duplicată și scorul afișat înainte de decontare. Proiecția limitează indicele la banda vizibilă. | `-TableEndings` măsoară pauza reală, verifică prezența rangului și culorii în PNG și compară ruta de scor cu tabla terminală. |
| Scor compact | Proiecția verifică spațiul, ordinea locurilor și valorile cu semn. | O schimbare a poziționării, a textului sau a densității cere proba nativă focalizată. |
| Istoric, detalii și schimbarea înălțimii | Componentele acoperă modelul, punctajele, totalurile, jurnalul de 48 de mâini și rândurile din pagini suprapuse. | Modificări la scroll, atingere, schimbarea ferestrei sau integrarea antetului fix. |
| Ajutor, catalog și profil | Conținutul, filtrele, eligibilitatea și persistența au probe offline. | Modificări la navigare, tastatură, accesibilitate sau afișarea dialogului. |
| Marcaje Rentz și colectarea levatei | Motorul și proiecția verifică legalitatea, marcajele și starea salvată. | Modificări la raster, ritm, reatașarea după relansare sau așezarea cărților. |

Probele focalizate se aleg după interacțiunea afectată. Nativul păstrează gesturile, capturile și dovada reluării procesului. [Opțiunile runnerului](ATF_Android.md#livrabile-și-limite)

## Anatomia unui test auditabil

```mermaid
flowchart LR
    SETUP[Date controlate] -->|pregătesc| ACTION[Acțiune unică]
    ACTION -->|produce| ACTUAL[Rezultat observat]
    RULE[Oracol independent] -->|calculează| EXPECTED[Rezultat așteptat]
    ACTUAL -->|intră în| CHECK[Comparație și verdict]
    EXPECTED -->|intră în| CHECK
    CHECK -->|se publică în| REPORT[Extent]
```

<details>
<summary>Legendă și limite</summary>

Fixture-ul fixează intrarea și interfața exercitată. Oracolul stabilește rezultatul așteptat înaintea comparației. Extent publică observațiile și diagnosticul capturate în execuție, cu identitatea suitei și a cazului.

</details>

- Folosește același contract de verificare când rulezi cazul singur și când îl descoperă suita.
- Folosește mock-uri la frontierele procesului, stocării sau UI. Pentru reguli de joc, exercită motorul și păstrează oracolul independent.
- Păstrează în raport intrarea relevantă, așteptarea și valoarea observată. Reconcilierea cu XML verifică inventarul și rezultatul fiecărui caz.

## Rulare și dovezi

- Pentru o revizie editorială, verifică identitatea intrărilor și păstrează dovada de regresie la commitul executat. Rulează guvernanța pe noua revizie și leagă cele două dovezi prin [reconcilierea editorială](ATF_Qualification.md#reconcilierea-unei-revizii-editoriale).
- Rulează regresia offline înaintea probei native. Compară inventarul așteptat cu XML-urile Maven și Android JVM. Păstrează refuzurile și aserțiunile cu valori independente în [rapoartele componentei](ATF_Components.md#execuție).
- Runnerul componentei citește sumarul JSON al publicatorului Extent din fluxul de ieșire standard. Mesajele de diagnostic ale JVM au jurnal separat. Verdictul verifică digestul raportului și integritatea intrărilor.
- **Traseul Android de release.** 3 mâini complete, cu Rentz prima.
- RAG leagă replay-ul, capturile, versiunea și hash-ul APK-ului de test. Registrul cere amprenta jurnalului capturat înainte să elibereze rezervarea. [Poarta nativă](ATF_Release.md#ordinea-calificării-android-curente) · [Identitatea distribuirii și expunerea](ATF_Learning.md#identitatea-distribuirii-și-expunerea)
- Păstrează contractele stabile în ghidul modulului, constatările deschise în [feedback](../feedback/README.md) și artefactele datate în depozitul extern.
