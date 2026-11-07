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

[Registrul de calitate](ATF_RentzQuality.md) deține adopția binară și probele încă lipsă ale adaptoarelor. [Lifecycle-ul](../rentz/ATF_Lifecycle.md), [guvernanța sursei](../rentz/ATF_Components.md) și validarea strictă au contracte și componente proprii; acceptanța nativă a pauzei, relansării și filesystem-ului rămâne distinctă de JVM.

| Prioritate | Responsabil și diferență | Criteriu de închidere |
|---|---|---|
| P1 | Referința: pornirea poate produce WebView negru pe configurația Android inspectată. | Identificați runtime-ul și cauza prin probe delimitate; un ecran utilizabil și instrucțiuni lizibile preced calificarea regulilor. Instalarea sau PID-ul procesului nu constituie succes funcțional. |
| P1 | Android local: tabla Rentz, contextul mutărilor și tabela de scor sunt încă prezentări de prototip. | Închideți [ToDo-urile de interfață](ATF_Rentz_UI_ToDo.md): deschidere J reală, geometrie stabilă, scor explicabil și toate coloanele accesibile, prin componente și acceptanță nativă selectivă. |
| P2 | Persistență: versiunea regulilor este explicită; compatibilitatea veche este limitată la reluarea validată a comenzilor, fără migrarea scorului sau a opțiunilor noi. | Calificați migrări reprezentative și refuzul sesiunilor incompatibile, cu păstrarea fișierului original. Orice schimbare de profil sau candidat cere identitate și strategie explicită. |

## Transferul generic

Kitul și JAR-urile au responsabilități comune, iar legalitatea și scorul aparțin domeniului Rentz. API-ul de guvernanță reutilizabil trebuie să accepte rădăcină, inventar și module mixte Maven/Gradle explicit, fără impunerea motorului altui joc.

## Guvernanța infrastructurii

[Contractul implementat](../rentz/ATF_Infrastructure.md) păstrează registrul, allocatorul și probele roșu/verde la consumator. Extinderea către alte proiecte trebuie să adopte explicit rădăcinile, proprietarii și producătorii lor; o rulare trecută aici nu certifică toate directoarele temporare ale celorlalte proiecte sau ale sistemului.

| Prioritate | Cerință rămasă | Criteriu de închidere |
|---|---|---|
| P2 | Retenția copiilor istorice referite de receipt-uri și classpath-uri trebuie reconciliată înaintea unei curățări mai largi. | Inventar cu proprietar și dovada utilizării, arhivă portabilă verificată și eliminare numai după eliberarea proceselor; nu redenumiți proveniența istorică pentru a obține verde. |

## Prezentare și tabelă de scor

[Backlog-ul UI](ATF_Rentz_UI_ToDo.md) centralizează constatările, capturile și criteriile de închidere. Accesul la istoric și acordul stării cu oracolul nu califică singure lizibilitatea, toate coloanele după derulare sau păstrarea tablei terminale.

[Contractul regulilor](../rentz/ATF_Rules.md) · [Lecțiile transferului](../rentz/ATF_Transfer.md) · [Rutarea bibliotecilor](README.md)

## Adopția Test Kit

[Responsabilul Kit](../../../atf_j0/docs/feedback/ATF_Common_Test_Support_Feedback.md#kit-library-151--distribuție-unică-fără-surse-paralele) deține pachetul și API-ul. Consumatorul păstrează proba Rentz și lansatorul izolat.

Identitatea copiei inițiale rămâne în istoricul provenienței; adopția Kit are identitate proprie la consumator. Componentele Rentz verifică binarul, iar scriptul păstrează inventarul reactorului fără surse Kit; acceptanța Android rămâne separată.
