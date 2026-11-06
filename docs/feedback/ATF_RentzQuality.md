# Calitatea Rentz: integritate, adaptoare și guvernanță

## Pe scurt

**Deschis.** Registrul păstrează riscurile tehnice încă necalificate. [Regulile și fidelitatea](ATF_Rentz.md) și [prezentarea tablei și scorului](ATF_Rentz_UI_ToDo.md) au criterii proprii.

Validarea strictă are contract stabil în [învățare și admiterea datelor](../rentz/ATF_Learning.md), [salvarea sesiunii](../../rentz-lab-app/app/README.md) și [infrastructură](../rentz/ATF_Infrastructure.md). [Lifecycle-ul](../rentz/ATF_Lifecycle.md) și [guvernanța sursei](../rentz/ATF_Components.md) au contracte și componente proprii; execuția nativă se califică separat.

| ID | Prioritate | Statut și impact | Responsabil |
|---|---|---|---|
| Q06 | P2 | Adopția bibliotecilor nu are încă o poartă permanentă completă a consumatorului. | Integrarea binară; bibliotecile la responsabilul comun. |
| Q07 | P2 | Lipsesc probe negative proprii pentru procese, lifecycle, publicare și selecțiile de testare. | Adaptoarele de testare. |
| Q08 | P2 | Limita strategică este documentată; evaluarea curentă nu califică optimizarea întregii partide. | Politicile și protocolul AI. |
| Q09 | P2 | Navigarea și exemplele de pornire nu conduc uniform către infrastructura efectivă. | Documentația consumatorului. |

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

- Exemplele modulelor trebuie să pornească dintr-un workspace înregistrat și să precizeze PowerShell, JDK, SDK și directorul de execuție relevant; „copie temporară completă” nu definește singură contractul infrastructurii.
- În [observarea referinței](../../rentz-reference/README.md), distingeți regula declarată în resurse de regula observată în execuție și de interpretarea laboratorului.
- Izolați documentele istorice Contract Whist de traseul activ Rentz printr-un inventar și o regulă de retenție; nu lăsați ghiduri vechi la nivelul contractelor curente și nu mutați dovezile indicate de ele fără o arhivă verificabilă.
- Nu copiați scoruri de audit, numere de teste sau rezultate de partide în README-uri. Guvernanța de stil și linkuri trebuie completată cu verificarea afirmațiilor față de cod și cu probele negative ale contractului.

## Ordine de rezolvare

Q06–Q07 ca porți permanente → calibrare nativă, UI și evoluția AI pe criteriile proprii. Închideți fiecare lacună cu implementarea, identitatea sursei și proba relevantă; un raport integral verde pe populația veche nu o închide.
