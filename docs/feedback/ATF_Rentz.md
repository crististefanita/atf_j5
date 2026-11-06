# Lacunele active ale modelului Rentz

## Reguli și fidelitate

| Prioritate | Responsabil și diferență | Criteriu de închidere |
|---|---|---|
| P1 | Domeniul: catalogul păstrează magnitudini vizibile, dar asocierea cu „Classic”/„Traditional” și câmpurile rămase nu sunt confirmate. | Captură care identifică preset-ul și fiecare valoare, inclusiv recompensa Whist și sosirile etichetate pentru fiecare masă; comparații independente și dublare pe sub-joc. [Proveniența](../rentz/ATF_RuleSources.md) delimitează observația numerică. |
| P1 | Domeniul: instrucțiunile APK declară pachetul, levatele fără atu, captura 10♣ și excluderea ultimei alegeri de la pe-nevăzute; frontierele rămase și execuția originală au calificare distinctă. | Confirmați primul alegător, închiderea pentru dame/carouri, actorii terminați și ultimul loc în secvențe. Nu redeschideți ca necunoscute regulile deja ancorate în resurse; păstrați diferența dintre declarație și verificare în execuție. |
| P2 | Domeniul: Totale plus, dezactivarea sub-jocurilor și deschiderea permanentă din valet sunt declarate de autor, dar nu sunt implementate. | Programul derivă numărul mâinilor din selecția validată; fiecare loc consumă exact jocurile active. Deschiderea, legalitatea și terminarea folosesc explicit varianta selectată. |
| P2 | AI: alegerea Normal folosește încă reperul simplu, iar continuarea Hard presupune adversari tactici. | Calificați separat alegerea sub-jocului și cartea, pe familii și mese; un candidat nou păstrează sursa și datele rezervate, fără degradarea frontierelor informației. |
| P2 | AI: lumile sunt fezabile, fără uniformitate sau calibrare probabilistică demonstrată. | Identificați distribuția samplerului și familia adversă; orice ponderare după comportament are reguli și componente proprii, apoi evaluare pe date noi. |
| P2 | AI: căutarea valorifică mâna curentă; nu planifică alocarea sub-jocurilor rămase pentru întreaga partidă, iar controlerul adversarilor dezvăluie înaintea alegerii. | Comparați separat planificarea programului și o eventuală decizie pe nevăzute, luată înaintea accesului la mână. Respectați interdicția ultimei alegeri și evaluați pe distribuiri noi, fără a numi căutarea locală strategie global optimă. |

## Integrare și infrastructură

| Prioritate | Responsabil și diferență | Criteriu de închidere |
|---|---|---|
| P1 | Referința: pornirea poate produce WebView negru pe configurația Android inspectată. | Identificați runtime-ul și cauza prin probe delimitate; un ecran utilizabil și instrucțiuni lizibile preced calificarea regulilor. Instalarea sau PID-ul procesului nu constituie succes funcțional. |
| P2 | Instrumente: helperul PowerShell este separat de adaptorul Java și nu are încă o suită proprie de procese controlate. | Refuz, timeout, fluxuri, proprietar schimbat, eroare cu exit zero și lipsa fișierului nou sunt detectate fără dispozitiv. |
| P2 | Consumator: publicatorul minim păstrează comparațiile, dar calificarea fazelor eșuate și a temporizării cere probe negative proprii. | Cazuri cu eroare în pregătire/corp/închidere și publicație eșuată; păstrați cauza și separați costul publicării de timpul testului. |
| P2 | Android local: prezentarea este un prototip, iar un sanity cu o distribuire nu acoperă matricea sub-jocurilor, meselor și dimensiunilor. | Cazuri de componente pentru prezentarea stărilor, text și istoric, apoi E2E selectiv pentru secvențe, ținte negative/pozitive, pe nevăzute și mese de trei până la șase locuri. |
| P2 | Persistență: versiunea regulilor este explicită; compatibilitatea veche este limitată la reluarea validată a comenzilor, fără migrarea scorului sau a opțiunilor noi. | Calificați migrări reprezentative și refuzul sesiunilor incompatibile, cu păstrarea fișierului original. Orice schimbare de profil sau candidat cere identitate și strategie explicită. |

## Transferul generic

Kitul și JAR-urile au responsabilități comune, iar legalitatea și scorul aparțin domeniului Rentz. API-ul de guvernanță reutilizabil trebuie să accepte rădăcină, inventar și module mixte Maven/Gradle explicit, fără impunerea motorului altui joc.

## Istoric: extinderea calificării grafice

Sanity-ul verifică accesul la istoric și starea scorului, fără confruntarea fiecărei coloane după derulare orizontală. [Captura nativă](../../../../rentz_back/android-acceptance/20261106-003/native/checkpoint-06.png) păstrează viewportul original; coloanele din afara lui nu sunt calificate prin acea imagine.

Pentru închidere: componente de prezentare pentru toate locurile și scorurile, apoi o probă E2E cu derulare până la ultima coloană, etichete complete și comparații puncte/total pentru fiecare jucător. Nu înlocuiți captura originală cu un tabel recompus pentru a ascunde frontiera viewportului.

[Contractul regulilor](../rentz/ATF_Rules.md) · [Lecțiile transferului](../rentz/ATF_Transfer.md) · [Rutarea bibliotecilor](README.md)

## Adopția Test Kit

[Kit J0](../../../atf_j0/docs/feedback/ATF_Common_Test_Support_Feedback.md#kit-library-151--distribuție-unică-fără-surse-paralele) deține pachetul și API-ul. J5 păstrează proba Rentz și scriptul TMP.

library-provenance.json declară încă consumer atf_j4: păstrați acel fapt ca proveniență a copiei și înregistrați distinct adopția J5 înaintea calificării noului binar. Aceasta nu este acceptanță moștenită și nu cere inițializare Git.
