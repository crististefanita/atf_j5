# Integrarea nucleului UI comun

## Pe scurt

Whist publică nucleul Android ca AAR și shell-ul Web ca JAR. Ambele folosesc contracte comune de geometrie, cărți și tranziții, iar adaptoarele Rentz furnizează textele, datele, comenzile și regulile jocului. Renderer-ele rămân specifice platformei; profilele și comportamentul observabil se califică separat, prin componente și acceptanță.

## Frontiera consumatorului

| Intrare comună | Responsabilitatea Rentz |
|---|---|
| `HomeMenuContent` și `HomeMenu` | Texte RO/EN, starea reluării și callback-uri către rutele reale; primul profil și preferințele sunt ale aplicației. |
| `PlayerNameDialog` și `NameEdit` | Politica numelui, identitatea locului și salvarea; erorile și resetarea păstrează semantica declarată. |
| `ScreenViews` | Etichetele selectorilor și protecția ferestrei, furnizate explicit; aceeași geometrie și aceleași stiluri ale widgeturilor. |
| `GameTitleBar` | Măsoară spațiul pentru acțiune, titlu și controlul opțional. Adaptorul `RentzGameHeader` furnizează progresul mâinii, sub-jocul, textele RO/EN și starea AutoPlay. |
| `atf-ui-browser` | Shell-ul și contractul de viewport pentru Web; adaptorul Rentz furnizează conținutul, comenzile și stocarea profilului. Randarea rămâne specifică platformei, iar contractele de geometrie și interacțiune sunt comune. |
| `CardFace` și `CardFaceDrawable` | Proiecție numai după dezvăluirea permisă; fără obiecte Whist, mâini adverse sau seed. |
| `HandLayoutMetrics` | Opt cărți per loc în modelul curent, o carte centrată și tratamentul explicit al mâinii goale. |

Masa, secvențele, alegerea sub-jocului și scorul folosesc adaptoarele proprii din atlasul prezentării. Biblioteca de produs nu introduce Core/Reporting/Kit ATF în APK.

Manifestul Rentz adoptă `CardTableTheme`; J4 păstrează aliasul echivalent `AppTheme`. Fereastra, rutele și preferințele aparțin consumatorului.

## Admiterea bibliotecilor de produs

1. Citiți coordonatele și amprentele din [proveniența bibliotecilor UI](../../rentz-lab-app/ui-library/provenance.json) și confruntați-le cu [declarațiile Gradle](../../rentz-lab-app/app/build.gradle).
2. Confruntați AAR, JAR, POM, metadata, surse și SHA-256. Păstrați dependențele declarate și compatibilitatea bibliotecii `minSdk 24` cu aplicația `minSdk 26`.
3. Folosiți o copie în [workspace-ul înregistrat](ATF_Infrastructure.md), fără build sau probe în sursa activă ori TEMP-ul Windows.
4. În copia tehnică, declarați repository-ul și dependența de mai jos; rulați componentele JVM și construiți APK-ul pentru verificarea D8/manifest.
5. Păstrați separat dovada admiterii de acceptanța meniului și mesei reale; un tip disponibil în classpath nu demonstrează randarea widgetului.

Fragmentul activ pentru `dependencyResolutionManagement.repositories` izolează dependența de produs:

```groovy
exclusiveContent {
    forRepository { maven { url = uri('ui-library') } }
    filter { includeGroup 'ro.cardtable' }
}
```

Dependența se declară în modulul aplicației, după admitere:

```groovy
implementation 'ro.cardtable:card-table-ui:0.1.20'
implementation 'ro.cardtable:card-table-names:0.1.0'
```

Aceste intrări sunt active în configurația live. [Repository-ul admis](../../rentz-lab-app/ui-library/README.md) păstrează artefactele și hash-urile; testele refuză dependența de motorul Whist.

## Componente și acceptanță

- Verificați proiecțiile pe date Rentz reale, callback-uri unice, limbă și nume, legalitatea comenzii și tratamentul refuzului.
- Păstrați componentele lifecycle-ului, codec-ului și sesiunii; adaptarea UI nu le înlocuiește și nu schimbă salvarea privatului în date de prezentare.
- Comparați geometria și textele cu reperul comun, cu fixture-uri și oracole independente; declanșarea unui callback în JVM nu este tap Android.
- Adăugați numai sanity-ul nativ necesar pentru meniu/profil, mâna și scorul efectiv, după trecerea componentelor. Rezultatele, APK-urile tehnice și logurile rămân externe.

[Backlog-ul UI](../feedback/ATF_Rentz_UI_ToDo.md) deține criteriile rămase. [Calificarea offline](ATF_Qualification.md) și [acceptanța Android](ATF_Android.md) păstrează identitățile și limitele execuției.

### Matricea de afișaj

| Parametru | Selecție pentru componente |
|---|---|
| Lățime utilă | 320, 360, 393, 412, 600 și 800 dp; o lățime în pixeli nu înlocuiește măsurarea în dp. |
| Orientare | Portret și peisaj, inclusiv spațiul redus de barele sistemului. |
| Scala fontului | 1,0 / 1,3 / 2,0. |
| Conținut | Mese de 3–6 jucători, opt cărți proprii, tablă completă și până la 48 de mâini în istoric. |

La limita lizibilității se alege un mod explicit de derulare sau o dispunere adaptată. Nu micșorați nelimitat textul și nu eliminați jucători, valori sau cărți pentru a face testul să treacă; țintele interactive păstrează minimum 48 × 48 dp, separat de cărțile doar informative, conform [recomandării Android pentru accesibilitate](https://developer.android.com/guide/topics/ui/accessibility/apps).

[Ghidul Android pentru layout-uri adaptive cu Views](https://developer.android.com/develop/ui/views/layout/responsive-adaptive-design-with-views) fundamentează adaptarea la spațiul disponibil. Dimensiunile matricei sunt selecția de testare a proiectului.

## Componente comune și oracole Rentz

| Comun, fără reguli | Rămâne în consumator |
|---|---|
| `HomeMenuContent`, dropdown și editor prin porturi | Texte, preferințe, reguli de nume, rute și salvări Rentz. |
| `SeatContent`, `PlayerSeatFrame`, `BoardLayout` | Nume, actor efectiv, puncte/delte și mapare loc motor → loc vizual. |
| `HandCard`, `HandFan`, `CenteredHandView` | Vizibilitate înainte de reveal, legalitate, pas și aplicarea unică a comenzii Rentz. |
| `PlayedCardContent`, `PlayedCardsView` | Levata publică și momentul colectării; tabla de secvențe este altă compoziție a centrului. |
| `CollectionTarget` și animatorul comun | Adaptorul Rentz identifică o levată nouă din evenimentele publice, rezolvă câștigătorul și păstrează termenul la redimensionare. |
| `PlayerNames` Java | Sesiunea exclude numele uman la pornire și proiectează numele AI după loc. Jurnalele vechi reconstruiesc lista din seed. O excludere diferită poate salva lista AI. |

Testele de licitație, atu și punctaj Whist rămân exclusiv în J4. J5 implementează propriile componente pe observații și comenzi Rentz reale; opt cărți pe loc, faza nevăzută, pasul, secvențele și scorul nu se testează prin redenumirea fixture-urilor Whist.

Modelele UI păstrează date vizibile neutre. În Rentz, componentele acoperă mese de 3–6 locuri, iar calificarea nativă declară separat configurația executată.

### Nume, scoruri și ordinea mâinii

| Contract | Oracol |
|---|---|
| Chenar jucător | Numele și cele două rânduri de scor rămân întregi. Matricea acoperă font până la 2×, diacritice, geometrie și valori negative lungi. |
| Nume adversari | Pentru 3–6 locuri, selecția comună rezervă numele uman, iar restaurarea păstrează ordinea locurilor și a numelor. Jurnalul vechi folosește seed-ul. |
| Mână vizibilă | Ordinea este ♠ ♥ ♣ ♦, apoi rang crescător. Proiecția păstrează identitatea și legalitatea; proba nativă verifică ordinea fizică și centrajul ultimei cărți. |

[Componentele mâinii](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/table/RentzTableContentTest.java)

### Marcajul cărților jucabile în Rentz

În tura proprie din sub-jocul Rentz, toate cărțile legale primesc fundal crem și contur auriu. Marcajul vine din observația motorului și se păstrează când tura este delegată. Atingerea rămâne disponibilă numai când jucătorul controlează tura.

Componenta comună aplică stilul cărților marcate. În tura manuală, o atingere respinsă colorează temporar cartea aleasă și evidențiază toate opțiunile legale. Componentele confruntă setul marcat cu regulile după mutare manuală sau delegată, după pas și la schimbarea turei.

Proba `-RentzCardMarksOnly` verifică pe emulator acțiunile accesibile și culorile capturate pentru o carte, mai multe cărți, după mutare și după pas. [Componentele de geometrie](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/table/RentzCardSpaceTest.java) · [Runner Android](../../atf-tests/tools/run-shared-ui-sanity.ps1)

### Schimbarea contractului la masă

- Indicatorul de tură rezervă înaintea poziționării înălțimea maximă măsurată a etichetelor Alege, Joacă și Pasează, în limba și la scara textului curente. Schimbarea numărului de rânduri nu mută chenarele.
- Meniul comun păstrează accesul direct la istoricul scorului, apoi acțiunile specifice fazei; oprirea AutoPlay rămâne disponibilă numai când automatizarea este activă. Construirea meniului nu execută nicio comandă.
- Alegerea sub-jocului și jocul păstrează aceleași dreptunghiuri ale locurilor pentru același ecran. Masa măsoară locurile o singură dată pe cadru, apoi adaptează comenzile de alegere, secvența sau levata la banda rămasă. Indicatorul Alege, Joacă sau Pasează arată actorul curent deasupra chenarului.
- Rendererul WEB păstrează elementul chenarului pentru fiecare identitate de loc la trecerea AI → jucător. Pregătește noul conținut separat, apoi aplică geometria măsurată, faptele și actorul într-un singur commit; redimensionarea rămâne permisă, iar o masă incompatibilă primește elemente noi.
- Pe un ecran scund, levata reduce mai întâi distanța dintre rânduri și păstrează ordinea cărților. Bugetul se verifică în pixeli, cu aceeași rotunjire separată a poziției și a înălțimii ultimului rând ca la randarea Android.
- Masa și ecranul de punctaj afișează progresul `Mâna x/y` pe primul rând și contractul pe rândul următor. Marcajul `×2` rămâne lângă contract.
- Adaptorul browser folosește aceleași două rânduri după selectarea sub-jocului; ascunde titlul duplicat și panoul de atu din shell-ul comun Whist. Selectorul WEB păstrează numele de contract RO/EN din motor.

Componentele verifică geometria pe dimensiuni controlate și premiile pe locuri. Mock-urile refuză suprapunerea chenarelor, un marcaj atribuit altui actor și un clasament cu locuri duplicate sau străine. [Geometrie](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/table/RentzCardSpaceTest.java) · [Punctaj](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/score/RentzScoreContentTest.java) · [Mock-uri](../../atf-tests/tools/test-ui-selectors.ps1)

Acceptanța de release compară dreptunghiurile mesei de patru locuri înainte și după schimbarea contractului, după mutări și după reluare. Proba focalizată compară selectorul și prima atingere în Rentz și Levate la 3, 5 și 6 locuri. [Poarta de release](ATF_Release.md#ordinea-calificării-android-curente) · [Proba focalizată](ATF_Android.md#livrabile-și-limite)

### Decontarea unei secvențe Rentz

- Actorul automat primește un cadru propriu înaintea întârzierii de 550 ms. Logul APK-ului izolat leagă cadrul, pornirea și revizia comenzii confirmate. [Ciclul de viață](ATF_Lifecycle.md)
- În WEB, aceeași acțiune produce snapshot-uri publice înainte și după fiecare carte. Înainte de randare, adaptorul validează întregul lot: actorul corespunde locului activ, fiecare pas adaugă exact o carte unică, cartea umană pleacă din mâna proprie în aceeași tranziție, iar mutările adverse păstrează mâna umană. Payload-ul nu poate conține mâini adverse; orice abatere respinge lotul înainte de prima randare, fără stare parțială. Pentru AI, indicatorul locului precedă intervalul de 550 ms, iar bifa „Joc automat” rămâne vizibilă prin păstrarea acțiunii-marker din starea anterioară; în pauza de sosire rămâne vizibilă, dar dezactivată. La patru jucători, secvența folosește banda liberă calculată din planul comun; ancorele locurilor nu se modifică. La ultima carte, snapshot-ul final rămâne vizibil pe durata sosirii de 1,8 secunde, apoi apare scorul. Componentele verifică ordinea, sloturile, checkbox-ul, geometria compactă, durata și lipsa mâinilor adverse înainte de orice verificare în browser.
- La terminare, cadrul sosirii prezintă câștigătorul și totalul public al cărților neplasate. Fereastra de 1,8 secunde începe la prima randare. Chenarele păstrează totalurile anterioare până la scor.
- Tabla terminală păstrează cărțile jucate și sloturile libere. În zona suprapusă, indicele lizibil se termină la marginea următoarei cărți jucate.
- Ecranul de punctaj afișează mâna confirmată. Revenirea la tablă păstrează revizia și continuarea. [Proba Android](ATF_Android.md#livrabile-și-limite)

## Traseul minim de calificare

1. Rulați componentele aplicației prin `scripts/test-rentz-android-jvm.ps1`, cu workspace și cache externe înregistrate.
2. În snapshot-ul calificat, executați `:app:testDebugUnitTest`, `:app:lintDebug` și `:app:assembleUiSanity`; pentru comparațiile UI furnizați `-Prentz.uiEvidence=<director-extern>`.
3. Verificați semnătura, pachetul `ro.rentzlab.app.uisanity`, SHA-256 și identitatea surselor. Varianta izolată nu înlocuiește aplicația utilizatorului sau APK-ul de release.
4. Rulați `atf-tests/tools/run-shared-ui-sanity.ps1` cu `-BuildQualification` și un `-RunRoot` nou. Rezervarea atomică precede ADB; traseul acoperă un profil de afișaj și o singură mână, apoi cleanup-ul propriu.
5. Publicați comparațiile capturate, acțiunile native, ierarhiile și PNG-urile prin `RentzUiIntegrationReport`, compilat în source-set-ul Maven de test. Verificați expandarea raportului și integritatea imaginilor.

Un JDK nou și compileSdk 36 nu garantează disponibilitatea API-ului Java pe Android 28. [API-urile Java și desugaring](https://developer.android.com/studio/write/java8-support) au un contract separat; adaptorul folosește colectorul portabil imutabil, iar Lint și proba nativă protejează această frontieră.

Receipt-ul probei UI este distinct de cel al release-ului și se produce numai după verificarea rezultatelor efective:

| Câmp | Sursă verificată |
|---|---|
| `status`, `appTests`, `newAuditedCases` | XML Gradle fără eșec/skip și inventarul cazurilor auditate. |
| `apk`, `applicationId`, `apkSha256`, `apkBytes` | APK-ul `assembleUiSanity`, manifestul binar, semnătura și hash-ul fișierului complet. |
| `stage`, `sourceManifestSha256` | Snapshot extern înregistrat și inventar confruntat înainte/după build. |
| `assertionDirectory`, `capturedComparisons` | JSON-urile comparațiilor capturate în execuția finală, fără populație goală. |
| `uiLibrary`, `lintErrors`, `lintWarnings` | Proveniența AAR-ului și rezultatul Lint al acelorași intrări. |

Varianta izolată are receipt propriu. Runnerul curent folosește alegeri scriptate pentru un traseu repetabil și verifică rezultatele prin replay independent. Decizia externă se probează prin observația actorului și CLI. [Domeniul celor două rute](ATF_Android.md#livrabile-și-limite)

## Auditul cititorului Extent

- [Driverul focalizat](../../atf-tests/tools/run-trick-collection-sanity.ps1) execută ultima mutare legală și reia mâna după oprirea procesului. Înregistrați fixture-ul de dezvoltare înainte de a-l expune emulatorului.
- [Analiza cadrelor](../../atf-tests/tools/analyze-trick-collection.mjs) măsoară timestamp-urile, centrele cărților și apropierea de chenarul câștigător. Extent include cadrele și hash-urile înregistrărilor.

- `-CompactScores` verifică încadrarea valorilor pe două rânduri la font 1,5 și reluarea aceleiași sesiuni după schimbarea limbii. [Componentele mesei](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/table/RentzTableContentTest.java) verifică valorile și descrierile fiecărui loc la 3–6 jucători, în ambele limbi.
- `-HelpAwardsOnly` verifică navigarea, afișarea și accesibilitatea unor premii reprezentative în ambele limbi. Matricea completă a premiilor și descrierilor pentru 3–6 jucători se verifică la [componentă JVM](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/score/RentzScoreContentTest.java).
- `-HistoryDetails` confruntă profilul jurnalului și premiile locurilor confirmate.

- Spark grupează metodele sub clasă: confruntați atât populația metodelor, cât și suma comparațiilor; numărul intrărilor din stânga nu este numărul testelor.
- Folosiți controlul standard de expandare, apoi disclosure-urile acțiunilor, aserțiunilor și corpurilor. Toate rândurile trebuie desfăcute, iar ultimul trebuie accesibil prin derulare reală.
- Captura este un link către viewer, nu un `<img>` permanent în tabel. Capturile identice pot folosi referințe; verificați PNG-ul rezolvat față de original, decodarea, încadrarea și închiderea completă a viewer-ului înaintea următorului click.
- Verificarea cititorului poate utiliza Chromium offline; acest lucru nu schimbă proveniența acțiunilor Android, executate prin ADB și UI Automator.

## Realizări și profil local

- Meniul **Realizări** prezintă rezultatele confirmate la masă, grupate în trei niveluri. Ordinea recomandă un traseu, iar fiecare obiectiv avansează independent.
- După salvarea punctajului, un panou temporar sub antet grupează realizările avansate în mâna respectivă. Când apar deblocări și progres parțial, afișează câte un rând din fiecare și numărul celorlalte actualizări. La ultima mână include obiectivele confirmate la clasamentul final.
- Panoul se închide automat sau prin butonul de închidere. Reafișarea punctajului și reluarea unei partide salvate păstrează progresul fără repetarea notificării.
- Recomandarea ține cont de numărul de jucători și de dificultatea partidei curente, inclusiv înainte de prima mână. Obiectivele care cer altă configurație rămân vizibile cu regula lor. Filtrul după contract afișează tacticile contractului și obiectivele de varietate sau de scor la care contribuie.
- **Antrenament** păstrează 800 de obiective și filtrele lor. Catalogul include 500 de obiective Ușor, 200 Mediu și 100 Greu. Identificatorii și dovezile deja salvate rămân valabile când se schimbă ecranul din care sunt accesate.
- Profilul salvează o identitate aleatorie și fusul orar ales la creare. Numele afișat se poate schimba separat. Sincronizarea între dispozitive și comutarea între profiluri rămân în plan.
- Progresul se confirmă la închiderea mâinii și se scrie atomic împreună cu partida. Jocul manual, delegarea și pasul obligatoriu au surse distincte. Jurnalele vechi păstrează creditul confirmat, iar obiectivele adăugate încep să numere de la următoarea mână confirmată.
- Obiectivele pe zile folosesc fusul salvat și cer o distribuire distinctă pentru fiecare zi numărată. Ora provine din dispozitiv.
- Căutarea recunoaște titlul și regula în română sau engleză, inclusiv când lipsesc diacriticele. Filtrele se combină și păstrează ordinea de învățare. Rândurile spun explicit dacă obiectivul este blocat, în progres sau deblocat.
- Ecranul folosește verdele tablei și paleta locurilor la masă. Cartonașele deblocate au fundal crem și text de stare, iar bara din antet arată progresul total.
- Lista virtualizată, fereastra de detaliu și panoul de progres sunt randate de `card-table-ui`. Rentz furnizează ordinea, textele, eligibilitatea, filtrele și datele deja confirmate.
- Detaliul folosește aceeași evaluare ca acordarea creditului. El arată criteriul, tipul dovezii și ce a lipsit din ultima mână a partidei curente. O mână eligibilă poate avea deja credit dacă dovada a mai fost înregistrată.
- Clasările din Realizări folosesc rezultatul terminal Rentz. Egalitățile păstrează locul comun. O partidă integral manuală cere decizii manuale confirmate de la început și permite pasul automat obligatoriu.
- Testele cu mâini simulate verifică ordinea, pragurile, proveniența și reluarea. Partidele offline verifică disponibilitatea obiectivelor pe loturi separate. Rularea nativă are puncte distincte pentru Realizări și Antrenament.

[Catalogul executabil](../../rentz-lab-app/app/src/main/achievement-rules/rentz-gameplay-achievements.yaml) · [Planul calibrării](../../rentz-lab-app/config/rentz-achievement-calibration.yaml) · [Componentele Realizări](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/achievements/GameplayAchievementComponentTest.java) · [Extinderi și acceptanță](../feedback/ATF_Rentz_Achievements_ToDo.md)

## Clasamentul final

- La `MATCH_END`, ecranul de punctaj afișează înaintea detaliilor ultimei mâini numele, locul și totalul confirmat pentru fiecare jucător.
- Sortarea este numerică, descrescătoare. Totalurile egale au același loc și păstrează ordinea locurilor la masă. Următorul loc ține cont de numărul jucătorilor aflați la egalitate.
- Clasamentul se calculează din totaluri la afișare. Istoricul confirmat și ordinea mesei rămân stabile după reluare.

[Componentele clasamentului](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/score/RentzScoreContentTest.java) acoperă egalitățile, scorurile negative și cele patru mărimi de masă. [Acceptanța terminală](../feedback/ATF_Rentz_UI_ToDo.md#p1--geometrie-secvențe-și-scor) completează traseul de release cu trei mâini.

## Istoricul adaptiv Rentz

Grila folosește lățimi măsurate, subcoloane aliniate și antet fix. Coloana fixă arată numărul mâinii, sub-jocul, alegătorul și marcajul ×2. Grupurile de scor arată **Δ:** pentru mână și **Σ:** pentru totalul confirmat.

| Proprietar | Contract |
|---|---|
| Biblioteca comună | `ScoreTableView` și `ScoreHistoryView` dețin randarea Android, măsurarea coloanelor și sincronizarea derulării. |
| Rentz | `RentzScoreContent` proiectează modelele portabile `ScoreTableContent` și `HistoryContent`, cu etichetele și punctajul jocului. |
| Adaptoare | Android păstrează ID-urile și callback-ul mâinii; WEB traduce aceleași modele în protocolul rendererului comun. |
| Calificare | Componentele Android verifică octeții încărcați din AAR; componentele ATF compară separat amprentele modelelor portabile din receipt-urile Android și WEB. |

| Situație | Comportament |
|---|---|
| Lățime disponibilă | Textul se măsoară cu fontul Android activ. Coloana mâinii ocupă cel mult 40% din grilă, iar numele și descrierea mâinii se pot împărți pe rânduri. |
| Mai mulți jucători decât încap | Antetul și scorurile derulează la aceeași poziție orizontală. Coloana mâinii rămâne vizibilă. |
| Istoric înalt | Un singur viewport vertical mută simultan descrierile mâinilor și scorurile. Antetul și legenda păstrează poziția. |
| Înălțime foarte mică | Când antetul și legenda nu lasă loc pentru primul rând complet, conținutul dialogului derulează vertical în întregime. |
| Istoric gol sau mână în curs | Se afișează numai rezultatele confirmate. Istoricul gol are un mesaj explicit. |

- Testele de componentă confruntă lățimile și înălțimile și reconstruiesc toate cele 48 de mâini ale unei mese de șase din jurnalul canonic. Punctele provin din motor, iar oracolul adună separat deltele pe fiecare loc. [Componentele istoricului](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/score/)
- Extracția comună pentru istoric rămâne limitată la geometrie și stil neutru. Categoriile Rentz, modelul punctajului, legenda și descrierile accesibile rămân în adaptorul jocului; orice cod comun ulterior trebuie să accepte proiecții de date fără reguli de scor.
- Sanity-ul `-HistoryOnly` citește `historyFixture` și `historyFixtureSha256` din receipt. Fixture-ul identifică jurnalul și valorile publice de la cele două capete ale istoricului. Traseul nativ deschide dialogul, derulează pe ambele axe și confruntă revizia și totalurile la întoarcerea în meniu. [Lansatorul Android](../../atf-tests/tools/run-shared-ui-sanity.ps1)
- Adaugă `-HistoryMatrix` pentru profilul compact de 720 × 1280 și fereastra scundă de 720 × 740, ambele la 360 dpi, font 1,5 și interfață EN. Proba API 28 măsoară ultimul grup de scor și rândul 48, verifică legenda la capăt și mărește din nou dialogul deschis.
- Invariantele grilei și capcanele la extinderea testelor sunt lângă cod. [README-ul grilei](../../rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/score/README.md)

### Detaliul unei mâini

| Responsabilitate | Contract |
|---|---|
| Motor | Închide rezultatul cu deltele, totalurile, contribuțiile pe categorii și locurile de sosire. La Totale, dama de caro este numărată în ambele categorii. |
| Jurnal | Salvează aceleași comenzi și același profil. Rezultatul public se reconstruiește la reluare, inclusiv pentru un jurnal vechi compatibil. |
| Interfață | Atingerea etichetei subliniate deschide detaliul. Locurile Rentz urmează ordinea motorului, iar ultima poziție explică atribuirea la închiderea mâinii. |
| Probe | Componentele confruntă categoriile cu mutările observate. Sanity-ul deschide o mână de levate dublată și o mână Rentz din jurnalul restaurat. |

Ajutorul afișează grila pentru 3–6 jucători din profilul efectiv, verificat și cu profil alternativ. Probele Android în RO/EN confruntă ajutorul și ordinea locurilor cu premiu zero după reluare.

[README-ul grilei și al detaliului](../../rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/score/README.md)

### Spațiul dintre chenare

| Parte | Responsabilitate |
|---|---|
| Masa comună | Levada folosește `TableGeometry.BoardSpec`, `PlayedCardsView` și ancorele `TableSeatLayout`; adaptorul măsoară casetele și rezervă separat secvența Rentz. |
| Secvențe | Cele patru șiruri folosesc banda dintre aceleași chenare din alegerea sub-jocului. La înălțime redusă se adaptează grila în banda disponibilă. |
| Cărțile din secvențe | Cele patru rânduri sunt centrate între chenare și folosesc lățimea mesei. La patru locuri, banda centrală folosește 23 px suplimentari fără mutarea ancorelor. Textul și spațiul dintre cărți depind de dimensiunea slotului. |
| Tura la secvențe | Cărțile legale din mâna activă au o nuanță discretă. Fiecare loc automat este afișat înainte de pauza de 550 ms; pasa forțată folosește aceeași pauză. Ordinea cadru → worker → checkpoint și anularea la lifecycle sunt definite în [contractul de lifecycle](ATF_Lifecycle.md). |

[Componentele geometriei](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/table/RentzCardSpaceTest.java) verifică ordinea comună a numelor și cărților, centrajul și absența intersecțiilor. [Testul de adopție](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/libraries/CardUiAdoptionTest.java) confruntă clasele încărcate cu AAR-ul comun. [Feedback-ul UI](../feedback/ATF_Rentz_UI_ToDo.md) păstrează verificările de făcut pe dispozitiv.

### Antetul mesei și AutoPlay

`RentzGameHeader` este adaptorul subțire al aplicației: proiectează progresul mâinii, numele sub-jocului și textele de accesibilitate în `GameTitleBar`. Biblioteca măsoară titlul și bifa după lățimea efectivă, mută bifa pe rândul următor dacă ar comprima titlul și păstrează ținta tactilă de 48 dp. Activitatea Rentz rămâne proprietarul stării, al verificării cadrului și al anulării deciziei automate; biblioteca primește doar valoarea curentă și callback-ul boolean.

- Componentele Rentz verifică textul și starea bifei în RO/EN, inclusiv ascunderea după încheierea mâinii. Un control inactiv nu pornește automatizarea în timpul unei ture blocate; un control activ păstrează acțiunea de oprire chiar dacă AI-ul calculează.
- Testul de adopție verifică octeții `GameTitleBar` din AAR-ul fixat. Contractul de măsurare și ținta tactilă sunt calificate în producătorul comun.
- Tema WEB furnizează explicit `--whist-menu-touch: 48px` și `--whist-title-gap: 8px`, corespunzătoare țintei și spațiului logic din antetul Android; componentele adaptorului și ale motorului compilat refuză lipsa acestor valori.
- `RentzCardSpaceTest` reutilizează cele patru profile native comune (320×599, 360×740, 393×800 cu font 2× și 600×1140), peste profilele Rentz compacte, scunde și late. La 3–6 locuri compară aceleași dreptunghiuri de ancorare și verifică banda centrală.

[Adaptorul antetului](../../rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/chrome/RentzGameHeader.java) · [Componentele antetului](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/chrome/RentzGameHeaderComponentTest.java) · [Calificarea nativă a antetului comun](https://github.com/crististefanita/whist/blob/main/whist-lab-app/app/src/test/java/ro/whistlab/app/screens/AutoGameHeaderComponentTest.java)

### De la ultima levată la următorul sub-joc

- Bugetul începe la primul desen al mesei: 1.000 ms pentru cărțile nemișcate și 500 ms pentru deplasare și estompare la câștigător. La o pornire rece, checkpoint-ul aflat exact la final de levată permite o singură reluare vizuală. O levată nouă începută păstrează cărțile ei; jurnalul nu primește comenzi pentru animație.
- Un checkpoint deja punctat se deschide direct, fără o sarcină AI care să redeseneze masa. Termenul absolut este verificat și înainte de desen. Cărțile expirate dispar din masa curentă înainte de construirea paginii de punctaj.
- Pe ferestre accelerate hardware cu scara implicită, Rentz programează mișcarea, micșorarea și estomparea pe rendererul Android, în aceeași fereastră de 1.500 ms. Cărțile ajung la câștigător înainte de estompare. Traseul software folosește animatorul comun.
- După colectarea ultimei levate, ecranul „Punctajul mâinii” preia contractul, multiplicatorul, **Δ:** și **Σ:** din același rezultat confirmat ca istoricul. Tabelul principal are un rând per jucător, trei coloane și text normal; contribuțiile și clasamentul final sunt disponibile în „Detalii de punctaj”. Jocul așteaptă acțiunea „Mâna următoare”; masa și istoricul rămân accesibile.
- În alegerea normală, după dezvăluirea cărților, jucătorul poate cere o sugestie AI calculată pe opțiunile legale, cu profilul și dificultatea partidei. Alegerea oarbă nu afișează sugestii. O recomandare nu selectează și nu confirmă automat sub-jocul.
- Separăm termenul exact verificat în componente de intervalul observabil în înregistrarea emulatorului. Extent păstrează și măsurătorile care ies din toleranță.

### Adaptarea secvenței și a scorului în browser

Biblioteca comună deține ancorele locurilor și măsurarea mesei; adaptorul Rentz specializează numai secvența și proiecția punctajului.

În browser, un container de comenzi gol nu intră în fluxul mesei, indiferent de spațierea CSS. Observațiile geometrice folosesc numai masa vizibilă, excluzând arborii `data-preparing`. La colectare, copia animată pornește pe dreptunghiul cărții și ajunge la chenarul câștigătorului; această mișcare intenționată se verifică separat de stabilitatea sloturilor.

| Zonă | Contract de adaptare | Verificare |
|---|---|---|
| Locuri | `RentzCardSpace.profile` furnizează aceleași sloturi `PlayedCardMetrics` și rezervarea mâinii; `BoardSpec` stabilește centrul. Numai secvența Rentz folosește `fullWidthCardBand`, la 3–6 locuri. | Confruntă adaptorul nativ și AAR-ul fixat cu răspunsul TeaVM pentru aceleași măsurători. |
| Fețe depuse | Cele șapte contracte cu levate folosesc aceleași dimensiuni și sloturi comune. Patru locuri formează crucea; celelalte mese folosesc rândurile exportate de bibliotecă. | Poziții de referință independente, una și toate cărțile vizibile, indici de loc în ordine diferită, fonturi și ferestre compacte. |
| Dimensiuni | Măsoară `clientWidth/clientHeight` înainte de scalarea scenei. | Scalare subunitară și supraunitară, fără micșorare dublă. |
| Hint legal | Marchează starea secvenței prin atribut DOM; nu depinde de selectorul CSS `:has`. | Conturul rămâne activ cu sau fără AutoPlay, inclusiv pe Chrome mobil mai vechi. |
| Browser mobil | Folosește înălțimea vizibilă pentru container, inclusiv cu bara de adresă deschisă. | Mock de viewport și verificare Chrome offline pe dispozitiv. |
| Actualizarea paginii | O singură revizie de conținut identifică toate importurile, workerul, stilurile și fontul. | Cache deja populat, schimbare numai în adaptorul de scor, dependență lipsă și recitirea octeților publicați. |
| Punctaj | `RentzScoreContent` și `RentzText` sunt compilate în ambele adaptoare. Δ = punctele mâinii; Σ = total după mână, cu legendă sub tabel. | Confruntă proiecția nativă cu rândurile WEB pentru fiecare sub-joc, limbă și alegere normală/oarbă. |
| Prezentarea rezultatului | Refolosește grila și `ScoreGridStyle`, inclusiv ponderile native Jucător/Δ:/Σ:, fără bold. Contribuțiile apar la cerere. Istoricul complet își păstrează grila pe mâini. | Mese de 3–6, RO/EN, valori semnate și celule fără îngroșare în browser. |
| Levate | Fiecare carte, inclusiv cea proprie, are cadru în playback. `TrickCollectionMotion` fixează afișarea și colectarea la câștigător. | Toate fețele pe masă înainte de colectare; nicio trecere la scor înainte de finalizarea ei, inclusiv cu mișcare redusă. |
| Reluare | Păstrează toate rândurile confirmate și identitatea sub-jocului. | Rentz plus alt sub-joc, istoric înainte și după reîncărcare. |
| Meniu | `RentzGameMenu` furnizează disponibilitatea rutelor peste `TableMenuContent`; adaptorul DOM păstrează callbacks și rutele WEB de profil și clasamente. | Ruta de scor cere o mână confirmată; lipsa proiecției este refuzată înainte de afișarea meniului. |
| Automat | Cadența vine direct din `TurnPacing`. Programarea DOM și lifecycle-ul Android își păstrează proprietarii. | Oprire, pauză, revizie și livrare întârziată, folosind componentele comune și ceas controlat. |

[Mecanismul și pașii MCP](../../atf-ui-browser/README.md#acceptanța-web-prin-browser-mcp)

Contractele livrate în JAR-ul de teste califică viewportul, feedback-ul mâinii, meniul și proporțiile feței. `verify-shared-ui.mjs` confruntă și funcția efectivă de poziționare cu referința citită din AAR; `verify-shared-automation.mjs` controlează așteptarea modulului comun, fără să copieze programatorul. Rasterul, gesturile native și bugetul ferestrelor extreme rămân verificări pe platformă.

Pentru istoric, fixture-urile de măsurare acoperă lățimi de la 280 la 600, înălțimi scunde și înalte și scări de font de la 1× la 2×. Verifică păstrarea numerelor semnate, ultimul jucător și ultima mână în zona derulabilă. Aceste fixture-uri validează contractul de măsurare; nu simulează rasterizarea fontului Android.

## Antetele istoricului

Istoricul folosește antetele compacte `Δ` și `Σ`, fără două puncte. Rezultatul mâinii și informațiile de la masă păstrează `Δ:` și `Σ:`. Valorile, ordinea locurilor și legenda sunt comune proiecției native compilate pentru WEB; widgetul comun primește aceste etichete de la adaptorul Rentz.

## Spațiul deasupra jucătorului

| Strat | Responsabilitate |
|---|---|
| Android | `PlayerSeatView` include eticheta în înălțimea măsurată a locului. |
| Shell WEB comun | Măsoară separat eticheta exterioară și furnizează `actorClearance`. |
| Adaptor Rentz | `RentzPageLayout` exclude rezerva din banda celor patru șiruri; ancorele locurilor rămân cele comune. |
| Componente | Confruntă profilele cu și fără rezervă și refuză suprapunerea benzii peste etichetă. |
