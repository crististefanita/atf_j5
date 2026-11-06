# Calitatea Rentz: integritate, adaptoare și guvernanță

## Pe scurt

**Deschis.** Registrul păstrează riscurile tehnice încă necalificate. [Regulile și fidelitatea](ATF_Rentz.md) și [prezentarea tablei și scorului](ATF_Rentz_UI_ToDo.md) au criterii proprii.

Validarea strictă are contract stabil în [învățare și admiterea datelor](../rentz/ATF_Learning.md), [salvarea sesiunii](../../rentz-lab-app/app/README.md) și [infrastructură](../rentz/ATF_Infrastructure.md). Trecerea acestor componente nu califică lifecycle-ul Android.

| ID | Prioritate | Statut și impact | Responsabil |
|---|---|---|---|
| Q04 | P1 | Risc din cod, nereprodus pe dispozitiv: un calcul vechi poate continua după distrugerea activității. | Controlerul și stocarea sesiunii. |
| Q05 | P2 | Inventarul sursei depășește limita de coeziune și include un runner Java fără pachet. | Arhitectura motorului și instrumentele. |
| Q06 | P2 | Adopția bibliotecilor nu are încă o poartă permanentă completă a consumatorului. | Integrarea binară; bibliotecile la responsabilul comun. |
| Q07 | P2 | Lipsesc probe negative proprii pentru procese, lifecycle, publicare și selecțiile de testare. | Adaptoarele de testare. |
| Q08 | P2 | Limita strategică este documentată; evaluarea curentă nu califică optimizarea întregii partide. | Politicile și protocolul AI. |
| Q09 | P2 | Navigarea și exemplele de pornire nu conduc uniform către infrastructura efectivă. | Documentația consumatorului. |

## Q04: calculul vechi nu mai este proprietarul sesiunii

În [MainActivity](../../rentz-lab-app/app/src/main/java/ro/rentzlab/app/MainActivity.java), `shutdown()` nu anulează lucrul început. Același worker poate încă salva în `session.json`, folosi numele comun `session.json.tmp` sau publica un callback după distrugerea activității. O activitate nouă poate deține deja altă sesiune; `ATOMIC_MOVE` nu decide care rezultat este încă actual.

Aceasta este o **analiză de risc**, nu o pierdere de date observată. Sunt necesare un proprietar unic al stării, o generație invalidabilă și aplicarea rezultatului numai dacă sesiunea și revizia capturate sunt încă actuale.

| Mecanism comparat | Ce se poate adapta din proiectul anterior | Limită |
|---|---|---|
| Calcul și livrare | [AiTurnCoordinator](../../../atf_j4/whist-lab-app/app/src/main/java/ro/whistlab/app/lifecycle/AiTurnCoordinator.java): anulare și verificarea generației inclusiv pentru callback deja pus în coadă. | Cancelarea unui Future nu este singură protecție împotriva scrierii vechi. |
| Aplicarea mutării | [AiTurnRequest](../../../atf_j4/whist-lab-app/app/src/main/java/ro/whistlab/app/lifecycle/AiTurnRequest.java): identitatea sesiunii și snapshot capturat. | Se adaptează la comenzi Rentz, fără obiective sau licitații Whist. |

**Închidere:** executori și magazin injectabile; teste deterministe pentru distrugere în calcul, callback întârziat, sesiune înlocuită, excepție la salvare și reluare validă. Intercalați comenzile controlat, fără a depinde de `sleep`; abia apoi calibrați lifecycle-ul real Android.

## Q05: coeziune și surse realmente guvernate

[Inventarul explicit](../../../../rentz_back/infrastructure/governance/20261106-p1-fixes-f5be18ef/source-governance.json) aplică principiile de inventar lexical și limita de coeziune ale responsabilului comun, printr-un adaptor pentru rădăcinile Maven, Android și tools. Nu este întreaga suită de guvernanță a producătorului.

- Separați vocabularul și regulile, starea partidei, inferența și politicile prin responsabilități clare; pachetul `ro.rentzlab.core` le amestecă acum. Nu creați câte un pachet per clasă doar pentru a satisface limita.
- [RentzNativeFlow](../../atf-tests/tools/RentzNativeFlow.java) trebuie să aibă pachet, sursă compilată declarată și verificări proprii; compilarea manuală nu îl face vizibil automat reactorului.
- Faceți permanente inventarul source-set-urilor mixte, limitele de import și coeziunea, cu probe pentru pachetul greșit, fișier lipsă și creștere neaprobată. Nu măriți pragul pentru a ascunde structura.
- Desfaceți metodele compacte din adaptor și codec pe operații citibile, după teste de caracterizare; separarea responsabilităților contează mai mult decât numărul de linii.

## Q06–Q07: adopție binară și probe de eșec

Există și o diferență de livrare: J5 declară Reporting J20, iar consumatorul comparat declară J21. [Identitățile comparate](../../../../rentz_back/infrastructure/governance/20261106-deep-audit/library-comparison.json) păstrează versiunile și hash-urile efective. Reconciliați livrarea responsabilului comun și schimbările relevante, apoi calificați adopția J5; nu copiați un JAR doar fiindcă are număr mai mare și nu rescrieți proveniența veche ca și cum ar fi acceptanță nouă.

| Arie | Cerință de închidere |
|---|---|
| Biblioteci | Verificați permanent perechile fizice JAR/POM, proveniența, hash-urile, absența surselor producătorului, originea claselor și contractele consumate. O copie cu proveniență a proiectului anterior nu este acceptanță moștenită. Păstrați Test Kit sursă până la adopția binară verificată; [rutarea responsabilității](README.md) rămâne unică. |
| Publicare | Eșec în pregătire, corp, închidere și publicare, păstrând cauza primară și erorile secundare; temporizarea publicării rămâne distinctă. Verificați expected/actual și populația selectată pe JAR-ul fizic. Nu modificați rendererul pentru a masca un defect al intrărilor. |
| Procese și Android | Mock-uri de proces pentru timeout, exit zero cu eroare, lipsa unui fișier nou, Unicode și proprietar schimbat. Testele nu ating emulatorul și nu folosesc un XML vechi drept succes. |
| Selecții | Intrări rapide, regresie, strategie/învățare, infrastructură și Android JVM explicite. Selecția standard trebuie să includă porțile relevante; o probă nouă nu devine invizibilă doar pentru că nu produce Extent. |
| Prezentare | Implementați proiecții și geometrie prin componente înaintea extinderii E2E; [backlog-ul UI](ATF_Rentz_UI_ToDo.md) deține matricea, tabla și scorul, fără duplicare aici. |

## Q08: forța AI se măsoară la obiectivul declarat

[Mecanismul curent](../rentz/ATF_Learning.md) optimizează marja mâinii pe lumi fezabile și un buget finit. Proba descriptivă cu aceleași cărți publice și totaluri diferite produce aceleași estimări; aceasta este limita căutării locale, nu o încălcare a legalității.

- Definiți separat optimizarea mediei de puncte, șansa de a câștiga partida și alocarea sub-jocurilor rămase. Ponderile schimbate nu demonstrează automat progres la toate trei.
- Extindeți regresiile situaționale pentru alegerea sub-jocului, cărți periculoase, pas și frontierele de sosire. O pierdere salvată devine regresie numai după justificarea independentă a așteptării.
- Comparați candidatul înghețat cu Normal și reperul păstrat, pe distribuiri noi și familii distincte; agregați pe distribuire, fără a trata rotațiile corelate drept observații independente.
- Păstrați incertitudinea samplerului, bugetul și identitatea YAML/fabricii/APK. Nu numiți „perfect” un câștig agregat și nu ajustați candidatul pe lotul destinat evaluării noi.

## Q09: documentația trebuie să arate mecanismul complet

- Completați [indexul docs](../README.md) cu traseele pentru AI, Android și infrastructură; documentul responsabil există, dar nu toate intrările conduc la el.
- Exemplele modulelor trebuie să pornească dintr-un workspace înregistrat și să precizeze PowerShell, JDK, SDK și directorul de execuție relevant; „copie temporară completă” nu definește singură contractul infrastructurii.
- În [observarea referinței](../../rentz-reference/README.md), distingeți regula declarată în resurse de regula observată în execuție și de interpretarea laboratorului.
- Izolați documentele istorice Contract Whist de traseul activ Rentz printr-un inventar și o regulă de retenție; nu lăsați ghiduri vechi la nivelul contractelor curente și nu mutați dovezile indicate de ele fără o arhivă verificabilă.
- Nu copiați scoruri de audit, numere de teste sau rezultate de partide în README-uri. Guvernanța de stil și linkuri trebuie completată cu verificarea afirmațiilor față de cod și cu probele negative ale contractului.

## Ordine de rezolvare

Q04 cu intercalări controlate → Q05–Q07 ca porți permanente → UI și evoluția AI pe criteriile proprii. Închideți fiecare lacună cu implementarea, identitatea sursei și proba relevantă; un raport integral verde pe populația veche nu o închide.
