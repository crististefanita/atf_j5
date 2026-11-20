# Feedback activ pentru interfața Rentz

## Pe scurt

Probele focalizate pentru 3, 5 și 6 locuri urmăresc selectorul, prima atingere și finalul unei mâini Rentz. Pentru fiecare gol rămas, precizați cadrul sau interacțiunea care trebuie observată pe dispozitiv. [Probe Android](../rentz/ATF_Android.md#livrabile-și-limite)

## P1 · Geometrie, secvențe și scor

| ID | Criteriu rămas |
|---|---|
| U01, U04, U05, R02 | Extindeți matricea de ferestre scunde la mâna completă, o carte, mâna goală și pasul forțat. Rangul 10 și indicii cărților de margine rămân integral lizibili în evantai; eticheta actorului păstrează cuvintele întregi la text mărit. Urmăriți starea mâinii proprii până la continuarea vizibilă. |
| R03 | Marcați ultima mutare și explicați extensiile legale sau pasul fără a divulga cărți adverse. Testați explicația pentru ambele capete ale secvenței. |
| U07, S01, S04 | Jocul offline verifică premiile, ordinea locurilor și egalitățile. Pe APK rămân de calificat egalitățile și valorile extreme pe mesele compacte de 3, 5 și 6 locuri. |

Folosiți comparații pe aceeași configurație efectivă și captați doar tranziția relevantă. [Matricea de afișaj](../rentz/ATF_UiIntegration.md#matricea-de-afișaj) · [Istoricul adaptiv](../rentz/ATF_UiIntegration.md#istoricul-adaptiv-rentz)

## P1 · Bifa Automat și ritmul turei

Antetul folosește `GameTitleBar` din AAR-ul fixat. Contractul stabil este în [ghidul de integrare](../rentz/ATF_UiIntegration.md#antetul-mesei-și-autoplay); aici rămân limitele de acceptanță.

| Strat | Contract și verificare |
|---|---|
| Adaptor | [RentzGameHeader](../../rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/chrome/RentzGameHeader.java) păstrează oprirea disponibilă cu AI în așteptare. [Componentele](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/chrome/RentzGameHeaderComponentTest.java) verifică RO/EN, control activ și pornire blocată. |
| Geometrie offline | Profilele Whist 320×599, 360×740, 393×800 (font 2×) și 600×1140 completează profilele Rentz scund și lat. [Matricea de afișaj](../rentz/ATF_UiIntegration.md#antetul-mesei-și-autoplay) |
| Lifecycle offline | [Componentele AutoPlay](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/lifecycle/AutoPlayComponentTest.java) refuză răspunsul livrat după oprire și păstrează tura adversarului. |
| Acceptanță Android rămasă | Confirmați la font mărit o singură intenție per atingere, resetarea vizuală la mâna nouă, pauză/navigare și reluarea între două mutări. [Contractul lifecycle](../rentz/ATF_Lifecycle.md) |

## P1 · Admiterea bibliotecilor și paritatea Whist

Păstrați AAR-ul Android `0.2` și pachetul Browser `0.4` cu JAR-ul de teste asociat. O modificare a adaptorului Rentz nu justifică înlocuirea octeților bibliotecii. La admitere, confruntați intrările producătorului, commitul, SHA-256 și clasele încărcate. [Contractul de livrare](../../rentz-lab-app/ui-library/README.md#regresie-și-livrare-către-rentz)

| Prioritate | Cerință rămasă | Criteriu de închidere |
|---|---|---|
| P1 | Paritatea pe profile dinamice | Componentele confruntă sloturile și dreptunghiurile AAR–TeaVM și păstrează identitatea elementelor WEB între AI și jucător; pe platformă confirmați după redimensionare și font mărit stabilitatea vizuală a chenarelor între alegere, joc și colectare. O egalitate de coordonate nu închide singură observația de mișcare percepută. Proba nativă identifică profilul, APK-ul și regiunea observată. |
| P1 | Rasterul APK al secvenței la 3, 5 și 6 jucători | Pe profil compact și font mărit, confirmați indicii suprapuși, ultima carte și pasul forțat. Calificarea Chrome nu închide rasterul APK-ului. Păstrați geometria comună a locurilor. |
| P1 | Oprirea AutoPlay în ambele adaptoare | Oprirea rămâne accesibilă când AI-ul calculează; livrarea întârziată nu aplică o comandă. În Web, includeți și oprirea între două cadre de playback. |
| P2 | Fereastra Chrome și barele browserului | La text mărit, antetul, oprirea Automat și mâna proprie rămân integral accesibile când bara de adresă își schimbă înălțimea. Comparați viewport-ul vizual și captura nativă; dreptunghiurile DOM valide nu certifică singure lipsa decupării. |
| P2 | Meniuri și fonturi comune | Contractele livrate de meniu și proporțiile tipografice sunt verificate fără emulator. Calificați rasterul la font mărit, focusul tastaturii și clickul în exterior pe platformă; nu repetați calculele pure prin E2E. |

Pentru contractele cu levate, păstrați matricea completă în [componente](../rentz/ATF_Components.md): motor compilat, renderer comun, ceas controlat și oracol separat de punctare. Contractul DOM și criteriul copiilor animate sunt în [integrare](../rentz/ATF_UiIntegration.md#adaptarea-secvenței-și-a-scorului-în-browser). Acceptanța Chrome Android nu închide rasterul APK-ului; păstrați separat profilul și rendererul observat, fără câte un E2E pentru fiecare combinație deja calificată prin componente.

## P2 · Meniu și istoric

### U10–U11 · Acceptanța nativă a meniului și istoricului

Contractul implementat aparține [integrării WEB–APK](../rentz/ATF_UiIntegration.md#meniul-și-aspectul-istoricului). Componentele verifică planul comun, disponibilitatea, comenzile și tokenurile din AAR/JAR; probele Chromium măsoară stilul și derularea rendererului real.

- Pe un APK identificat, confirmați titlul și tabelul fără bold, separatoare continue între grupurile de jucători, 3–6 locuri și font mărit.
- Verificați gestul din coloana fixă, revenirea din detaliile unei mâini și păstrarea poziției; comparațiile JVM nu certifică rasterul Android.
- Confirmați focusul și tastatura pentru Nume deschis în timpul jocului; dialogul suspendă livrarea AI și reia același checkpoint la închidere. Politica Unicode rămâne în U02/U03.

| ID | Criteriu rămas |
|---|---|
| U02, U03 | Calificați rutele negative la font mărit: anularea, resetarea numelui, refuzul de salvare și confirmarea înlocuirii sesiunii. Aliniați filtrul UTF-16 al dialogului comun cu politica Rentz în puncte de cod. |
| Istoric | Porniți tragerea de pe coloana fixă, inclusiv după fling. Confirmați deplasarea rândurilor la tragere și deschiderea exact a unui detaliu la atingere. |
| U09 | Widgeturile `ScoreTableView` și `ScoreHistoryView` sunt comune. Rentz furnizează `Δ`/`Σ` în istoric și `Δ:`/`Σ:` la rezultat, identitățile mâinilor și callback-ul detaliilor. Pe Android rămân drag/fling pornit din coloana fixă, sincronizarea antetului și restaurarea poziției. Testele JVM cu porturi de scroll nu închid aceste criterii native. |

[Strategia de testare](../rentz/ATF_TestingStrategy.md) · [Admiterea bibliotecilor](../rentz/ATF_UiIntegration.md#admiterea-bibliotecilor-de-produs)

## P2 · Acceptanța nativă a rezultatului compact

Pagina de punctaj folosește tabelul neutru Jucător/Δ:/Σ:, cu contribuțiile la cerere. Pe mesele APK de 3, 5 și 6 locuri rămân rasterul la font mărit, derularea pe ferestre scunde și detaliile fără pierderea accesului la „Mâna următoare”. [Contractul și testele rapide](../rentz/ATF_UiIntegration.md#adaptarea-secvenței-și-a-scorului-în-browser) păstrează separat proiecția comună și observația de platformă.

## Acceptanța acțiunilor mărite

Păstrați separat acceptanța raster a APK-ului: etichete complete și derulare până la ultima acțiune, în RO/EN cu text mărit. Componentele califică rolurile și callback-urile comune, iar pixelii Android cer observația unui APK identificat.
