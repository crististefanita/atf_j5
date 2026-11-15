# Calitatea Rentz: integritate, adaptoare și guvernanță

## Pe scurt

Aici se urmăresc rezultatele necesare pentru promovarea AI și limitele de mentenanță ale adaptoarelor. [Regulile și fidelitatea](ATF_Rentz.md) și [prezentarea tablei și scorului](ATF_Rentz_UI_ToDo.md) au criterii proprii.

## Q08: forța AI se măsoară la obiectivul declarat

**P2 · Responsabil: motorul AI și integrarea Android.** Promovarea candidatului la strategia implicită cere progres independent pe obiectivul declarat și admiterea codec-ului folosit de produs. [Contractul de evaluare](../rentz/ATF_Learning.md) · [Regresia de admitere](../../atf-tests/src/test/java/atf/rentz/OfflineArenaComponentTest.java)

- Un orizont mai lung trebuie să depășească reperul pe obiectivul declarat, pe lot nou, fără ajustare după expunere. O regresie măsurată păstrează candidatul experimental și implicitul produsului neschimbat.
- Identificați distribuția samplerului și familia de politici adverse, apoi separați variația lumilor de eroarea continuării. O pierdere devine regresie mică după justificarea independentă a deciziei.
- Pentru tactica live de joc în lipsa culorii, fixați varianta și comutatorul înaintea lotului. Comparați reperul și candidata pe aceleași distribuiri și rotații, separat pe contracte și număr de locuri. Raportați puncte, credit de victorie, cost și intervalul pereche pe seed. Un câștig pe subsetul tactic nu validează singur profilul global. [Ramura tactică](../../rentz-lab-app/game-core/src/main/java/ro/rentzlab/ai/TacticalPolicy.java)
- Profilul contextual rămâne separat de candidatul cu dovadă publică. Reguli locale plauzibile sau componente verzi nu justifică păstrarea lor într-un candidat care pierde comparația pereche.
- Integrarea Android trebuie să transporte registrul public complet și să alinieze codec-ul, identitatea politicii și APK-ul înaintea calificării native. Componentele JVM nu închid această frontieră.

## Limitele orchestrării

**P2 · Responsabil: calificarea J5.** [Runnerul Android](../../atf-tests/tools/run-shared-ui-sanity.ps1) combină comenzile ADB, extragerea UI, scenariile și compunerea dovezilor. O schimbare de selector traversează aceste responsabilități.

- Separați transportul ADB de oracolul scenariului. Păstrați intrările publice, ordinea porților, codurile de diagnostic și schema Extent.
- Verificați la componentă o ierarhie Android întârziată și un vecin valid, cu fixture-uri locale și un adaptor ADB simulat. Păstrați traseul nativ consecutiv pentru verificarea integrării.

## Distribuția APK

| Prioritate | Lacună delimitată | Criteriu de închidere |
|---|---|---|

| P2 | Contractele de transport și arhivă sunt adaptoare locale pentru distribuția produsului; reutilizarea printr-o bibliotecă comună nu este încă împachetată. | Prin ownerul comun, promovați un API generic cu nume de produs configurabil și oracole negative independente. Păstrați în consumator selecția versiunii, regulile Rentz și acceptanța consumatorului. |
