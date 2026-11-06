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
| Q09 | P2 | Rute înregistrate, proveniență diferențiată și arhivă inventariată; contractul permanent protejează retenția și comenzile. | Documentația consumatorului. |

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
- Profilul contextual rămâne separat de candidatul cu dovadă publică. Reguli locale plauzibile sau componente verzi nu justifică păstrarea lor într-un candidat care pierde comparația pereche.
- Integrarea Android trebuie să transporte registrul public complet și să alinieze codec-ul, identitatea politicii și APK-ul înaintea calificării native. Componentele JVM nu închid această frontieră.

## Q09: documentația trebuie să arate mecanismul complet

| Contract implementat | Protecție permanentă |
|---|---|
| Pornire | README-urile modulelor indică rădăcina, PowerShell, JDK, condiția SDK și colecția lansatorului înregistrat. |
| Proveniență | [Observarea referinței](../../rentz-reference/README.md) distinge declarația publicată, resursa APK, observația de runtime și interpretarea laboratorului. |
| Istoric | [Arhiva Contract Whist](../history/contract-whist/README.md) are inventar, hash-uri și retenție; dovezile externe rămân la destinațiile originale. |
| Corpus complet | [Guvernanța documentară](../../atf-tests/src/test/java/atf/rentz/governance/DocumentationContractTest.java) verifică traseele și controlează negativ omisiunea, alterarea arhivei și lansatorul neînregistrat. |

Rezultatele, identitățile și pașii RED/GREEN rămân în [auditul extern](../../../../rentz_back/infrastructure/governance/20261106-q08-q09-ff1d7846/TDD.md). README-urile explică mecanismele; nu copiază scoruri de audit sau rezultate de partide.

[Auditul bugetelor egale](../../../../rentz_back/infrastructure/governance/20261106-hard-equal-24c98bb8/TDD.md) separă reproducerea reperului, ablațiile de dezvoltare, corecția controlului și verdictul pe lot nou. Nu atribuiți un câștig împotriva unei mese uniform Normal progresului față de versiunea Hard păstrată.

## Ordine de rezolvare

Păstrați Q09 prin colecția `infrastructure` și regresia standard. Calificați separat progresul strategic Q08, integrarea nativă și prezentarea; un raport verde al componentelor nu demonstrează superioritate de joc.

## Q11: paritatea guvernanței trebuie să fie permanentă și delimitată

**P2 · deschis · responsabil: integrarea testelor și documentația consumatorului.**

[Guvernanța sursei](../../atf-tests/src/test/java/atf/rentz/governance/SourceLayoutTest.java) protejează inventarul, coeziunea și dependențele. [Contractul documentar](../../atf-tests/src/test/java/atf/rentz/governance/DocumentationContractTest.java) protejează retenția și rutele de pornire; niciunul nu constituie singur controlul complet al stilului sau al nivelurilor executabile.

- Introduceți un control permanent al nivelului testelor Maven, cu adnotări compuse, forme calificate, ambiguități și clase ajutătoare distincte. Clasificați explicit probele Gradle, fără dependențe ATF în producția motorului.
- Integrați regulile documentare comune printr-un contract verificat: inventar complet de README-uri și ghiduri active, structură, linkuri și fragmente, stil, proză și legende. Păstrați arhiva istorică sub retenția ei explicită.
- Un audit extern cu validatorii comuni nu înlocuiește colecția permanentă; păstrați rădăcina și sursele validatorului identificabile, cu defect injectat și vecin valid.
- Schimbarea testelor-gate trebuie să păstreze criteriul independent, motivul și baza de comparație; metadata declarativă nu dovedește singură verificarea diferenței Git.

API-ul comun se solicită prin [responsabilitățile bibliotecilor](README.md); nu copiați întregul reactor sau implementarea bibliotecilor în consumator.

## Q12: lizibilitate și coeziune dincolo de numărul de fișiere

**P2 · deschis · responsabil: motorul, politicile și arena offline.**

[RentzMatch](../../rentz-lab-app/game-core/src/main/java/ro/rentzlab/core/RentzMatch.java), [DecisionEvaluator](../../rentz-lab-app/game-core/src/main/java/ro/rentzlab/ai/search/DecisionEvaluator.java) și [OfflineArena](../../atf-tests/src/main/java/atf/rentz/OfflineArena.java) comprimă tranziții, validări și agregări în instrucțiuni lungi. Limita de opt fișiere nu măsoară densitatea ori numărul motivelor de schimbare.

- Separați semantic restaurarea progresului public, tranziția de joc și agregarea experimentului; evitați un pachet artificial sau o clasă creată doar pentru prag.
- Declarați importurile și etapele evaluării explicit, păstrând fațadele, ordinea consumului aleatorului și plafonul reproductibil.
- Calificați refactorizarea prin aceleași observații publice, alegeri, estimări, scoruri și costuri deterministe; timpii se măsoară separat.
- Nu amestecați această lucrare cu promovarea profilului AI sau cu schimbarea interfeței.

## Distribuția APK

| Prioritate | Lacună delimitată | Criteriu de închidere |
|---|---|---|
| P1 | Traseul personal README → GitHub Release → fișier salvat în browser cere dovadă distinctă față de API și transferul nativ. | Versiune curentă, sesiune autentificată, bytes, SHA-256 și CRC; orice blocare a clientului rămâne diagnostic, fără substituirea cu un PASS API. |
| P2 | Contractele de transport și arhivă sunt adaptoare locale provenite din modelul J4; reutilizarea printr-o bibliotecă comună nu este încă împachetată. | Prin ownerul comun, promovați un API generic cu nume de produs configurabil și oracole negative independente. Păstrați în J5 selecția versiunii, regulile Rentz și acceptanța consumatorului. |

[Procesul livrării](../rentz/ATF_Release.md) separă DRAFT, PREPARED și PUBLISHED; o pregătire validă nu este un APK calificat.

## Proveniența livrării și populația testelor

- **P2:** unele invocări Gradle parametrizate au același display name în aceeași clasă. Introduceți identități XML neambigue; păstrați toate invocările originale și nu deduceți metode unice din etichete.
- **Contract permanent:** release-ul 0.1.1 califică snapshot-ul J21 identificat în dosar. Refactorizările live și Reporting J22 cer calificare proprie și versiune nouă; nu înlocuiți asset-ul publicat.
- **P2:** formalizați reconcilierea diferențelor exclusiv documentare într-un compositor versionat, cu controale negative. Receipt-ul original FAIL se păstrează, iar calificarea compusă identifică explicit execuțiile și diferența acceptată.
