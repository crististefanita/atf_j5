# Motorul de reguli Rentz

## Pe scurt

Model Java fără dependențe Android sau ATF în producție. Deține distribuirea, cele opt sub-jocuri, secvențele, rotația, refuzul și scorul unui profil explicit.

## Contracte

| API | Responsabilitate |
|---|---|
| `Card.deck` | Pachet distinct cu opt cărți pentru fiecare loc. |
| `OpeningDeal`, `RentzMatch.fromOpeningDeal` | Primă distribuire controlată, validată și imuabilă; continuarea folosește seed separat. |
| `TrickRules` | Urmarea culorii și câștigătorul fără atu. |
| `RentzBoard` | Extinderea contiguă pe culori. |
| `ScoreProfile` | Ponderi de punctaj, cumul și interval numeric sigur. |
| `RentzMatch` | Faze, alegeri, acțiuni, evenimente publice și istoric. |
| `LabPolicy` | Reper simplu care primește numai observația actorului. |
| `RentzPlayer` | Port stabil cu niveluri Normal și Hard, pe observația actorului. |
| `TacticalPolicy` | Ținte negative, conservarea puterii și continuări Rentz. |
| `KnowledgeSampler` | Cărți necunoscute, culori absente, pasuri și distribuiri fezabile. |
| `SearchPolicy`, `PolicySettings` | Lumi comune, continuarea mâinii și buget/ponderi explicite. |

`laboratory()` este profil provizoriu, separat de preset-urile originale neconfirmate. [Regulile](../../docs/rentz/ATF_Rules.md) dețin semantica și limitele, iar [manifestul](../../config/rentz-rules.yaml) indică statutul fiecărei afirmații.

## Verificare

Dintr-o copie temporară completă a proiectului, cu Maven și JDK 21:

```powershell
mvn -B -o -pl rentz-lab-app/game-core -am test
```

| Familie | Verificare |
|---|---|
| [Reguli](src/test/java/ro/rentzlab/core/RentzRulesTest.java) | Pachet, levate, alegere, blind și partide virtuale. |
| [Secvențe](src/test/java/ro/rentzlab/core/RentzSequenceTest.java) | Frontiere ale turei, închidere și memorie publică. |
| [Scor](src/test/java/ro/rentzlab/core/RentzSettlementTest.java) | Capturator, contribuții și fonduri recalculate independent, sosiri și resetare. |
| [Frontiere](src/test/java/ro/rentzlab/core/RentzBoundaryTest.java) | Distribuiri controlate și refuzuri fără mutație. |
| [Jucători](src/test/java/ro/rentzlab/core/RentzAiTest.java) | Reguli tactice, lumi fezabile și aceeași decizie pentru aceeași observație. |

Partida virtuală este integrare de domeniu, fără interfața originală. Componentele mici păstrează așteptări independente și refuzuri fără mutație; [strategia testării](../../docs/rentz/ATF_Components.md) explică interpretarea rezultatului.

[Învățarea offline](../../docs/rentz/ATF_Learning.md) deține evoluția politicii. Nivelul Hard rămâne o politică cu buget finit și continuare tactică; testele nu îl declară optim sau infailibil.
