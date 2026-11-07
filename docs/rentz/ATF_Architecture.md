# Arhitectura domeniului Rentz

## Limite

Motorul Java nu depinde de Android, de publicarea HTML sau de algoritmul aplicației originale. Testarea ATF folosește JDK 21; modelul este compilat pentru Java 17 și are și o intrare Gradle exclusiv JVM.

```mermaid
flowchart LR
    CONFIG[Profil de scor] -->|configurează| ENGINE[Stare și reguli]
    ENGINE -->|proiectează| OBS[Observația actorului]
    OBS -->|alimentează| POLICY[Jucător Normal sau Hard]
    POLICY -->|propune| ACTION[Acțiune]
    ACTION -->|validează și aplică| ENGINE
    ORACLE[Oracol independent] -->|confruntă| ENGINE
```

<details>
<summary>Legendă: actorul și oracolul au intrări distincte</summary>

Motorul deține distribuirea completă. Politica primește numai mâna actorului și faptele publice; oracolul poate folosi informație suplimentară fără a o transmite politicii.

</details>

| Componentă | Contract |
|---|---|
| `Card`, `Contract` | Pachet și vocabularul celor opt sub-jocuri. |
| `OpeningDeal` | Partiție imuabilă și completă a pachetului pentru o primă distribuire controlată. |
| `TrickRules` | Legalitate și câștigător fără atu. |
| `RentzBoard` | Deschidere și extindere contiguă pe culori. |
| `ScoreProfile` | Punctaj, cumul și protecție numerică. |
| `RentzMatch` | Faze, alegător, rotație, refuz, închidere și istoric. |
| `Observation` | Mână proprie, dimensiuni adverse, masă, evenimente publice și registru imuabil al mâinilor încheiate. |
| `MatchUtility`, `FullMatchRollout` | Separă obiectivele finale și continuarea ipotetică; [învățarea](ATF_Learning.md) definește portofoliul rămas și limitele candidatului offline. |
| `LabPolicy` | Reper determinist și explicabil, fără calificare expert. |
| `RentzPlayer`, `TacticalPolicy`, `SearchPolicy` | Port Normal/Hard, tactică publică și compararea alternativelor pe lumi comune. |
| `KnowledgeSampler`, `PolicySettings` | Distribuiri ipotetice fezabile și buget/ponderi distincte de regulile de scor. |
| `DecisionEvaluator`, `RolloutBudget` | Evaluare în pachetul `ai.search`, cu lot comun atomic și plafon de tranziții. Măsurarea lucrului nu este o limită de timp CPU. |
| `TargetTactics` | Dovada țintei și costul local în `ai.tactics`; politica proprie și modelul adversarilor au roluri separate. |
| `RuleProfiles` | Citire strictă a profilului YAML în infrastructura JVM. |
| `RuleCatalog` | Proveniență parțială și legături ale manifestului către API-ul și declarațiile de test curente. |
| `OfflineArena`, `EvaluationRegistry` | Jocuri complete CPU, comparare pereche, expunerea datelor și retenția externă. |
| `GameSession`, `SessionJson` | Comenzi ale adaptorului Android, jurnal privat reproductibil și observație publică serializată separat. |
| `SavedJson` | Cititor strict comun JVM/Android; document unic și numere exacte înaintea validării formei și rejucării comenzilor. |
| `MainActivity`, `RentzApplication` | Prezentare nativă și compoziția unui repository unic în proces; fără calcul AI sau scriere directă în activitate. |
| `SessionController`, `SessionRepository`, `FileSessionStorage` | Checkpoint, copie separată, proprietar revocabil și înlocuire atomică; [contractul de lifecycle](ATF_Lifecycle.md) deține intercalările. |

## Memoria publică

Pachetul `ro.rentzlab.core` deține modelul și regulile, iar `ro.rentzlab.ai` deține politicile și inferența. AI consumă observația motorului; motorul nu importă politica. [Inventarul Java](../../config/java-source-layout.json) și guvernanța permanentă protejează această direcție și rădăcinile Maven/Android.

| Informație | Contract |
|---|---|
| Mâna proprie | Poate rămâne ascunsă înaintea alegerii pe nevăzute; în joc este vizibilă actorului. |
| Observație publică | Dimensiunile adverse, angajamentul pe nevăzute, punctele mâinii, totalurile, ordinea sosirilor și evenimentele publice; exclude seed-ul și mâinile adverse. |
| Stabilitate | Listele produse de motor sunt copii imuabile; mutările ulterioare nu rescriu o observație deja primită. `completedHands` păstrează și vectorii de puncte/totaluri imuabili. |
| Eveniment | Actorul, acțiunea, cartea și dimensiunea tablei înaintea acțiunii. |
| Pas | Descrie frontiera curentă; alte mutări pot deschide continuări, deci nu dovedește imposibilitatea permanentă de a juca. |

### Admiterea scorurilor din registru

[PublicProgress](../../rentz-lab-app/game-core/src/main/java/ro/rentzlab/core/progress/PublicProgress.java) verifică programul, fondul, intervalele, cumulul și alocarea pe locuri înainte de construirea unei continuări. La alegerea pe nevăzute se aplică multiplicatorul și unităților, și premiilor.

| Contract | Condiție suplimentară pentru alocare |
|---|---|
| Levate, Dame, Carouri și Whist | Fiecare scor este multiplu al unității contractului. Un fond nul permite numai zero pe toate locurile. |
| Regele de cupă și Zece de treflă | Întreaga valoare nenulă revine unui singur loc. |
| Rentz | Valorile pe locuri formează o permutare a premiilor primelor poziții din profil, câte una pentru fiecare jucător. Egalitățile și zerourile își păstrează multiplicitatea. |
| Totale | Trebuie să existe o alocare a celor opt levate și a cărților cu penalizare care produce vectorul. Dama de caro contribuie la ambele categorii la același loc, iar o levată are exact câte o carte de la fiecare jucător. |

[Alocarea pentru Totale](../../rentz-lab-app/game-core/src/main/java/ro/rentzlab/core/progress/TotalsAllocation.java) caută pe numerele de capturi rămase și memorează stările refuzate. Limita pe loc depinde de dimensiunea pachetului, cu cel mult 1.728 de combinații de numărători la șase jucători.

Un registru admis certifică o alocare aritmetică compatibilă cu scorul și programul. Legalitatea ordinii de joc, a câștigătorilor și a distribuirii efective se stabilește prin auditul tranzițiilor pe cărți.

## Configurație și trasabilitate

| Intrare | Consumator | Contract |
|---|---|---|
| [Punctaj](../../config/rentz-score-profiles.yaml) | `RuleProfiles` → `ScoreProfile` | Configurație executabilă, tipizată, cu identitate de laborator și probă de paritate cu fabrica Java. |
| [Manifest](../../config/rentz-rules.yaml) | `RuleCatalog.claims` și auditul legăturilor | Statut, proveniență și referințe către implementare și componente; nu interpretează strategie. |
| [Observații](../../config/rentz-reference-observations.yaml) | `RuleCatalog.evidence` | Informație numerică parțială despre sursă, cu necunoscute păstrate; nu produce profil executabil. |
| [Strategie Hard](../../config/rentz-hard-policy.yaml) și [profil contextual](../../config/rentz-contextual-policy.yaml) | `PolicyInputs` → `PolicySettings` | Obiectiv, tactică, număr de lumi și plafon de tranziții tipizate; dovada publică este separată de euristicile experimentale. |

Toate intrările refuză chei duplicate, câmpuri necunoscute și documente YAML suplimentare. Schimbarea unei reguli cere reconcilierea codului, componentelor și statutului; auditul legăturilor nu demonstrează semantic adevărul regulii sau execuția testului indicat.

## Distribuiri controlate

```mermaid
flowchart LR
    FIXTURE[Distribuire explicită] -->|validează partiția| DEAL[OpeningDeal]
    DEAL -->|înlocuiește numai prima distribuire| ENGINE[Motor Rentz]
    ENGINE -->|proiectează mâna proprie| ACTOR[Observație permisă]
    SEED[Seed de continuare] -->|distribuie mâinile următoare| ENGINE
```

<details>
<summary>Legendă: fixture-ul aparține constructorului și oracolului</summary>

Fiecare loc are opt cărți, iar reuniunea lor este exact pachetul mesei, fără duplicate sau cărți lipsă. Copiile sunt imuabile; actorul nu primește obiectul distribuirii și nu vede cărți prin alegerea pe nevăzute.

</details>

`fromOpeningDeal` permite cazuri mici și reproductibile fără editarea stării interne, rezultate injectate sau interfață Android. Seed-ul de continuare afectează numai distribuirile ulterioare; cunoașterea fixture-ului de către oracol nu se transmite politicii și nu califică forța ei de joc.

## Adaptorul Android

Compoziția, geometria și auditul sunt limite separate ale adaptorului nativ. Alegerile sub-jocurilor, tabla, scorul și observațiile au semantica Rentz; atuul, licitațiile și seriile Whist nu devin reguli reutilizate.

Compatibilitatea Android se califică pe API-ul și runtime-ul efectiv. Testele JVM nu confirmă disponibilitatea metodelor Java pe dispozitiv sau randarea WebView ori nativă; [protocolul evoluției](ATF_Learning.md) separă politicile, arena și admiterea E2E.

| Limită de sesiune | Contract |
|---|---|
| Restaurare | Repetă comenzile acceptate, cu aceeași identitate de profil și politică. |
| Proiecție debug | Exclude seed-ul și mâinile adverse; [protocolul Android](ATF_Android.md) separă intrările jucătorului și oracolului. |
