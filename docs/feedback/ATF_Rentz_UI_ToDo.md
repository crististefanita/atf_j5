# Feedback activ pentru interfața Rentz

## Pe scurt

Prioritatea este verificarea pe dispozitiv a turei, a geometriei la 3, 5 și 6 locuri și a decontării Rentz. Proba compactă de release acoperă patru locuri, Rentz urmat de două contracte distincte, scorul și reluarea. [Contractul de integrare](../rentz/ATF_UiIntegration.md) · [Poarta Android](../rentz/ATF_Release.md#ordinea-calificării-android-curente)

## P1 · Geometrie, secvențe și scor

| ID | Criteriu rămas |
|---|---|
| U01, U04, U05, R02 | La 3, 5 și 6 locuri, comparați ancorele în selector, levată și Rentz. Pe profil scund și font mărit, verificați rangul și simbolul culorii la suprapunere, opt/una/zero cărți, pasul și accesul la continuare. |
| R03 | Marcați ultima mutare și explicați extensiile legale sau pasul fără a divulga cărți adverse. Testați explicația pentru ambele capete ale secvenței. |
| R04, R05 | Verificați nativ sosirea înainte de decontare și contorul accesibil al cărților neplasate. Comparați tabla terminală și spațiile rămase înainte și după deschiderea scorului. |
| U07, S01, S04 | Verificați nativ egalitățile, atașarea valorilor la loc și afișarea sosirii Rentz înainte de punctajul confirmat. Acoperiți mărimile de masă din afara traseului de release prin probe focalizate. |

Folosiți comparații pe aceeași configurație efectivă și captați doar tranziția relevantă. [Matricea de afișaj](../rentz/ATF_UiIntegration.md#matricea-de-afișaj) · [Istoricul adaptiv](../rentz/ATF_UiIntegration.md#istoricul-adaptiv-rentz)

## P1 · Bifa Automat și ritmul turei

**Responsabil:** adaptorul Rentz. Antetul construiește bifa în activitate. Adoptați controlul comun după admiterea AAR-ului compatibil și verificați dispunerea la font mărit, o singură intenție per atingere și revenirea la debifat la mâna nouă. [Antetul curent](../../rentz-lab-app/app/src/main/java/ro/rentzlab/app/MainActivity.java) · [Biblioteca UI](../../rentz-lab-app/ui-library/README.md)

Măsurați pe dispozitiv marcajul actorului, pauza și câte o depunere în trei mutări adverse urmate de o levată nouă. Verificați anularea la pauză sau navigare și reluarea între două mutări. Testele lifecycle fixează ordinea și invalidarea. [Contractul lifecycle](../rentz/ATF_Lifecycle.md) · [Componente](../../rentz-lab-app/app/src/test/java/ro/rentzlab/app/lifecycle/AutoPlayComponentTest.java)

## P2 · Meniu și istoric

| ID | Criteriu rămas |
|---|---|
| U02, U03 | Calificați rutele negative la font mărit: anularea, resetarea numelui, refuzul de salvare și confirmarea înlocuirii sesiunii. Aliniați filtrul UTF-16 al dialogului comun cu politica Rentz în puncte de cod. |
| Istoric | Porniți tragerea de pe coloana fixă, inclusiv după fling. Confirmați deplasarea rândurilor la tragere și deschiderea exact a unui detaliu la atingere. |

[Strategia de testare](../rentz/ATF_TestingStrategy.md) · [Admiterea bibliotecilor](../rentz/ATF_UiIntegration.md#admiterea-bibliotecilor-de-produs)
