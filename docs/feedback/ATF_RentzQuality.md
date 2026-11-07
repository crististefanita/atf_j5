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

## Guvernanță și coeziune

Nivelurile testelor, corpusul documentar și revizuirea diferențelor Git au controale permanente în [calificarea consumatorului](../rentz/ATF_Qualification.md#guvernanța-permanentă). [Mecanismele AI](../rentz/ATF_Learning.md#separarea-mecanismelor-și-refactorizarea-sigură) separă registrul public, tranziția, estimările și agregarea experimentelor.

[Auditul Q11–Q12](../../../../rentz_back/infrastructure/governance/20261106-q11-q12-bb020681/TDD.md) păstrează probele RED/GREEN și comparația cu reperul compilat; o modificare de structură nu promovează profilul AI. Împachetarea API-ului generic de guvernanță rămâne [cerință comună separată](README.md#contract-generic-pentru-guvernanță).

## Distribuția APK

| Prioritate | Lacună delimitată | Criteriu de închidere |
|---|---|---|
| P1 | Traseul personal README → GitHub Release → fișier salvat în browser cere dovadă distinctă față de API și transferul nativ. | Versiune curentă, sesiune autentificată, bytes, SHA-256 și CRC; orice blocare a clientului rămâne diagnostic, fără substituirea cu un PASS API. |
| P2 | Contractele de transport și arhivă sunt adaptoare locale pentru distribuția produsului; reutilizarea printr-o bibliotecă comună nu este încă împachetată. | Prin ownerul comun, promovați un API generic cu nume de produs configurabil și oracole negative independente. Păstrați în consumator selecția versiunii, regulile Rentz și acceptanța consumatorului. |

[Procesul livrării](../rentz/ATF_Release.md) separă DRAFT, PREPARED și PUBLISHED; o pregătire validă nu este un APK calificat.

## Proveniența livrării și populația testelor

- **P2:** unele invocări Gradle parametrizate au același display name în aceeași clasă. Introduceți identități XML neambigue; păstrați toate invocările originale și nu deduceți metode unice din etichete.
- **Contract permanent:** release-ul 0.1.1 califică snapshot-ul J21 identificat în dosar. Refactorizările live și Reporting J22 cer calificare proprie și versiune nouă; nu înlocuiți asset-ul publicat.
- **P2:** formalizați reconcilierea diferențelor exclusiv documentare într-un compositor versionat, cu controale negative. Receipt-ul original FAIL se păstrează, iar calificarea compusă identifică explicit execuțiile și diferența acceptată.

## Inventar executabil și admitere a evaluării

[Calificarea tehnică](../rentz/ATF_Qualification.md#inventarul-sursei-executabile) deține rezolvarea ierarhiei, interfețele externe revizuite și confruntarea cu Jupiter. [Admiterea evaluării](../rentz/ATF_Learning.md#admiterea-rezultatelor-reluate) deține contextul independent, populația completă și recalcularea agregărilor.

Cerințele Q15 și Q16 sunt protejate prin componente și controale permanente; modificarea sursei, a JAR-urilor ori a schemei cere recalificare. [Auditul extern](../../../../rentz_back/infrastructure/governance/20261107-q15-q16-8e2c2092/TDD.md) păstrează RED/GREEN și identitatea exactă, fără copierea rezultatelor în ghidurile de mecanism.

### Limite păstrate explicit

- Moștenirea din dependențe externe necunoscute cere surse disponibile ori o interfață testată ca lipsită de teste; o excepție nominală fără probă nu este un mecanism de descoperire.
- Admiterea unui rezumat coerent nu autentifică expeditorul și nu înlocuiește auditul tranzițiilor sau verificarea fișierelor jurnal.
- Progresul strategic și integrarea nativă păstrează criteriile separate de mai sus; remedierea porților nu promovează profilul AI.

## Constatări din revizia curentă

| ID | Prioritate și statut | Proprietar și limită |
|---|---|---|
| Q17 | P2 · remediat în inventar și selector | Containerele Nested moștenite au proprietari binari verificabili în XML. |
| Q18 | P2 · remediat în arenă și admitere | Obiectivul, profilul cerut și politicile efective au identități distincte. |
| Q19 | P2 · remediat în registrul public | Restaurarea cere o alocare posibilă a scorului pentru contractul și profilul declarate. |
| Q20 | P1 · remediat | Protocolul, registrul și agregarea refuză aliasurile aceleiași stări efective. |
| Q21 | P2 · remediat | Profilul de scor executat este declarat și confruntat cu profilul de încredere. |
| Q22 | P2 · remediat | Poarta Surefire confruntă cazurile, proprietarul și contoarele suitelor simple. |

### Q17 · Descoperirea containerelor și atribuirea cazurilor

- Regresiile confruntă moștenirea directă și tranzitivă cu Jupiter, inclusiv o clasă interioară omonimă și containere statice. Selectorul refuză numele neconvențional.
- Poarta verifică proprietarii cazurilor din XML și refuză clasele binare comune mai multor rădăcini. Păstrați refuzul până când există o probă de atribuire per rădăcină. [Contractul selectorului](../rentz/ATF_Qualification.md#containere-jupiter-imbricate)

### Q18 · Identitatea actorilor de evaluare

- Aceleași descrieri ale actorilor construiesc jucătorii și alimentează rezumatul, admiterea și raportul.
- Reperul Hard este legacy chiar dacă profilul solicitat este contextual. Normal și Baseline au identități distincte, fără setări de căutare.
- Admiterea refuză o identitate lipsă sau schimbată pe oricare rol, chiar cu același buget. [Contractul evaluării](../rentz/ATF_Learning.md)

[Auditul extern Q17–Q18](../../../../rentz_back/infrastructure/governance/20261107-q17-q18/TDD.md) leagă probele roșii, verificarea XML și rulările de calificare.

### Q19 · Fezabilitatea scorurilor în registrul public

- Poarta refuză un fond distribuit în unități imposibile, împărțirea premiului pentru o carte unică și premiile de sosire incompatibile cu profilul. Refuzul intervine înainte de construirea continuării.
- Pentru Totale, alocarea trebuie să păstreze suprapunerea damei de caro și capacitatea levatelor. Un oracol independent confruntă această condiție, iar jocurile motorului oferă vecini legali pe toate dimensiunile mesei.
- Păstrați validarea aritmetică separată de auditul mutărilor. Dacă apar reguli de punctaj noi, adăugați atât vectorul imposibil cu fond corect, cât și vecinul legal pentru profilul respectiv. [Contractul registrului](../rentz/ATF_Architecture.md#admiterea-scorurilor-din-registru)

[Auditul extern Q19](../../../../rentz_back/infrastructure/governance/20261107-q19/TDD.md) păstrează probele roșii și verzi și verdictul de guvernanță.

### Q20 · Identitatea distribuirii depinde de generator

- [Identitatea generatorului](../rentz/ATF_Learning.md#identitatea-distribuirii-și-expunerea) este comună motorului, protocolului, registrului și agregării. Etichetele originale și schema registrului rămân disponibile pentru reproducere.
- Regresiile acoperă aliasurile din lot și din istoricul v1, toate combinațiile de faze și refuzul înainte de a produce dovezi de partidă. Vecinii păstrează reluarea explicită în dezvoltare, masele diferite și stările realmente distincte.
- Păstrați criteriul la o schimbare de generator. Demonstrați identitatea efectivă și conservarea istoricului înainte de a redeschide evaluarea rezervată. [Probe roșu–verde](../../../../rentz_back/infrastructure/governance/20261107-q20/TDD.md)

### Q21 · Admiterea cere identitatea profilului de scor

- [Admiterea](../../atf-tests/src/main/java/atf/rentz/arena/PromotionAdmission.java) cere profilul executat în rezultat și profilul de încredere în context. Extent și partidele reținute păstrează valorile efective.
- Regresiile confruntă două rulări reale, un rezumat istoric fără profil și mutații ale profilului declarat. Manifestul CLI trebuie să acopere fișierul de scor. [Probe roșu–verde](../../../../rentz_back/infrastructure/governance/20261107-q21-q22/TDD.md)

### Q22 · Populația Surefire se verifică din cazurile raportate

- [Poarta populației](../../scripts/TestCollections.psm1) refuză contoarele contradictorii, cazurile fără nume și proprietarii străini în suitele simple. Grupurile imbricate păstrează atribuirea prin inventar când Surefire consolidează cazurile.
- Lansatorul păstrează raportul brut și diagnosticul de populație la refuz. Regresiile acoperă atât invocări multiple valide, cât și lipsa cazurilor sau a proprietarului corect. [Probe roșu–verde](../../../../rentz_back/infrastructure/governance/20261107-q21-q22/TDD.md)

## Coordonarea porților în timpul integrării UI

Integrarea paralelă a introdus teste noi și un UiAuditRule bazat pe org.junit.rules.TestRule. Proprietarul integrării trebuie să reconcilieze inventarul sursei, metadatele de nivel, frontiera externă JUnit 4 și controalele exacte ale testelor înaintea unei calificări globale. O interfață aprobată nominal cere și probă pe dependența build-ului care o folosește; nu relaxați rezolvarea necunoscutului pentru a obține verde.

- Păstrați modificările UI și README-urile sale la proprietarul lor; reviewerul nu aprobă automat octeți pe care nu i-a verificat.
- Registrul declară TestRule, iar verificarea reflectivă Maven nu găsește clasa. Legați aprobarea de classpath-ul build-ului care consumă interfața și verificați acolo proveniența și absența metodelor de test. [Proba Maven](../../../../rentz_back/infrastructure/governance/20261107-q17-q18/red-maven.log)
- După încheierea integrării, fixați un snapshot nou și rulați infrastructura fără drift ori schimbare de populație. Calificarea unei selecții de documente nu înlocuiește această poartă.
- Revizia după Q19 păstrează refuzurile de control Git, structură README de scor și classpath Maven la ownerul integrării. [Verdictul infrastructurii](../../../../rentz_back/components/20261107-post-q19-review/result.json)
- S05/S06 a corectat structura README-ului de scor și a înregistrat hash-ul testului propriu. Regresia de livrare a rămas refuzată la frontiera JUnit 4 și la controalele Git ale schimbărilor paralele. Reluați regresia din surse stabilizate înainte de a schimba release-ul curent. [Rularea de livrare](../../../../rentz_back/components/settlement-release-retry-j5/result.json) · [Reverificarea README](../../../../rentz_back/ui-review/settlement-details-j5/documentation-structure.log)
- [Rezultatul inițial](../../../../rentz_back/components/20261107-live-review-governance/result.json) și [preflight-ul live](../../../../rentz_back/infrastructure/governance/20261107-live-review-3ee0e439/live-preflight.json) rămân refuzate; nu sunt corectate retrospectiv în PASS.
