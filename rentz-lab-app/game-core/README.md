# Motorul de reguli Rentz

## Pe scurt

Model Java fără dependențe Android sau ATF în producție. Deține distribuirea, cele opt sub-jocuri, secvențele, rotația, refuzul și scorul unui profil explicit.

## Organizare și dependențe

| Pachet | Responsabilitate |
|---|---|
| [`ro.rentzlab.core`](src/main/java/ro/rentzlab/core/) | Vocabularul, starea partidei, legalitatea, secvențele și scorul; nu depinde de politicile AI. |
| [`ro.rentzlab.core.progress`](src/main/java/ro/rentzlab/core/progress/) | Validarea registrului public și reconstrucția portofoliului; fără politică sau informație privată. |
| [`ro.rentzlab.ai`](src/main/java/ro/rentzlab/ai/) | Portul jucătorului, tactica, inferența și căutarea; consumă observațiile și API-ul motorului. |
| [`ro.rentzlab.ai.search`](src/main/java/ro/rentzlab/ai/search/) | Evaluarea pe loturi comune și bugetul determinist de tranziții; nu primește starea reală privată a mesei. |
| [`ro.rentzlab.ai.tactics`](src/main/java/ro/rentzlab/ai/tactics/) | Dovezi publice pentru ținte și reguli locale de cost; distincte de oracolul scorului. |

Aliasurile regulilor din YAML se rezolvă prin registrul tipizat al claselor publice, fără a presupune că toate implementările au același pachet. [Guvernanța sursei](../../config/java-source-layout.json) declară rădăcinile și direcțiile permise; mutarea unei clase nu schimbă punctajul sau ponderile.

## Contracte

| API și mecanism | Responsabilitate |
|---|---|
| `Card.deck` | Pachet distinct cu opt cărți pentru fiecare loc. |
| `OpeningDeal`, `RentzMatch.fromOpeningDeal` | Primă distribuire controlată, validată și imuabilă; continuarea folosește seed separat. |
| `RentzMatch.fromPublicProgress` | Reface scorul și alegerile rămase din registrul public validat, cu distribuire și viitor ipotetice. |
| `TrickRules` | Urmarea culorii și câștigătorul fără atu. |
| `RentzBoard` | Extinderea contiguă pe culori. |
| `ScoreProfile` | Ponderi de punctaj, cumul și interval numeric sigur. |
| `RentzMatch` | Faze, alegeri, acțiuni, evenimente publice și istoric. |
| `LabPolicy` | Reper simplu care primește numai observația actorului. |
| `RentzPlayer` | Port stabil cu niveluri Normal și Hard, pe observația actorului. |
| `TacticalPolicy` | Ținte negative, conservarea puterii și continuări Rentz. |
| `KnowledgeSampler` | Cărți necunoscute, culori absente, pasuri și distribuiri fezabile. |
| `SearchPolicy`, `PolicySettings` | Lumi comune, obiectiv declarat, orizont de mână sau partidă și buget/ponderi explicite. |
| `MatchUtility`, `FullMatchRollout` | Utilități finale separate și continuare ipotetică pe istoricul public; candidat offline, fără schimbarea implicită a produsului. |
| `DecisionMoments` | Helper intern pentru loturi complete, risc și departajare stabilă; fără generare de lumi, I/O sau expunere în portul jucătorului. |
| `DecisionEvaluator`, `RolloutBudget` | Numai lumi complet evaluate pentru toate alternativele; oprire simetrică și fallback public explicit. |
| `TargetTactics` | Captură demonstrabilă a țintei și penalitatea propriei cărți; continuarea adversă păstrează modelul legacy declarat. |

`laboratory()` este profil provizoriu, separat de preset-urile originale neconfirmate. [Regulile](../../docs/rentz/ATF_Rules.md) dețin semantica și limitele, iar [manifestul](../../config/rentz-rules.yaml) indică statutul fiecărei afirmații.

## Verificare

Din rădăcina proiectului, în PowerShell 7, cu JDK 21, Maven și cache-ul pregătit, fără SDK Android:

```powershell
.\scripts\run-rentz-components.ps1 -Collection learning
```

[Infrastructura](../../docs/rentz/ATF_Infrastructure.md) creează copia înregistrată și păstrează rezultatele extern. Selecția `learning` confruntă politicile și protocolul arenei; `regression` adaugă restul componentelor și cere SDK Android pentru etapa JVM a aplicației.

| Familie | Verificare |
|---|---|
| [Reguli](src/test/java/ro/rentzlab/core/RentzRulesTest.java) | Pachet, levate, alegere, blind și partide virtuale. |
| [Secvențe](src/test/java/ro/rentzlab/core/RentzSequenceTest.java) | Frontiere ale turei, închidere și memorie publică. |
| [Scor](src/test/java/ro/rentzlab/core/RentzSettlementTest.java) | Capturator, contribuții și fonduri recalculate independent, sosiri și resetare. |
| [Frontiere](src/test/java/ro/rentzlab/core/RentzBoundaryTest.java) | Distribuiri controlate și refuzuri fără mutație. |
| [Jucători](src/test/java/ro/rentzlab/core/RentzAiTest.java) | Reguli tactice, lumi fezabile și aceeași decizie pentru aceeași observație. |
| [Rafinarea Hard](src/test/java/ro/rentzlab/core/RentzHardRefinementTest.java) | Ținte demonstrate public, incertitudine păstrată, cost local și buget fără prefix parțial de alternative. |

Partida virtuală este integrare de domeniu, fără interfața originală. Componentele mici păstrează așteptări independente și refuzuri fără mutație; [strategia testării](../../docs/rentz/ATF_Components.md) explică interpretarea rezultatului.

[Învățarea offline](../../docs/rentz/ATF_Learning.md) deține evoluția politicii. Nivelul Hard rămâne o politică cu buget finit și continuare tactică; testele nu îl declară optim sau infailibil.
