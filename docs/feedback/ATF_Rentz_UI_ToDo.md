# Feedback activ pentru interfața Rentz

## Pe scurt

Probele focalizate pentru 3, 5 și 6 locuri urmăresc selectorul, prima atingere și poziția actorului. Agenda nativă rămasă urmărește cazurile din continuarea mâinii, decontarea și lizibilitatea la suprapunere. [Contractul de integrare](../rentz/ATF_UiIntegration.md) · [Poarta Android](../rentz/ATF_Release.md#ordinea-calificării-android-curente)

## P1 · Geometrie, secvențe și scor

| ID | Criteriu rămas |
|---|---|
| U01, U04, U05, R02 | În continuarea probei focalizate, verificați nativ pe profil scund și font mărit rangul și simbolul culorii la suprapunere, o carte rămasă, mâna goală, pasul și accesul la continuare. Componentele verifică geometria și legalitatea pentru aceste stări. |
| R03 | Marcați ultima mutare și explicați extensiile legale sau pasul fără a divulga cărți adverse. Testați explicația pentru ambele capete ale secvenței. |
| R04, R05 | Verificați nativ sosirea înainte de decontare și contorul accesibil al cărților neplasate. Comparați tabla terminală și spațiile rămase înainte și după deschiderea scorului. |
| U07, S01, S04 | Jocul offline verifică premiile, ordinea locurilor și egalitățile. Pe dispozitiv verificați cum se afișează egalitățile și valorile semnate, plus sosirea Rentz înainte de primul cadru cu punctajul confirmat. |

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
