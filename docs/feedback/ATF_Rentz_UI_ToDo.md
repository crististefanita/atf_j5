# Feedback activ pentru interfața Rentz

## Pe scurt

Probele focalizate pentru 3, 5 și 6 locuri urmăresc selectorul, prima atingere și finalul unei mâini Rentz. Pentru fiecare gol rămas, precizați cadrul sau interacțiunea care trebuie observată pe dispozitiv. [Probe Android](../rentz/ATF_Android.md#livrabile-și-limite)

## P1 · Geometrie, secvențe și scor

| ID | Criteriu rămas |
|---|---|
| U01, U04, U05, R02 | Extindeți matricea de ferestre scunde la cazurile cu o carte, mâna goală și pasul forțat. Urmăriți starea mâinii proprii până la continuarea vizibilă. |
| R03 | Marcați ultima mutare și explicați extensiile legale sau pasul fără a divulga cărți adverse. Testați explicația pentru ambele capete ale secvenței. |
| U07, S01, S04 | Jocul offline verifică premiile, ordinea locurilor și egalitățile. Pe dispozitiv rămân de calificat randarea egalităților și a valorilor semnate la decontare. |

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

Păstrați AAR-ul Android `0.1.19` și pachetul Browser `0.2.13` cu JAR-ul de teste asociat. O modificare a adaptorului Rentz nu justifică înlocuirea octeților bibliotecii. La admitere, confruntați intrările producătorului, commitul, SHA-256 și clasele încărcate. [Contractul de livrare](../../rentz-lab-app/ui-library/README.md#regresie-și-livrare-către-rentz)

| Prioritate | Cerință rămasă | Criteriu de închidere |
|---|---|---|
| P1 | Paritatea pe profile dinamice | Componentele confruntă sloturile și dreptunghiurile AAR–TeaVM; pe platformă confirmați după redimensionare și font mărit că toate chenarele rămân fixe între alegere, joc și colectare. Proba nativă identifică profilul, APK-ul și regiunea observată. |
| P1 | Secvența Rentz la 3–6 jucători | Măsurați banda centrală și toate cele patru șiruri, inclusiv ultima carte și pasul forțat. Nu schimbați geometria comună a locurilor pentru a compensa conținutul specific. |
| P1 | Oprirea AutoPlay în ambele adaptoare | Oprirea rămâne accesibilă când AI-ul calculează; livrarea întârziată nu aplică o comandă. În Web, includeți și oprirea între două cadre de playback. |
| P2 | Meniuri și fonturi comune | Contractele livrate de meniu și proporțiile tipografice sunt verificate fără emulator. Calificați rasterul la font mărit, focusul tastaturii și clickul în exterior pe platformă; nu repetați calculele pure prin E2E. |

## P2 · Meniu și istoric

| ID | Criteriu rămas |
|---|---|
| U02, U03 | Calificați rutele negative la font mărit: anularea, resetarea numelui, refuzul de salvare și confirmarea înlocuirii sesiunii. Aliniați filtrul UTF-16 al dialogului comun cu politica Rentz în puncte de cod. |
| Istoric | Porniți tragerea de pe coloana fixă, inclusiv după fling. Confirmați deplasarea rândurilor la tragere și deschiderea exact a unui detaliu la atingere. |
| U09 | Comparați grila și legenda scorului între jocuri înainte de extracție: mutați numai geometria și stilul neutru în componenta comună; păstrați categoriile, punctajul și modelul de accesibilitate în adaptoarele jocurilor. Calificați lățimi compacte și late în RO/EN. |

[Strategia de testare](../rentz/ATF_TestingStrategy.md) · [Admiterea bibliotecilor](../rentz/ATF_UiIntegration.md#admiterea-bibliotecilor-de-produs)

## Paritatea WEB: spațiu, scor și istoric

Contractul stabil și testele rapide sunt în [ghidul integrării](../rentz/ATF_UiIntegration.md#adaptarea-secvenței-și-a-scorului-în-browser). Acceptanța prin browser cere o secvență Rentz completă în patru locuri și încă un sub-joc complet, cu P/T, un singur premiu zero la Rentz, cumul corect și istoric păstrat după reîncărcare. O pagină încărcată fără aceste verificări nu califică randarea.

Pentru Android rămân exclusiv verificările de raster și de gesturi deja enumerate mai sus. Nu închideți aceste observații pe baza simulării DOM sau a probei WEB.

### P2 · Acceptanța nativă a rezultatului compact

Pagina de punctaj folosește acum tabelul neutru Jucător/P/T, cu contribuțiile la cerere. Componentele validează proiecția pentru 3–6 locuri și ambele limbi; înaintea calificării unui APK, confirmați rasterul la font mărit, derularea pe ferestre scunde și deschiderea/închiderea detaliilor fără pierderea accesului la „Mâna următoare”. Nu echivalați compilarea sau verificarea WEB cu această acceptanță Android.
