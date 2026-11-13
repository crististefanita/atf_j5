# Integrarea nucleului Android comun

## Pe scurt

Nucleul vizual produs de Whist oferă componente neutre pentru meniul de pornire, profil, widgeturi, cărți, temă și geometria mâinii. Configurația live consumă AAR-ul fixat în repository-ul local, iar adaptoarele Rentz furnizează datele și comenzile proprii. Calificarea componentelor, controlul Android și release-ul publicat păstrează dovezi distincte.

## Frontiera consumatorului

| Intrare comună | Responsabilitatea Rentz |
|---|---|
| `HomeMenuContent` și `HomeMenu` | Texte RO/EN, starea reluării și callback-uri către rutele reale; primul profil și preferințele sunt ale aplicației. |
| `PlayerNameDialog` și `NameEdit` | Politica numelui, identitatea locului și salvarea; erorile și resetarea păstrează semantica declarată. |
| `ScreenViews` | Etichetele selectorilor și protecția ferestrei, furnizate explicit; aceeași geometrie și aceleași stiluri ale widgeturilor. |
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
implementation 'ro.cardtable:card-table-ui:0.1.11'
implementation 'ro.cardtable:card-table-names:0.1.0'
```

Aceste intrări sunt active în configurația live. [Repository-ul admis](../../rentz-lab-app/ui-library/README.md) păstrează artefactele și hash-urile; testele refuză dependența de motorul Whist.

## Componente și acceptanță

- Verificați proiecțiile pe date Rentz reale, callback-uri unice, limbă și nume, legalitatea comenzii și tratamentul refuzului.
- Păstrați componentele lifecycle-ului, codec-ului și sesiunii; adaptarea UI nu le înlocuiește și nu schimbă salvarea privatului în date de prezentare.
- Comparați geometria și textele cu reperul comun, cu fixture-uri și oracole independente; declanșarea unui callback în JVM nu este tap Android.
- Adăugați numai sanity-ul nativ necesar pentru meniu/profil, mâna și scorul efectiv, după trecerea componentelor. Rezultatele, APK-urile tehnice și logurile rămân externe.

[Backlog-ul UI](../feedback/ATF_Rentz_UI_ToDo.md) deține criteriile rămase. [Calificarea offline](ATF_Qualification.md) și [acceptanța Android](ATF_Android.md) păstrează identitățile și limitele execuției.

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

### Schimbarea contractului la masa de patru jucători

- Alegerea sub-jocului și jocul păstrează aceleași dreptunghiuri ale locurilor pentru același ecran. Masa măsoară locurile o singură dată pe cadru, apoi adaptează comenzile de alegere, secvența sau levata la banda rămasă. Indicatorul Alege, Joacă sau Pasează arată actorul curent deasupra chenarului.
- Pe un ecran scund, levata reduce mai întâi distanța dintre rânduri și păstrează ordinea cărților. Bugetul se verifică în pixeli, cu aceeași rotunjire separată a poziției și a înălțimii ultimului rând ca la randarea Android.
- Masa și ecranul de punctaj afișează progresul `Mâna x/y` pe primul rând și contractul pe rândul următor. Marcajul `×2` rămâne lângă contract.

[Componentele de geometrie](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/table/RentzCardSpaceTest.java) verifică încadrarea pe dimensiuni controlate. [Acceptanța de release](ATF_Release.md#ordinea-calificării-android-curente) compară dreptunghiurile native înainte și după schimbarea contractului, după mutări și după reluare.

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

Varianta izolată are receipt propriu. Runnerul curent folosește replay-ul independent al sesiunii, iar integrarea deciziei externe din lansatorul prototipului rămâne U09.

## Auditul cititorului Extent

- [Driverul focalizat](../../atf-tests/tools/run-trick-collection-sanity.ps1) execută ultima mutare legală și reia mâna după oprirea procesului. Înregistrați fixture-ul de dezvoltare înainte de a-l expune emulatorului.
- [Analiza cadrelor](../../atf-tests/tools/analyze-trick-collection.mjs) măsoară timestamp-urile, centrele cărților și apropierea de chenarul câștigător. Extent include cadrele și hash-urile înregistrărilor.

- `-CompactScores` verifică în română chenarul la font 1,5 și confruntă ordinea cărților vizibile cu observația motorului.
- `-HelpAwardsOnly` verifică în ambele limbi premiile distincte pentru mesele de 3–6 jucători.
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

[Componentele clasamentului](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/score/RentzScoreContentTest.java) acoperă egalitățile, scorurile negative și cele patru mărimi de masă. [Acceptanța terminală](../feedback/ATF_Rentz_UI_ToDo.md#p1-tabelă-de-scor-elegantă-și-utilă) completează traseul de release cu trei mâini.

## Istoricul adaptiv Rentz

Grila folosește lățimi măsurate, subcoloane aliniate și antet fix. Coloana fixă arată numărul mâinii, sub-jocul, alegătorul și marcajul ×2. Grupurile de scor arată **Δ** pentru mână și **Σ** pentru totalul confirmat.

| Situație | Comportament |
|---|---|
| Lățime disponibilă | Textul se măsoară cu fontul Android activ. Coloana mâinii ocupă cel mult 40% din grilă, iar numele și descrierea mâinii se pot împărți pe rânduri. |
| Mai mulți jucători decât încap | Antetul și scorurile derulează la aceeași poziție orizontală. Coloana mâinii rămâne vizibilă. |
| Istoric înalt | Un singur viewport vertical mută simultan descrierile mâinilor și scorurile. Antetul și legenda păstrează poziția. |
| Înălțime foarte mică | Când antetul și legenda nu lasă loc pentru primul rând complet, conținutul dialogului derulează vertical în întregime. |
| Istoric gol sau mână în curs | Se afișează numai rezultatele confirmate. Istoricul gol are un mesaj explicit. |

- Testele de componentă confruntă lățimile și înălțimile și reconstruiesc toate cele 48 de mâini ale unei mese de șase din jurnalul canonic. Punctele provin din motor, iar oracolul adună separat deltele pe fiecare loc. [Componentele istoricului](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/score/)
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
| Masa comună | Levada păstrează `BoardLayout`, `PlayedCardsView` și ancorele `TableSeatLayout` folosite în Whist. |
| Secvențe | Cele patru șiruri folosesc banda dintre aceleași chenare din alegerea sub-jocului. La înălțime redusă se adaptează grila în banda disponibilă. |
| Cărțile din secvențe | Cele patru rânduri sunt centrate între chenare și folosesc lățimea mesei. Textul și spațiul dintre cărți depind de dimensiunea slotului. |
| Tura la secvențe | Cărțile legale din mâna activă au o nuanță discretă. Fiecare loc automat este afișat înainte de pauza de 550 ms; pasa forțată folosește aceeași pauză. Ordinea cadru → worker → checkpoint și anularea la lifecycle sunt definite în [contractul de lifecycle](ATF_Lifecycle.md). |

[Componentele geometriei](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/table/RentzCardSpaceTest.java) verifică ordinea comună a numelor și cărților, centrajul și absența intersecțiilor. [Testul de adopție](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/libraries/CardUiAdoptionTest.java) confruntă clasele încărcate cu AAR-ul comun. [Feedback-ul UI](../feedback/ATF_Rentz_UI_ToDo.md) păstrează verificările de făcut pe dispozitiv.

### De la ultima levată la următorul sub-joc

- Bugetul începe la primul desen al mesei: 1.000 ms pentru cărțile nemișcate și 500 ms pentru deplasare și estompare la câștigător. La o pornire rece, checkpoint-ul aflat exact la final de levată permite o singură reluare vizuală. O levată nouă începută păstrează cărțile ei; jurnalul nu primește comenzi pentru animație.
- Un checkpoint deja punctat se deschide direct, fără o sarcină AI care să redeseneze masa. Termenul absolut este verificat și înainte de desen. Cărțile expirate dispar din masa curentă înainte de construirea paginii de punctaj.
- Pe ferestre accelerate hardware cu scara implicită, Rentz programează mișcarea, micșorarea și estomparea pe rendererul Android, în aceeași fereastră de 1.500 ms. Cărțile ajung la câștigător înainte de estompare. Traseul software folosește animatorul comun.
- După colectarea ultimei levate, ecranul „Punctajul mâinii” preia contractul, multiplicatorul, contribuțiile, **Δ** și **Σ** din același rezultat confirmat ca istoricul. Jocul așteaptă acțiunea „Mâna următoare”; masa și istoricul rămân accesibile.
- În alegerea normală, după dezvăluirea cărților, jucătorul poate cere o sugestie AI calculată pe opțiunile legale, cu profilul și dificultatea partidei. Alegerea oarbă nu afișează sugestii. O recomandare nu selectează și nu confirmă automat sub-jocul.
- Separăm termenul exact verificat în componente de intervalul observabil în înregistrarea emulatorului. Extent păstrează și măsurătorile care ies din toleranță.
