# Adaptorul WEB Rentz

## Pe scurt

Adaptorul livrează aceeași partidă Rentz în browser, folosind motorul J5 și shell-ul comun Card Table Browser. `GameSession` rămâne sursa regulilor, a comenzilor legale, a AI-ului și a scorului. Adaptorul transformă comenzile locale ale paginii în comenzi pentru sesiunea comună și proiectează numai informația publică; mâinile adversarilor nu sunt trimise către interfață.

```mermaid
flowchart LR
    U[Interfața WEB] -->|acțiune + revizie| P[Adaptor RentzPage]
    P -->|apelează| S[BrowserSession comun]
    S -->|comandă| G[GameSession J5]
    G -->|folosește| C[game-core Rentz]
    G -->|observație publică| P
    P -->|JSON local| U
    U -->|salvează| L[(localStorage: profil, progres, reluare)]
```

<details>
<summary>Legendă</summary>

Cartea și actorul ajung la browser prin observația publică. Sesiunea este proprietarul stării partidei.

</details>

Shell-ul comun gestionează masa, dialogurile, comenzile cu revizie, animațiile și aspectul adaptiv. J5 păstrează conținutul specific Rentz: cele opt sub-jocuri, succesiunea cărților, punctajul, profilul, realizările și istoria.

### Resursele publicate

O revizie de conținut identifică întregul pachet încărcat de browser.

| Responsabilitate | Contract |
|---|---|
| Actualizare | HTML-ul, importurile statice și dinamice, workerul, stilurile și fontul cer aceeași revizie în URL. |
| Coerență | Browserul care a deschis o ediție anterioară încarcă împreună shell-ul și adaptorul noi. |
| Integritate | Build-ul recitește manifestul și fiecare amprentă; refuză dependențele lipsă și împachetarea repetată. |
| Persistență | Profilul și sesiunea păstrează cheia locală existentă. |

Consumatorul fixează Card Table Browser 0.2.23. [Proveniența pachetului](lib/browser-build.json) leagă cele două JAR-uri de commitul Whist și de amprentele SHA-256. Fontul comun `CardTableSuits` redă simbolurile de culoare în secvența Rentz; build-ul verifică prezența fontului și a licenței sale în pachetul static.

### Reutilizarea prezentării native

`RentzCardSpace.profile` leagă ambele adaptoare de aceleași componente neutre. O modificare de contract nu schimbă forma sau scara cărții depuse.

| Responsabilitate comună | Consum în Rentz | Verificare rapidă |
|---|---|---|
| Locuri și levată | `TableGeometry.BoardSpec`, `TableSizing`, `PlayedCardMetrics`; `boardProfile` transmite explicit sloturile către renderer. | Aceleași dreptunghiuri și sloturi pentru 3–6 locuri, măsurate în AAR și în motorul Web compilat. |
| Fețe și marcaj | `CardFaceMetrics`, `HandLayoutMetrics`, `PlayedCardStyle`, `CardHighlight`. | Fixture-ul tipografic livrat în JAR-ul de teste se confruntă cu AAR-ul; funcția reală `placePlayed` primește DOM mock. |
| Scor și istoric | `RentzScoreContent` proiectează o singură dată valorile confirmate; `RentzScoreLayout` selectează rolurile Jucător/Δ:/Σ: din `ScoreGridStyle`. WEB transmite proiecția direct rendererului comun. | Aceleași rânduri, totaluri, etichete și multiplicatori în ambele adaptoare; reprezentările contradictorii sunt refuzate. |
| Meniu | `RentzGameMenu` specializează `TableMenuContent`; WEB filtrează rutele indisponibile folosind aceeași proiecție. Modulele comune `table-menu` și `profile` păstrează callbacks și închiderea. | Scor indisponibil înainte de confirmarea mâinii; istoric direct, oprire condiționată, păstrarea rutelor WEB de profil și clasamente. |
| Automat | `TurnPacing` furnizează cadența; `auto-game` deține programarea în browser. Controllerul Android păstrează reviziile și lifecycle-ul. | Ceas controlat pentru oprire, pauză, reluare și sosirea la scor; callback-ul nativ întârziat nu modifică alt ecran. |
| Secvența Rentz | Adaptorul cere banda liberă și dezactivează poziționarea unei cărți per loc. | Ancore neschimbate; cele patru șiruri folosesc geometria Rentz, independent de levată. |

Fețele depuse păstrează dimensiunea comună pe toate cele șapte sub-jocuri cu levate, la aceeași fereastră și scară de font. Numărul de locuri schimbă sloturile, nu scara feței; ferestrele foarte scunde păstrează în Android adaptarea explicită la bugetul măsurat. [Integrarea nativă](../docs/rentz/ATF_UiIntegration.md#adaptarea-secvenței-și-a-scorului-în-browser)

`RentzText` conține vocabularul portabil al jocului. `RentzUiText` îl adaptează la dialogurile native; browserul compilează modelul portabil fără clase Android. Istoricul păstrează aceeași paletă și același separator, iar măsurătorile simulate verifică ferestre compacte, înalte și font mărit; rasterizarea și gesturile se califică separat pe platformă.

### Secvența vizibilă a unei mâini Rentz

- La o mutare umană, feedback-ul comun ridică mai întâi cartea selectată.
- Adaptorul proiectează apoi același snapshot în două locuri: cartea pleacă din mâna proprie, iar slotul public din secvența Rentz devine jucat.
- Pentru fiecare carte AI, locul activ rămâne vizibil după primul cadru și înaintea intervalului de 550 ms; tabla avansează cu o singură carte la fiecare pas.
- Bifa „Joc automat” rămâne în antet pe toate cadrele de redare: adaptorul păstrează numai acțiunea-marker `auto-step` din starea anterioară, fără să expună butoanele de carte în timpul animației.
- Cărțile Rentz legale păstrează evidențierea din APK și când jocul automat este activ: conturul și umplerea folosesc tokenurile `CardHighlight.IN_PLACE` ale shell-ului comun. Modul automat dezactivează atingerea, nu și semnalizarea; regula WEB se aplică numai când secvența Rentz este pe masă.
- Snapshot-urile conțin doar mâna proprie și secvența publică, niciodată mâinile adversarilor.

- La secvență păstrăm ancorele comune ale locurilor și măsurăm banda disponibilă între casetele efective, fără o distanță fixă dedusă dintr-un singur ecran.
- `RentzPageLayout` reutilizează `TableGeometry.fullWidthCardBand` pentru secvența cu 3–6 locuri. Rectangulele jucătorilor și geometria mâinii rămân exact cele comune; levatele păstrează centrul rezervat de `BoardSpec`.
- Rândurile citesc `clientWidth` și `clientHeight`, înainte de transformarea comună a scenei. Scalarea paginii se aplică o singură dată, inclusiv fonturilor indicilor.
- Inset-urile specifice se aplică exclusiv secvenței Rentz; celelalte sub-jocuri consumă sloturile și dimensiunile comune.

- Înainte de prima randare, adaptorul validează întregul lot de snapshot-uri.
- Fiecare pas trebuie să schimbe exact un slot liber în carte jucată; la mutarea umană, aceeași carte trebuie să dispară din mâna proprie, iar mutarea adversarului nu poate modifica mâna umană.
- Locul activ trebuie să corespundă actorului, iar payload-ul nu poate conține colecții de mâini adverse.
- Dacă un cadru este invalid, lotul este refuzat integral: tabla nu ajunge într-o stare WEB parțial actualizată.

```mermaid
sequenceDiagram
    participant J as Jucător
    participant W as Shell WEB comun
    participant A as Adaptor Rentz
    participant S as Sesiune Rentz
    J->>W: Selectează o carte legală
    W->>W: Ridică cartea
    W->>A: Trimite acțiunea și revizia
    A->>S: Aplică mutarea și avansează AI-ul
    S-->>A: Snapshot-uri publice înainte/după fiecare carte
    A-->>W: Playback ordonat, fără mâini adverse
    W->>W: Elimină cartea proprie și actualizează secvența
    loop Pentru fiecare carte AI
        W->>W: Marchează locul activ și așteaptă cadrul + 550 ms
        W->>W: Afișează următorul slot Rentz
    end
    opt Mâna Rentz s-a încheiat
        W->>W: Arată sosirea 1,8 s, apoi scorul confirmat
    end
```

<details>
<summary>Legendă</summary>

Săgețile punctate întorc observațiile publice. Așteptarea fixează ritmul redării cărților AI, iar scorul confirmat încheie mâna.

</details>

## Contractul de compatibilitate cu APK-ul

- Adaptorul WEB nu implementează o a doua versiune a jocului.
- Testele compilează aceleași surse `game-core` și `GameSession` pe care le folosește APK-ul, apoi verifică stratul de browser la granița publică.
- Sursa WEB proprie `PlayerNames` există numai în compilarea TeaVM: înlocuiește apelurile Unicode indisponibile în acel runtime, iar testul ei compară rezultatele direct cu biblioteca de nume folosită de APK.
- Codul nativ și APK-ul nu sunt schimbate de această adaptare.

### Scor și clasament comune

- `ScoreSettlement` păstrează ordinea de sosire la Rentz; la celelalte sub-jocuri păstrează ordinea locurilor.
- Fiecare rând de rezultat adaptează delta, totalul confirmat și contribuțiile din istoricul motorului.
- Rezultatul folosește rendererul comun de punctaj: un rând pentru fiecare jucător și coloanele Jucător, P, T, cu text normal și legendă imediat sub tabel. Contribuțiile exacte rămân în „Detalii de punctaj”.
- Istoricul complet păstrează două coloane Δ:/Σ: pentru fiecare loc și identifică mâna împreună cu sub-jocul. `rentz-score.mjs` adaptează cele două prezentări fără să recalculeze puncte.
- `ScoreStandings` aplică departajarea competitivă și eticheta totalului final folosite de APK.
- Testele compară adaptoarele Android și WEB cu rezultatul motorului pentru mese de 3–6 jucători și verifică un singur zero la Rentz.

### Levatele folosesc animația comună

Pentru toate sub-jocurile cu levate, `RentzTable` proiectează fiecare carte acceptată într-un `PlayStep`, inclusiv cartea proprie. Rendererul comun `playCards` marchează actorul, depune cărțile în ordinea jocului și păstrează masa completă până la colectare. Mutarea manuală este imediată; o mutare automată păstrează ritmul `TurnPacing`.

```mermaid
flowchart LR
    A[Actor marcat] -->|depune| B[O carte depusă]
    B -->|verifică| C{Levată completă?}
    C -->|Nu| A
    C -->|Da| D[Toate fețele vizibile: 1.000 ms]
    D -->|colectează| E[Colectare la câștigător: 500 ms]
    E -->|continuă| F[Levată nouă sau punctaj]
```

<details>
<summary>Legendă</summary>

Duratele provin din `TrickCollectionMotion`, aceeași bibliotecă folosită de Android. Secvența Rentz rămâne distinctă: păstrează șirurile pe masă și folosește sosirea finală, fără colectare de levate.

</details>

Componentele verifică ordinea evenimentelor, toate pozițiile jucătorului propriu, câștigătorul și temporizarea la 3–6 locuri. Testul cu ceas și DOM simulat execută funcția comună extrasă din pachet, inclusiv cu mișcare redusă; numai finalizarea colectării permite golirea mesei. Proba MCP confirmă fețele și tabelul realmente randate.

| Strat | Verifică fără emulator | Ce protejează |
|---|---|---|
| Componente JVM | Validarea configurării comune; 3–6 jucători și Normal/Greu; reguli, acțiuni legale, treceri automate, rezultate și istoric; profil, realizări și checkpoint; contractele de nume și punctaj; ordinea snapshot-urilor Rentz și cele 550 ms după primul cadru al locului activ, comparate cu biblioteca UI Android fixată | Motorul, regulile și ritmul de decizie rămân aliniate cu APK-ul, iar proiecția WEB nu schimbă rezultatul sesiunii |
| Paritate cu biblioteca APK | NFC, limita de zece puncte Unicode, intrări valide/invalide și lista deterministă de adversari, comparate cu JAR-ul `card-table-names`; profilul TeaVM verifică și zece litere Unicode din afara planului de bază, apoi respinge a unsprezecea; normalizarea se compară cu sursa Android `DisplayName` | Shim-urile necesare TeaVM nu schimbă contractul de profil Android |
| Componente pentru API-ul TeaVM și adaptorul DOM | Pornire pentru 3–6 locuri și Normal/Greu; cărți ascunse până la dezvăluire; alegerea Rentz; ancora de deschidere pentru fiecare dimensiune de masă; mutări legale; o mână Rentz completă; cadrele reale ale motorului trec prin adaptorul DOM cu randare mock; placa finală coincide cu starea confirmată; sosirea Rentz înaintea scorului; punctaj, progres, reluare și următoarea mână | Motorul compilat și secvența WEB reală respectă contractele APK fără DOM sau emulator |
| Componente DOM și transport local | Validarea atomică a tuturor snapshot-urilor; identitatea cărții mutate din mână pe secvență; mâna neschimbată la mutările adverse; locul activ înaintea întârzierii; acțiunile legale din mână identice cu sloturile legale din secvență înainte și după `auto-step`; evidențierea legală în mod manual și automat; bifa „Joc automat” prezentă la fiecare cadru; geometria centrului la 3–6 locuri; selectorul și antetul contractului selectat; sosirea Rentz de 1,8 secunde și revenirea la scor | Un payload inconsistent nu poate produce randare parțială; cardurile legale rămân marcate când automatizarea oprește atingerea, iar checkbox-ul nu clipește în redare; spațiul la patru locuri crește fără să schimbe ancorele comune; shell-ul nu afișează panoul Whist „Atu” |
| Verificare în browser MCP | Patru locuri, Greu, Rentz complet și încă un sub-joc complet; Δ:/Σ: în rezultat și istoricul ambelor mâini; cumul pe fiecare loc, reluare prin reîncărcare și cărți depuse pe ecran compact și înalt | Integrarea dintre controalele reale, browser, worker, persistență și artefactele construite |

- Build-ul verifică hash-urile JAR-urilor comune fixate.
- Testul `hand-presentation.mjs` din pachetul de teste comun este rulat cu setup-ul Rentz: verifică hint-ul pentru o atingere greșită, ridicarea cărții legale, anularea și aplicarea unică a mutării, fără browser sau emulator.
- Build-ul execută și contractele comune de meniu și tipografie, comparația AAR–TeaVM a mesei și testele modulului comun AutoPlay. Mock-urile înlocuiesc numai măsurarea DOM și așteptarea; geometria și programatorul rămân cele livrate de bibliotecă.

- Aceste verificări înlocuiesc verificările de logică, stare, alegeri AI, scor, istoric, profil și realizări care altfel ar fi repetate prin emulator.
- Se folosesc aceleași surse de motor, aceleași comenzi `GameSession` și același JAR nativ de nume ca bază de comparație; testele nu simulează sau injectează rezultatele partidei.

- La o acțiune Rentz, adaptorul trimite snapshot-uri publice pentru cartea umană și fiecare carte AI din acel răspuns.
- Shell-ul redă fiecare pas în ordinea jocului, iar antetul folosește cele două rânduri Android — progresul mâinii și contractul ales — fără titlu duplicat sau panou de atu.
- Bifa de joc automat rămâne vizibilă și în cadrele intermediare, inclusiv când următorul actor este un adversar; în scurta pauză de sosire rămâne vizibilă, dar este dezactivată.
- La sosirea terminală automatizarea se oprește pentru a păstra vizibil scorul. La încheiere, adaptorul păstrează ultimul snapshot cu secvența completă pe masă, prezintă sosirea timp de 1,8 secunde, apoi afișează scorul confirmat.
- Playback-ul este tranzitoriu și nu intră în checkpoint sau profil; reluarea unei partide terminate afișează direct scorul.

- Rămân dependente de un dispozitiv Android numai proprietățile platformei native: instalarea APK-ului, lifecycle-ul și restaurarea prin sistemul Android, inset-urile și fonturile furnizorului, gesturile tactile reale și performanța pe hardware.
- Ele nu sunt criterii pentru validarea logicii jocului în browser.
- Pagina WEB se califică separat prin testele de browser și prin publicarea reală.

## Compilare și verificări locale

Necesită PowerShell 7, JDK 21, Maven și Node.js 24. Toate clasele generate, cache-urile și paginile construite se scriu într-un director extern checkout-ului.

```powershell
$env:RENTZ_WORK_ROOT = '<director extern pentru artefactele Rentz>'
$components = Join-Path $env:RENTZ_WORK_ROOT 'browser-components'
$pages = Join-Path $env:RENTZ_WORK_ROOT 'browser-pages'
.\atf-ui-browser\Test-RentzBrowserComponents.ps1 -WorkRoot $components
.\atf-ui-browser\Build-RentzPages.ps1 -WorkRoot $pages
```

`Test-RentzBrowserComponents.ps1` rulează testele JVM ale adaptorului și ale sesiunii comune, apoi extrage runtime-ul și testul versionate și verifică toate cele 51 de cazuri din contractul de viewport. Build-ul WEB rulează, în ordine, testele JVM ale endpoint-ului, comparația cu biblioteca APK, compilarea TeaVM, verificările Node asupra motorului generat și verificările DOM. O eroare la oricare strat oprește build-ul.

### Acceptanța WEB prin browser MCP

[Fluxul reutilizabil](tests/rentz-web-flow.mjs) primește TAB-ul MCP și un callback care salvează captura JPEG originală și checkpoint-ul în directorul extern al rulării. Comenzile trec numai prin controalele publice. Scriptul nu injectează distribuiri, scoruri sau rezultate.

| Pas | Verificare |
|---|---|
| `start()` | Configurează patru locuri, Greu, dezvăluie cărțile și alege Rentz din dropdown. |
| `waitForScore()` → `score(1)` | Așteaptă sfârșitul real și confruntă premiile 300/200/100/0, perechile Δ:/Σ: și detaliile. Așteptarea este limitată la 40 de secunde pe apel. |
| `next()` → `waitForScore()` → `score(n)` | Termină următorul sub-joc; verifică identitatea și cumulul. Continuă până există și un sub-joc distinct de Rentz. |
| `history()` → `restore()` | Confruntă toate rândurile, numele și legenda înainte și după reîncărcare. |
| `geometry(titlu)` | Măsoară DOM-ul vizibil cu cărți depuse: patru rânduri, patru locuri, checkbox prezent și nicio intersecție cu jucătorii. |

AI-ul poate alege legitim din nou Rentz pentru alt alegător. În acel caz, etapa pentru un sub-joc distinct se continuă prin controalele normale; repetarea legală nu este defect de produs și nu califică singură scenariul mixt.

Verificările numerice extinse, cazurile de refuz și matricea 3–6 locuri rămân în componente. DOM-ul mock folosește chiar rendererul din JAR; motorul compilat joacă două mâini consecutive și verifică istoricul după restaurare. `component-comparisons` păstrează comparațiile evaluate de build pentru raportare, fără reconstruirea verdictului din numărul de linii al logului.

[Publicatorul Extent](tests/atf/rentz/browser/reporting/RentzWebAcceptanceReport.java) consumă comparațiile și checkpoint-urile.

- Folosește biblioteca Reporting declarată de checkout-ul calificat.
- Păstrează originalele JPEG/PNG; pentru contractul PNG al bibliotecii reîncodifică numai pixelii decodați, fără corecție vizuală.
- Distinge candidatul local, pagina publică, browserul mobil offline și calificarea APK.

## Pornire locală

Folosiți lansatorul și un build extern:

```powershell
$env:RENTZ_WORK_ROOT = '<director extern pentru artefactele Rentz>'
$local = Join-Path $env:RENTZ_WORK_ROOT 'browser-local'
.\atf-ui-browser\Start-RentzBrowser.ps1 -WorkRoot $local
```

## Publicare

Workflow-ul `.github/workflows/rentz-pages.yml` publică pachetul static din `main` numai după ce trec testele JVM native și verificările adaptorului și ale motorului compilat. Profilul și reluarea rămân în `localStorage`, pe dispozitivul browserului; pagina nu trimite datele într-un serviciu extern.

README-ul principal va lega jocul online după ce workflow-ul publică pagina și URL-ul trece verificarea anonimă. Un build local sau un URL de preview nu este adresă publică de joc.

## Surse

- [Regulile și arhitectura motorului Rentz](../rentz-lab-app/game-core/README.md)
- [JAR-ul comun Card Table Browser](lib/atf-ui-browser-0.2.23.jar) · [checksum](lib/atf-ui-browser-0.2.23.jar.sha256)
- [Testul comun de componentă](lib/atf-ui-browser-0.2.23-tests.jar) · [checksum](lib/atf-ui-browser-0.2.23-tests.jar.sha256)

### Textul scorului și continuitatea comună

- Textele scorului Web folosesc scara comună 1,5 o singură dată. Rolul dialogului de calcul rămâne explicit în adaptor; regulile și valorile provin din Rentz.
- JAR-ul de teste livrează și `shared-presentation.test.mjs`: constructorul real, ordinea AI/jucător, colectarea și tipografia în Chromium. Probele specifice Rentz păstrează oraclele celor șapte contracte de levate și ale secvenței.
- Contractele portabile și comparațiile DOM nu înlocuiesc acceptanța pixelilor Android. Artefactul Android nu se schimbă prin actualizarea JAR-ului Web.
