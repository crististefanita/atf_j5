# Calitatea Rentz: integritate, adaptoare și guvernanță

## Pe scurt

Registrul separă mecanismele implementate de promovarea strategică și de integrarea nativă încă necalificate. [Regulile și fidelitatea](ATF_Rentz.md) și [prezentarea tablei și scorului](ATF_Rentz_UI_ToDo.md) au criterii proprii.

| Contract stabil | Document responsabil |
|---|---|
| Validare și date | [Învățare](../rentz/ATF_Learning.md), [sesiune](../../rentz-lab-app/app/README.md) și [infrastructură](../rentz/ATF_Infrastructure.md). |
| Proprietate și sursă | [Lifecycle](../rentz/ATF_Lifecycle.md) și [guvernanță Java](../rentz/ATF_Components.md); execuția nativă se califică separat. |
| Adopție, colecții, proces și publicare | [Calificarea consumatorului](../rentz/ATF_Qualification.md). |
| Proiecții și geometrie | [Backlog UI](ATF_Rentz_UI_ToDo.md); probele offline nu închid calibrarea vizuală. |

| ID | Prioritate | Statut și impact | Responsabil |
|---|---|---|---|
| Q08 | P2 | Obiective și continuare completă implementate offline; promovarea produsului cere progres independent și integrare proprie. | Politicile și protocolul AI. |


## Q08: forța AI se măsoară la obiectivul declarat

[Mecanismul](../rentz/ATF_Learning.md) păstrează reperul pe marja mâinii și introduce separat punctele finale și creditul de victorie la finalul partidei. Registrul public imuabil reface totalurile, rotația și portofoliul rămas; seed-ul real și cărțile adverse reale rămân excluse.

| Contract implementat | Verificare și limită |
|---|---|
| Obiectiv și orizont | [Regresii situaționale](../../rentz-lab-app/game-core/src/test/java/ro/rentzlab/core/RentzMatchStrategyTest.java): egalități, puncte versus victorie, progres public și secrete reale distincte. |
| Alegeri rămase | Reconstrucție și continuare completă fără căutare recursivă; consistența registrului nu înlocuiește auditul tuturor cărților trecute. |
| Comparație independentă | Hard legacy la aceeași poziție, cu aceiași adversari Normal sau Hard; lumi, risc și plafon de tranziții aliniate, rotații agregate pe seed. |
| Model advers separat | Rafinarea continuării proprii nu schimbă tacit adversarii simulați; tactica legacy a modelului este declarată și verificată separat. |
| Dovadă publică | Captura sigură a țintei poate închide decizia `HAND_MARGIN` fără sampling; necunoscutul invalidează scurtătura și păstrează căutarea. |
| Oprire simetrică | Numai lumi complete pentru toate alternativele contribuie la estimări; fallback-ul și costul efectiv rămân vizibile. |
| Refuzul promovării | [Poarta evaluării](../../atf-tests/src/test/java/atf/rentz/OfflineArenaComponentTest.java) refuză familii lipsă, obiectiv nepotrivit, date insuficiente sau alt lot. |
| Incertitudine | Bootstrap finit pe seed-uri și corecție pe familii; nici samplerul, nici continuarea tactică nu sunt modele calibrate ale adversarului real. |

### Condiții rămase înaintea promovării

- Un orizont mai lung trebuie să depășească reperul pe obiectivul declarat, pe lot nou, fără ajustare după expunere. O regresie măsurată păstrează candidatul experimental și implicitul produsului neschimbat.
- Diagnosticul separă variația lumilor de eroarea politicii de continuare; o pierdere devine regresie mică numai cu justificarea independentă a deciziei.
- Pentru tactica live de joc în lipsa culorii, fixați varianta și comutatorul înaintea lotului. Comparați reperul și candidata pe aceleași distribuiri și rotații, separat pe contracte și număr de locuri. Raportați puncte, credit de victorie, cost și intervalul pereche pe seed. Un câștig pe subsetul tactic nu validează singur profilul global. [Ramura tactică](../../rentz-lab-app/game-core/src/main/java/ro/rentzlab/ai/TacticalPolicy.java)
- Profilul contextual rămâne separat de candidatul cu dovadă publică. Reguli locale plauzibile sau componente verzi nu justifică păstrarea lor într-un candidat care pierde comparația pereche.
- Integrarea Android trebuie să transporte registrul public complet și să alinieze codec-ul, identitatea politicii și APK-ul înaintea calificării native. Componentele JVM nu închid această frontieră.

## Ordine de rezolvare

Calificați separat progresul strategic Q08, integrarea nativă și prezentarea. [Regresia standard](../rentz/ATF_Qualification.md) păstrează verificarea mecanismelor stabilizate.

[Auditul bugetelor egale](../../../../rentz_back/infrastructure/governance/20261106-hard-equal-24c98bb8/TDD.md) păstrează comparațiile și verdictul pe lotul nou.

## Guvernanță și coeziune

Nivelurile testelor, corpusul documentar și revizuirea diferențelor Git au controale permanente în [calificarea consumatorului](../rentz/ATF_Qualification.md#guvernanța-permanentă). [Mecanismele AI](../rentz/ATF_Learning.md#separarea-mecanismelor-și-refactorizarea-sigură) separă registrul public, tranziția, estimările și agregarea experimentelor.

[Auditul Q11–Q12](../../../../rentz_back/infrastructure/governance/20261106-q11-q12-bb020681/TDD.md) păstrează probele RED/GREEN și comparația cu reperul compilat; o modificare de structură nu promovează profilul AI. Împachetarea API-ului generic de guvernanță rămâne [cerință comună separată](README.md#contract-generic-pentru-guvernanță).

## Distribuția APK

| Prioritate | Lacună delimitată | Criteriu de închidere |
|---|---|---|

| P2 | Contractele de transport și arhivă sunt adaptoare locale pentru distribuția produsului; reutilizarea printr-o bibliotecă comună nu este încă împachetată. | Prin ownerul comun, promovați un API generic cu nume de produs configurabil și oracole negative independente. Păstrați în consumator selecția versiunii, regulile Rentz și acceptanța consumatorului. |

[Procesul livrării](../rentz/ATF_Release.md) separă DRAFT, PREPARED și PUBLISHED; o pregătire validă nu este un APK calificat.

## Proveniența livrării și populația testelor

- **P2:** formalizați reconcilierea diferențelor exclusiv documentare într-un compositor versionat, cu controale negative. Receipt-ul original FAIL se păstrează, iar calificarea compusă identifică explicit execuțiile și diferența acceptată.

## Inventar executabil și admitere a evaluării

[Calificarea tehnică](../rentz/ATF_Qualification.md#inventarul-sursei-executabile) deține rezolvarea ierarhiei, interfețele externe revizuite și confruntarea cu Jupiter. [Admiterea evaluării](../rentz/ATF_Learning.md#admiterea-rezultatelor-reluate) deține contextul independent, populația completă și recalcularea agregărilor.

Cerințele Q15 și Q16 sunt protejate prin componente și controale permanente; modificarea sursei, a JAR-urilor ori a schemei cere recalificare. [Auditul extern](../../../../rentz_back/infrastructure/governance/20261107-q15-q16-8e2c2092/TDD.md) păstrează RED/GREEN și identitatea exactă, fără copierea rezultatelor în ghidurile de mecanism.

### Limite păstrate explicit

- Moștenirea din dependențe externe necunoscute cere surse disponibile ori o interfață testată ca lipsită de teste; o excepție nominală fără probă nu este un mecanism de descoperire.
- Admiterea unui rezumat coerent nu autentifică expeditorul și nu înlocuiește auditul tranzițiilor sau verificarea fișierelor jurnal.
- Progresul strategic și integrarea nativă păstrează criteriile separate de mai sus; remedierea porților nu promovează profilul AI.
