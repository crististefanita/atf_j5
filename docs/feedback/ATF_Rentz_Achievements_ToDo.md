# Realizări Rentz pentru profil

## Pe scurt

**Realizări** prezintă cele 18 obiective de joc din catalogul executabil, câte șase pe nivel. **Antrenament** păstrează cele 800 de obiective și progresul lor salvat. Acest registru urmărește extinderea, importul și probele native rămase. [Integrarea ecranelor și testele](../rentz/ATF_UiIntegration.md#realizări-și-profil-local)

## Cercetare și decizii

| Reper | Aplicare în Rentz |
|---|---|
| Progres incremental, vizibilitate implicită și dificultăți variate | Afișați pragul și progresul tuturor obiectivelor normale. Rezervați cele ascunse pentru surprize rare; folosiți totaluri persistente între sesiuni. [Ghidul Google pentru realizări](https://developer.android.com/games/pgs/quality) |
| Identificatori stabili și indicatori de progres | Păstrați identificatorul independent de traducere și folosiți un contor sau o colecție de dovezi care poate fi reconstituită. [Documentația Steamworks](https://partner.steamgames.com/doc/features/achievements) |
| Obiective zilnice într-un joc de cărți | Folosiți provocări opționale, complementare progresului permanent. Zilele active pot fi separate și realizările deja acordate nu expiră. [Microsoft Solitaire Collection](https://www.microsoft.com/en-us/edge/features/games-menu) |
| Catalog Android propriu | Păstrați cele 800 de obiective în Antrenament. Un eventual subset Play Games Services are propriul buget. Google documentează maximum 400 de realizări pe durata de viață a jocului și 2.000 de puncte în total. [Puncte și realizări Android](https://developer.android.com/games/pgs/achievements) |
| Contracte Rentz | Folosiți profilul de reguli activ pentru măsurare. Jocurile de evitare, captură și secvență au obiective diferite. [Regulile modelului](../rentz/ATF_Rules.md) · [Catalogul jocurilor românești](https://www.pagat.com/national/romania.html) |

## Extinderea catalogului

Fiecare obiectiv nou cere un ID stabil, texte RO/EN, prag, dovadă și profil de reguli compatibil. [Catalogul implementat și testele](../rentz/ATF_UiIntegration.md#realizări-și-profil-local)

### Ordinea de învățare

| Parcurs | Ce apare întâi | Următoarea treaptă |
|---|---|---|
| 1. Învață masa | Prima alegere, prima mutare legală, prima mână confirmată și citirea rezultatului. | Levata simplă, apoi capturile care schimbă punctajul. |
| 2. Înțelege contractele | Levate → Dame → Carouri → Regele de cupă → Totale. Whist inversează obiectivul levatelor, iar Zece de treflă concentrează captura pe o singură carte. | Rentz are un fir propriu: deschide culoarea → extinde capetele → gestionează pasul și efectele rangurilor → termină pe loc. |
| 3. Alege tactica | Primul rezultat favorabil, repetarea lui, apoi alegerea personală sau adaptarea la masa și contractul unui adversar. | Alegerea pe nevăzute, combinațiile și dificultatea Greu apar după tactica de bază echivalentă. |
| 4. Stăpânește jocul | Serii pe zile și distribuiri distincte, colecții între contracte și rezultate în partide complete. | Reperele de clasament și revenire apar după primele repere de progres din partidă. |

- Fiecare serie are o cheie stabilă de ordonare. Ordinea recomandată este parcurs → familie → tactică → prag. Verificați că fiecare prag următor cere o dovadă mai puternică sau un context nou.
- La început afișați puține obiective recomandate și grupați variantele de masă sub aceeași serie. Catalogul complet rămâne accesibil prin filtre, cu explicațiile obiectivelor viitoare vizibile.
- Un prag avansat poate primi credit când dovada lui există. Traseul de învățare organizează recomandările și evită cascadele de notificări; nu cere repetarea unei performanțe deja demonstrate.
- Exemplu pentru Rentz: **Prima culoare** → **Ambele capete** → **Ține tura după as** → **Podium la două mese** → **Podium în zile diferite**. Fiecare pas adaugă o regulă sau un context, fără să schimbe arbitrar condiția de reușită.

### Cele opt contracte

Fiecare rând conține cinci fapte candidate, A–E. „Prima” și „ultima levată” se referă la începutul și finalul efectiv ale contractului curent. Faptele de evitare măsoară cartea capturată, iar cele tactice disting explicit cartea jucată de rezultatul levatei.

| Contract | A | B | C | D | E |
|---|---|---|---|---|---|
| Levate | Pierde prima levată | Pierde ultima levată | Încheie cu cel mult două capturi | Încheie cu cel mult o captură | Încheie fără capturi |
| Dame | O damă ajunge la alt loc în levata în care joci | Aruncă o damă în afara culorii către un capturator advers | Evită capturarea damelor în prima jumătate | Încheie cu cel mult o damă | Încheie fără dame |
| Carouri | Pierde prima levată care conține un caro | Aruncă un caro către alt capturator | Evită capturile de caro în primele patru levate | Încheie cu cel mult două carouri | Încheie fără carouri |
| Regele de cupă | Pierde levata decisivă cu K♥ | Urmează culoarea în levata decisivă, iar alt loc ia K♥ | Aruncă K♥ legal în afara culorii | Alt loc ia K♥ după ce jucătorul deschide levata decisivă | Alt loc ia K♥ după ce jucătorul a capturat cel puțin o levată anterioară |
| Totale | Pierde prima levată | Încheie fără K♥ | Încheie fără dame | Încheie fără carouri | Încheie fără contribuții negative din nicio categorie |
| Whist | Câștigă prima levată | Câștigă ultima levată | Câștigă cel puțin două levate consecutive | Încheie cu cel puțin trei capturi | Încheie cu cel puțin patru capturi |
| Zece de treflă | Capturează 10♣ | Capturează 10♣ jucat de un adversar | Capturează propriul 10♣ | Capturează 10♣ într-o levată pe care o deschide | Capturează 10♣ după cel puțin o levată anterioară pierdută |
| Rentz | Deschide o culoare la rangul mesei | Extinde capătul inferior | Extinde capătul superior | Joacă un as și păstrează tura după confirmare | Termină printre primii doi |

| Nivel per contract | Extindere fixă | Filtru de admitere |
|---|---|---|
| 50 ușoare | 5 fapte × 5 variante de masă × 2 praguri | Masa liberă sau una dintre mesele cu 3/4/5/6 locuri. Pragurile cer una, apoi două mâini distincte eligibile. |
| 20 medii | 5 fapte × 4 contexte | Trei confirmări ca alegător, patru când a ales un adversar, patru la mese de cel puțin cinci sau trei în modul Greu. |
| 10 grele | 5 fapte × 2 parcursuri | Același fapt pe trei zile active, la minimum două mărimi de masă, respectiv pe patru zile active în modul Greu. Două mâini eligibile în fiecare zi, în partide diferite. |

- **Ușor** include o mână, două mâini distincte și o mărime de masă. Dacă există deja obiectivul de o mână, verificați dacă alegerea personală a contractului adaugă o dovadă distinctă.
- **Mediu** include alegătorul, masa, dificultatea și alegerea pe nevăzute.
- **Greu** cere câte o dovadă pe zi din partide distincte.

Matricea completă de mai sus rămâne reper pentru extindere. Păstrați variantele rare numai dacă au suficiente ocazii în joc. Înlocuiți o variantă respinsă cu o tactică din același contract și nivel.

### Familii transversale

Fiecare familie are patru axe distincte. Pentru fiecare axă se propun cinci probe ușoare, două medii și una grea; exemplele fixează sensul fiecărei axe înainte de calibrare.

| Familie | Patru axe | Exemplu mediu | Exemplu greu |
|---|---|---|---|
| Alegere și risc | Alegător uman, ofertă rămasă, alegere deschisă, alegere pe nevăzute | Încheie patru contracte diferite alese personal în sesiuni eligibile | Pe patru zile active, confirmă alegeri pe nevăzute cu obiectivul atins în trei contracte diferite |
| Ritm și progres | Primul prag, varietatea zilei, varietatea mesei, revenirea la joc | Confirmă într-o zi șase mâini din minimum trei contracte la două dimensiuni | În cinci zile active, confirmă un contract diferit în fiecare și acoperă în total toate cele opt |
| Partidă și clasament | Program complet, loc, egalitate, recuperare după deficit | Încheie o partidă de trei locuri; rezultatul de clasament se acordă după ultima mână | Încheie trei partide Greu pe trei zile active, din cel puțin două mărimi de masă, cu cel puțin o revenire pe podium după jumătate |
| Tehnică | Urmarea culorii, aruncare legală, secvențe, rezultat cu risc | Confirmă în trei contracte ocazii de aruncare care ajută obiectivul | În patru zile active, câștigă progres pe captură, evitare și secvență, cu criterii de rezultat distincte |
| Colecții | Contracte, locuri, combinații și profiluri de joc | Adună șase contracte distincte cu cel puțin un rezultat favorabil | Completează colecția de contracte la două mărimi de masă, cu cel puțin trei zile active și fără repetarea aceleiași distribuiri |

Probele ușoare transversale folosesc repere de 1–3 acțiuni, sub-jocuri sau combinații noi. Dacă axa cere o partidă completă, reperele ușoare marchează primii pași măsurabili, iar terminarea partidei intră la nivel mediu sau greu.

## Profil, proveniență și progres

| ID | Prioritate | Criteriu de acceptanță |
|---|---|---|
| ACH-01 | P1 | Identificator local aleator per profil. Schimbarea numelui păstrează progresul, schimbarea profilului îl separă. La reconectare, o identitate remote explicit asociată poate restaura progresul fără a amesteca două profiluri. |
| ACH-02 | P1 | Acordarea consumă numai comenzi confirmate și rezultate închise ale motorului. Cheia include profilul, partida, mâna, revizia și versiunea metricii. Restartul, replay-ul sau repetarea importului nu dublează progresul. |
| ACH-03 | P1 | Comenzile umane, delegarea AI și pasul automat au proveniență distinctă. O mână cu delegare nu califică reperele tactice manuale; progresul general etichetat „cu asistență” rămâne separat. Lipsa provenienței din jurnalele vechi nu dovedește joc manual. |
| ACH-04 | P1 | Partida completă este strict `MATCH_END`. Pentru Rentz, clasamentul sosirilor folosește `finishOrder`, inclusiv ultimul loc atribuit, iar Asul și cartea minimă se califică din tranziția confirmată. La K♥ și 10♣ se acceptă închiderea timpurie. Egalitățile au loc comun și indicator explicit. |
| ACH-05 | P1 | Zilele se calculează după un fus de profil explicit, cu evenimente persistente UTC și fus salvat pe eveniment. Schimbarea orei, importul aceleiași distribuiri și două dispozitive care retrimit aceeași dovadă nu pot fabrica zile sau mâini distincte. |
| ACH-06 | P2 | Catalogul și progresul au versiuni separate. Pragurile schimbate se migrează explicit, insignele acordate sunt monotone, iar datele importate păstrează dovada și nivelul de încredere. |

ACH-02–ACH-04 au baza locală în producție. Rămân verificarea aceleiași identități după import, sincronizarea între dispozitive și migrarea pragurilor. [Contractul de integrare](../rentz/ATF_UiIntegration.md#realizări-și-profil-local)

## Meniu și accesibilitate

Meniul, filtrarea și detaliul de eligibilitate sunt descrise în [integrarea realizărilor](../rentz/ATF_UiIntegration.md#realizări-și-profil-local).

- Adăugați ruta inversă din profil și, dacă se introduce importul, extindeți verificările de restabilire a stării.
- Lista virtualizată folosește identități stabile. Calificați cele 800 de intrări cu text mărit și navigare TalkBack. Verificați încărcarea după migrarea profilului când apare importul.
- Extindeți acceptanța Android a panoului la ultima mână a partidei. Traseul curent verifică o deblocare simultană cu progres parțial, închiderea manuală și reluarea fără repetarea panoului.

## Verificări pentru extindere

- **Componente:** perechi pozitiv/negativ pentru fiecare tip de metrică, deduplicare, offline, restabilire profil, două dispozitive, ceas mutat, egalități și respingerea autodelegării pentru realizări manuale.
- **Calibrare:** pe 3–6 locuri și ambele dificultăți, măsurați oportunitățile și proporția de reușită. Combinațiile trebuie să aibă un traseu demonstrabil, fără să ceară pierderi deliberate sau o distribuire aproape unică.
- **E2E selectiv:** joc real, deschidere realizare, revenire după restart și import repetat. Extent arată regula, acțiunile eligibile, progresul înainte/după și capturile native.
