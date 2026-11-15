# Lacunele active ale modelului Rentz

## Reguli și fidelitate

| Prioritate | Responsabil și diferență | Criteriu de închidere |
|---|---|---|
| P1 | Domeniul: catalogul păstrează magnitudini vizibile, dar asocierea cu „Classic”/„Traditional” și câmpurile rămase nu sunt confirmate. | Captură care identifică preset-ul și fiecare valoare, inclusiv recompensa Whist și sosirile etichetate pentru fiecare masă; comparații independente și dublare pe sub-joc. [Proveniența](../rentz/ATF_RuleSources.md) delimitează observația numerică. |
| P1 | Domeniul: instrucțiunile APK declară pachetul, levatele fără atu, captura 10♣ și excluderea ultimei alegeri de la pe-nevăzute; frontierele rămase și execuția originală au calificare distinctă. | Confirmați primul alegător, închiderea pentru dame/carouri, actorii terminați și ultimul loc în secvențe. Nu redeschideți ca necunoscute regulile deja ancorate în resurse; păstrați diferența dintre declarație și verificare în execuție. |
| P2 | Domeniul: Totale plus și dezactivarea sub-jocurilor rămân neimplementate. | Programul derivă numărul mâinilor din selecția validată; fiecare loc consumă exact jocurile active. [Regulile de deschidere și reluare](../rentz/ATF_Rules.md#pregătirea-mesei) aparțin contractului de produs. |
| P2 | AI. Alegerea Normal folosește reperul simplu, iar continuarea Hard presupune adversari tactici și distribuiri fezabile. | [Criteriile strategice](ATF_RentzQuality.md#q08-forța-ai-se-măsoară-la-obiectivul-declarat) urmăresc calibrarea distribuirilor, alegerea sub-jocului, jocul cărții și admiterea codec-ului Android. |

## Integrare și infrastructură

| Prioritate | Responsabil și diferență | Criteriu de închidere |
|---|---|---|
| P1 | Referința: pornirea poate produce WebView negru pe configurația Android inspectată. | Identificați runtime-ul și cauza prin probe delimitate; un ecran utilizabil și instrucțiuni lizibile preced calificarea regulilor. Instalarea sau PID-ul procesului nu constituie succes funcțional. |
| P2 | Persistență: versiunea regulilor este explicită; compatibilitatea veche este limitată la reluarea validată a comenzilor, fără migrarea scorului sau a opțiunilor noi. | Calificați migrări reprezentative și refuzul sesiunilor incompatibile, cu păstrarea fișierului original. Orice schimbare de profil sau candidat cere identitate și strategie explicită. |

## Platforma iOS

**P1 · Responsabil: produsul Rentz.** Prima decizie este includerea iOS în produs. După aprobare, criteriile de mai jos se aplică unei livrări distincte. [Calificarea curentă](../rentz/ATF_Release.md#calificarea-ios)

- **Componente.** Rulați aceiași vectori pentru reguli, scor, Normal/Hard, configurare și reluare pe motorul iOS ales. Testați separat persistența și lifecycle-ul adaptorului.
- **Acceptanță nativă.** Fixați versiunile Xcode și iOS folosite, verificați matricea declarată și probați pe un iPhone fizic instalarea, o partidă, salvarea și reluarea.
- **Distribuție.** Identificați artefactul, semnarea și receiptele canalului iOS. Într-o probă negativă, prezentați APK și dovezi Android pentru o livrare care declară și iOS. Poarta viitoare trebuie să identifice dovada iOS absentă și să blocheze acea calificare.

## Contracte înrudite

- [Guvernanță reutilizabilă și adopția Kit](README.md#contract-generic-pentru-guvernanță)
- [Retenția probelor](../rentz/ATF_Infrastructure.md#retenția-cache-urilor-și-a-fixture-urilor)
- [Prezentare și scor](ATF_Rentz_UI_ToDo.md)
