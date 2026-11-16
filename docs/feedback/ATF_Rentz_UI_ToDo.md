# Feedback activ pentru interfața Rentz

## Pe scurt

Probele focalizate pentru 3, 5 și 6 locuri urmăresc selectorul, prima atingere și finalul unei mâini Rentz. Pentru fiecare gol rămas, precizați cadrul sau interacțiunea care trebuie observată pe dispozitiv. [Probe Android](../rentz/ATF_Android.md#livrabile-și-limite)

## P1 · Geometrie, secvențe și scor

| ID | Criteriu rămas |
|---|---|
| U01, U04, U05, R02 | Extindeți proba peste profilul compact curent la ferestre mai scunde. Verificați o carte rămasă, mâna goală, pasul și accesul la continuare în timpul mâinii. |
| R03 | Marcați ultima mutare și explicați extensiile legale sau pasul fără a divulga cărți adverse. Testați explicația pentru ambele capete ale secvenței. |
| R04, R05 | Proba surprinde scorul confirmat și tabla terminală redeschisă. Pentru sosirea anterioară scorului, cereți un cadru distinct în fluxul automat și verificați contorul accesibil al cărților neplasate. |
| U07, S01, S04 | Jocul offline verifică premiile, ordinea locurilor și egalitățile. Pe dispozitiv rămân de calificat randarea egalităților și a valorilor semnate la decontare. |

Folosiți comparații pe aceeași configurație efectivă și captați doar tranziția relevantă. [Matricea de afișaj](../rentz/ATF_UiIntegration.md#matricea-de-afișaj) · [Istoricul adaptiv](../rentz/ATF_UiIntegration.md#istoricul-adaptiv-rentz)

## P1 · Bifa Automat și ritmul turei

**Responsabil:** adaptorul Rentz. Antetul construiește bifa în activitate. Adoptați controlul comun după admiterea AAR-ului compatibil și verificați dispunerea la font mărit, o singură intenție per atingere și revenirea la debifat la mâna nouă. [Antetul curent](../../rentz-lab-app/app/src/main/java/ro/rentzlab/app/MainActivity.java) · [Biblioteca UI](../../rentz-lab-app/ui-library/README.md)

Proba Rentz corelează cadrul actorului, pauza și o comandă pentru fiecare tură adversă. Rămân de verificat tranziția prin colectarea levatei, anularea la pauză sau navigare și reluarea între două mutări. [Contractul lifecycle](../rentz/ATF_Lifecycle.md) · [Componente](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/lifecycle/AutoPlayComponentTest.java)

## P2 · Meniu și istoric

| ID | Criteriu rămas |
|---|---|
| U02, U03 | Calificați rutele negative la font mărit: anularea, resetarea numelui, refuzul de salvare și confirmarea înlocuirii sesiunii. Aliniați filtrul UTF-16 al dialogului comun cu politica Rentz în puncte de cod. |
| Istoric | Porniți tragerea de pe coloana fixă, inclusiv după fling. Confirmați deplasarea rândurilor la tragere și deschiderea exact a unui detaliu la atingere. |

[Strategia de testare](../rentz/ATF_TestingStrategy.md) · [Admiterea bibliotecilor](../rentz/ATF_UiIntegration.md#admiterea-bibliotecilor-de-produs)
