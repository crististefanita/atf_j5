# Calitatea Rentz: integritate, adaptoare și guvernanță

## Pe scurt

Registrul separă mecanismele implementate de promovarea strategică și de integrarea nativă încă necalificate. [Regulile și fidelitatea](ATF_Rentz.md) și [prezentarea tablei și scorului](ATF_Rentz_UI_ToDo.md) au criterii proprii.

## Q08: forța AI se măsoară la obiectivul declarat

**P2 · Responsabil: motorul AI și integrarea Android.** Promovarea candidatului la strategia implicită cere progres independent pe obiectivul declarat și admiterea codec-ului folosit de produs. [Contractul de evaluare](../rentz/ATF_Learning.md) · [Regresia de admitere](../../atf-tests/src/test/java/atf/rentz/OfflineArenaComponentTest.java)

### Condiții rămase înaintea promovării

- Un orizont mai lung trebuie să depășească reperul pe obiectivul declarat, pe lot nou, fără ajustare după expunere. O regresie măsurată păstrează candidatul experimental și implicitul produsului neschimbat.
- Diagnosticul separă variația lumilor de eroarea politicii de continuare; o pierdere devine regresie mică numai cu justificarea independentă a deciziei.
- Pentru tactica live de joc în lipsa culorii, fixați varianta și comutatorul înaintea lotului. Comparați reperul și candidata pe aceleași distribuiri și rotații, separat pe contracte și număr de locuri. Raportați puncte, credit de victorie, cost și intervalul pereche pe seed. Un câștig pe subsetul tactic nu validează singur profilul global. [Ramura tactică](../../rentz-lab-app/game-core/src/main/java/ro/rentzlab/ai/TacticalPolicy.java)
- Profilul contextual rămâne separat de candidatul cu dovadă publică. Reguli locale plauzibile sau componente verzi nu justifică păstrarea lor într-un candidat care pierde comparația pereche.
- Integrarea Android trebuie să transporte registrul public complet și să alinieze codec-ul, identitatea politicii și APK-ul înaintea calificării native. Componentele JVM nu închid această frontieră.

## Distribuția APK

| Prioritate | Lacună delimitată | Criteriu de închidere |
|---|---|---|

| P2 | Contractele de transport și arhivă sunt adaptoare locale pentru distribuția produsului; reutilizarea printr-o bibliotecă comună nu este încă împachetată. | Prin ownerul comun, promovați un API generic cu nume de produs configurabil și oracole negative independente. Păstrați în consumator selecția versiunii, regulile Rentz și acceptanța consumatorului. |
