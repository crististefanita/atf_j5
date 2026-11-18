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

**Adoptarea controlului comun este implementată:** `RentzGameHeader` proiectează textul RO/EN și starea curentă în `GameTitleBar` din AAR-ul fixat; logica de pornire, oprire și anulare rămâne în Rentz. Componentele verifică starea vizibilă, bifată și dezactivată, iar testul de adopție confirmă clasa încărcată din AAR. [Adaptorul](../../rentz-lab-app/app/src/main/java/ro/rentzlab/app/ui/screens/RentzGameHeader.java) · [Componentele adaptorului](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/ui/screens/RentzGameHeaderComponentTest.java) · [Biblioteca UI](../../rentz-lab-app/ui-library/README.md)

**Rămâne acceptanța pe Android:** la font mărit, confirmarea unei singure intenții per atingere și resetarea vizuală la începutul mâinii noi. Geometria comună se verifică offline în Rentz pe profilele Whist de 320×599, 360×740, 393×800 (font 2×) și 600×1140, plus profilurile Rentz scund și lat. [Matricea de afișaj și antet](../rentz/ATF_UiIntegration.md#antetul-mesei-și-autoplay) · [Componente AutoPlay](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/lifecycle/AutoPlayComponentTest.java)

Proba Rentz corelează cadrul actorului, pauza și o comandă pentru fiecare tură adversă. Rămân de verificat anularea la pauză sau navigare și reluarea între două mutări. [Contractul lifecycle](../rentz/ATF_Lifecycle.md) · [Componente](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/lifecycle/AutoPlayComponentTest.java)

## P2 · Meniu și istoric

| ID | Criteriu rămas |
|---|---|
| U02, U03 | Calificați rutele negative la font mărit: anularea, resetarea numelui, refuzul de salvare și confirmarea înlocuirii sesiunii. Aliniați filtrul UTF-16 al dialogului comun cu politica Rentz în puncte de cod. |
| Istoric | Porniți tragerea de pe coloana fixă, inclusiv după fling. Confirmați deplasarea rândurilor la tragere și deschiderea exact a unui detaliu la atingere. |
| U09 | Comparați grila și legenda scorului între jocuri înainte de extracție: mutați numai geometria și stilul neutru în componenta comună; păstrați categoriile, punctajul și modelul de accesibilitate în adaptoarele jocurilor. Calificați lățimi compacte și late în RO/EN. |

[Strategia de testare](../rentz/ATF_TestingStrategy.md) · [Admiterea bibliotecilor](../rentz/ATF_UiIntegration.md#admiterea-bibliotecilor-de-produs)
