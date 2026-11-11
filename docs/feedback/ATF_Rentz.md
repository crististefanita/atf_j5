# Lacunele active ale modelului Rentz

## Reguli și fidelitate

| Prioritate | Responsabil și diferență | Criteriu de închidere |
|---|---|---|
| P1 | Domeniul: catalogul păstrează magnitudini vizibile, dar asocierea cu „Classic”/„Traditional” și câmpurile rămase nu sunt confirmate. | Captură care identifică preset-ul și fiecare valoare, inclusiv recompensa Whist și sosirile etichetate pentru fiecare masă; comparații independente și dublare pe sub-joc. [Proveniența](../rentz/ATF_RuleSources.md) delimitează observația numerică. |
| P1 | Domeniul: instrucțiunile APK declară pachetul, levatele fără atu, captura 10♣ și excluderea ultimei alegeri de la pe-nevăzute; frontierele rămase și execuția originală au calificare distinctă. | Confirmați primul alegător, închiderea pentru dame/carouri, actorii terminați și ultimul loc în secvențe. Nu redeschideți ca necunoscute regulile deja ancorate în resurse; păstrați diferența dintre declarație și verificare în execuție. |
| P2 | Domeniul: Totale plus, dezactivarea sub-jocurilor și deschiderea permanentă din valet sunt declarate de autor, dar nu sunt implementate. | Programul derivă numărul mâinilor din selecția validată; fiecare loc consumă exact jocurile active. Deschiderea, legalitatea și terminarea folosesc explicit varianta selectată. |
| P2 | AI: alegerea Normal folosește încă reperul simplu, iar continuarea Hard presupune adversari tactici. | Calificați separat alegerea sub-jocului și cartea, pe familii și mese; un candidat nou păstrează sursa și datele rezervate, fără degradarea frontierelor informației. |
| P2 | AI: lumile sunt fezabile, fără uniformitate sau calibrare probabilistică demonstrată. | Identificați distribuția samplerului și familia adversă; orice ponderare după comportament are reguli și componente proprii, apoi evaluare pe date noi. |
| P2 | AI: continuarea completă există offline, dar selecția viitoare a sub-jocurilor rămâne tactică; produsul implicit și codec-ul Android nu sunt promovate la candidatul pe partidă. | [Protocolul strategic](ATF_RentzQuality.md#q08-forța-ai-se-măsoară-la-obiectivul-declarat) cere progres independent, integrarea registrului public și o eventuală decizie pe nevăzute înaintea accesului la mână. Nu echivalați continuarea simulată cu planificarea optimă. |

## Integrare și infrastructură

<a id="închiderea-contractelor-cu-țintă-fixă"></a>

[Regulile și punctajul](../rentz/ATF_Rules.md) dețin contractul local de închidere timpurie. Tabelul urmărește verificările încă deschise pentru referință și integrarea nativă.

[Registrul de calitate](ATF_RentzQuality.md) deține adopția binară și probele încă lipsă ale adaptoarelor. [Lifecycle-ul](../rentz/ATF_Lifecycle.md), [guvernanța sursei](../rentz/ATF_Components.md) și validarea strictă au contracte și componente proprii; acceptanța nativă a pauzei, relansării și filesystem-ului rămâne distinctă de JVM.

| Prioritate | Responsabil și diferență | Criteriu de închidere |
|---|---|---|
| P1 | Referința: pornirea poate produce WebView negru pe configurația Android inspectată. | Identificați runtime-ul și cauza prin probe delimitate; un ecran utilizabil și instrucțiuni lizibile preced calificarea regulilor. Instalarea sau PID-ul procesului nu constituie succes funcțional. |
| P1 | Android local: integrarea mesei și a istoricului este livrată. Mai rămân opțiunea „Valet mereu” și probele native pentru geometrie compactă, ritmul actorilor și clasamentul final. | Închideți probele de componentă și de acceptanță nativă selectivă din [backlog-ul UI](ATF_Rentz_UI_ToDo.md). Contractele livrate sunt în [ghidul integrării](../rentz/ATF_UiIntegration.md). |
| P2 | Persistență: versiunea regulilor este explicită; compatibilitatea veche este limitată la reluarea validată a comenzilor, fără migrarea scorului sau a opțiunilor noi. | Calificați migrări reprezentative și refuzul sesiunilor incompatibile, cu păstrarea fișierului original. Orice schimbare de profil sau candidat cere identitate și strategie explicită. |
| P1 | iOS: cererea de suport iPhone nu are încă țintă, build sau calificare proprie în J5. | Contractul actual [descrie livrarea Android](../rentz/ATF_Release.md). Dacă iOS rămâne în scop, adăugați criterii și receipte de release proprii; APK-ul și testele Android nu îl califică. |

## Platforma iOS

Acesta este un obiectiv deschis al produsului, nu o afirmație că J5 conține deja o aplicație iOS. Documentația și artefactele Android rămân valabile pentru Android; ele nu închid criteriul iOS. Până când există țintă nativă și receipte separate, RAG-ul raportează cerința ca neimplementată/necalificată, fără să transforme APK-ul sau testele Android în dovadă iOS.

Păstrați contractele de domeniu și calificați separat implementarea nativă.

- **Reguli:** păstrați aceleași contracte de reguli, scor, Normal/Hard, configurare și reluare.
- **Motor:** decideți înaintea portării între un motor comun portabil și o reimplementare verificată prin aceiași vectori de conformitate.
- **Aplicație:** UI-ul, ciclul de viață și stocarea nativă au teste și identitate de release proprii.
- **Suport declarat:** fixați matricea versiunilor stabile, treceți testele offline/componente și verificați instalarea pe un dispozitiv fizic.

## Transferul generic

Kitul și JAR-urile au responsabilități comune, iar legalitatea și scorul aparțin domeniului Rentz. API-ul de guvernanță reutilizabil trebuie să accepte rădăcină, inventar și module mixte Maven/Gradle explicit, fără impunerea motorului altui joc.

## Guvernanța infrastructurii

[Contractul implementat](../rentz/ATF_Infrastructure.md) păstrează registrul, allocatorul și probele roșu/verde la consumator. Extinderea către alte proiecte trebuie să adopte explicit rădăcinile, proprietarii și producătorii lor; o rulare trecută aici nu certifică toate directoarele temporare ale celorlalte proiecte sau ale sistemului.

| Prioritate | Cerință rămasă | Criteriu de închidere |
|---|---|---|
| P2 | Retenția copiilor istorice referite de receipt-uri și classpath-uri trebuie reconciliată înaintea unei curățări mai largi. | Inventar cu proprietar și dovada utilizării, arhivă portabilă verificată și eliminare numai după eliberarea proceselor; nu redenumiți proveniența istorică pentru a obține verde. |

## Prezentare și tabelă de scor

[Ghidul integrării](../rentz/ATF_UiIntegration.md#istoricul-adaptiv-rentz) păstrează dovada accesului la coloane după derulare. [Backlog-ul UI](ATF_Rentz_UI_ToDo.md) păstrează criteriile deschise pentru clasament, lizibilitatea mesei și tabla terminală.

[Contractul regulilor](../rentz/ATF_Rules.md) · [Lecțiile transferului](../rentz/ATF_Transfer.md) · [Rutarea bibliotecilor](README.md)

## Adopția Test Kit

[Responsabilul Kit](../../../atf_j0/docs/feedback/ATF_Common_Test_Support_Feedback.md#kit-library-151--distribuție-unică-fără-surse-paralele) deține pachetul și API-ul. Consumatorul păstrează proba Rentz și lansatorul izolat.

Identitatea copiei inițiale rămâne în istoricul provenienței; adopția Kit are identitate proprie la consumator. Componentele Rentz verifică binarul, iar scriptul păstrează inventarul reactorului fără surse Kit; acceptanța Android rămâne separată.
