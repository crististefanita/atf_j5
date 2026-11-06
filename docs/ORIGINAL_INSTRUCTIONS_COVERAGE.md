# Instructions din aplicația originală — contract și acoperire

Recitite live la 2 noiembrie 2026, din meniul pachetului `com.qsoft.android.whist`, versiunea instalată 4.5/145.

Testul `OriginalInstructionReviewE2eTest` a deschis meniul, Instructions, a parcurs toate paginile până la stabilizarea ultimului viewport de două ori și a revenit la meniu fără joc nou sau schimbarea setărilor. Citirea completă a trecut în 122,272 s. Prima citire de 42,875 s cuprindea numai începutul paginii; nu este dovadă pentru secțiunile de mai jos.

Dovezi:
`<evidence-root>/ai-training\20261102-143525\original-instructions-final\`.
Text: `original-instructions-full.txt`; capturi reale și XML omonime.

`OriginalInstructionsContractTest` păstrează 20 de cazuri semantice independente de emulator.

Nu confundăm o propoziție despre un control UI cu o regulă de motor.

| ID | Regulă/caz verificat în componente |
|---|---|
| OI01 | 2–5 adversari înseamnă 3–6 locuri; pachetul profilului Oh Hell are 52 de cărți distincte. |
| OI02 | Domeniul complet 0…mărimea mâinii, inclusiv ofertele mari care necesită paginare în UI. |
| OI03 | Numai ultimul ofertant are interdicția de a egala suma cu mărimea mâinii; ceilalți pot produce temporar egalitatea. |
| OI04 | Contract exact, nu minim: depășirea ofertei nu primește bonus. |
| OI05 | Contract zero exact și diferența față de zero inexact. |
| OI06 | Toate cele 324 de perechi ofertă/levate 0…17 folosesc levate + 10 numai la exactitate. |
| OI07 | Primul ofertant și prima conducere sunt în stânga dealerului; toate locurile dealerului la 3–6 jucători. |
| OI08 | Conducătorul poate juca orice carte ținută. |
| OI09 | Urmarea culorii are prioritate chiar când câștigul levatei nu mai este posibil. |
| OI10 | În Oh Hell, când nu ai culoarea poți decarta chiar dacă ai atu; profilul românesc clasic cere atu. |
| OI11 | Cea mai mare carte din culoarea condusă câștigă; asul din altă culoare nu câștigă fără atu. |
| OI12 | Atuul bate culoarea condusă; cel mai mare atu bate un atu mai mic. |
| OI13 | În NT nu există atu implicit pe pică. |
| OI14 | Ciclul pică/inimă/treflă/caro/NT se repetă corect în profilul Descending. Vezi ambiguitatea de mai jos. |
| OI15 | Câștigătorul levatei conduce următoarea. |
| OI16 | Scorul se aplică după ultima carte a tuturor jucătorilor, nu înainte. |
| OI17 | Următoarea mână păstrează totalurile și golește ofertele/levatele mâinii precedente. |
| OI18 | Nu se joacă înainte de finalul licitării și nu se reaplică scorul după încheiere. |
| OI19 | Levata curentă, ultima levată și istoricul păstrează ordinea reală, în colecții imutabile. |
| OI20 | Balanța ofertelor este suma minus mărimea mâinii: negativă, zero sau pozitivă. |
| OI21–22 | Descending pornește la 52/N (inclusiv 17 pentru trei locuri); Down & Up are un minim. |
| OI24–25 | Blocurile Romanian și Long Romanian se repetă de N ori la capete și la întoarcere; Long este 8–1–8. |
| OI26–27 | Oh Hell 10–1–10 sau 8–1–8 la șase locuri; Reverse începe și termină cu o carte. |
| OI28–33 | Toate variantele încap în pachet, profilurile Romanian împart regulile de atu/scor, cartea întoarsă este neîmpărțită, iar numărul invalid de locuri este respins. |
| RS01–24 | Serii exacte/ratate, bonus 5/10, penalizare −5, resetare, excepția unei cărți, persistență, aplicare o singură dată, checkpoint-uri corupte, utilitatea Expert, licitare cu rezultat forțat și distribuții ipotetice stabile la schimbarea recompensei. |

Fiecare rând este legat de metoda `oiXX...` din clasa de test.

Domeniile enumerate dintr-o metodă nu sunt numărate ca sute de cazuri JUnit suplimentare. `Bid Other` și aranjarea coloanelor de scor sunt responsabilități de adapter/UI; testele de componente verifică domeniul și valorile, fără să pretindă OCR sau acoperirea aspectului vizual. Capturile și auditul numeric original al celor 19 mâini rămân dovezi separate pentru interfața originală.

## Ambiguități și separarea profilurilor

Pagina Instructions este generică și afirmă un ciclu de cinci stări de atu.

Partida originală Oh Hell de 19 mâini a demonstrat însă atu determinat de o carte neîmpărțită, nu un ciclu fix. Păstrăm ambele observații și tratăm ciclul ca regulă a profilului Descending; nu schimbăm Oh Hell pentru a potrivi o generalizare a textului. Aceasta este o limită de documentație a originalului.

Secțiunile Romanian și Oh Hell de mai jos precizează explicit diferențele
față de regulile generice. Romanian folosește pachet 8×N, urmarea culorii înaintea
atuului obligatoriu, NT la opt cărți și scor exact 5+levate, altfel minus
diferența. Long Romanian inversează dimensiunile, păstrând aceleași reguli.

Regula de serie a originalului acordă +5 după cinci contracte exacte eligibile sau −5 după cinci ratări.

- Mâinile de o carte nu avansează seria, dar un rezultat opus o întrerupe.
- După plată seria se resetează, deci există alt bonus la a zecea mână eligibilă consecutivă.
- Această precizare a fost verificată și prin inspecție read-only a propriului APK instalat, pentru că textul nu precizează ce se întâmplă cu o serie întreruptă de o carte.
- Nu am copiat implementarea originală.
- Faptele și proveniența instrumentului sunt păstrate în `original-rule-analysis/` din rularea de mai sus.

La cererea utilizatorului, Whist Lab oferă în setup valoarea **5 sau 10** pentru bonusul pozitiv de serie, implicit **10**.

- Valoarea 5 reproduce originalul; penalizarea rămâne −5 și bonusul fiecărui contract exact rămâne 5.
- Profilul clasic fără serii se selectează prin debifare.
- Salvările vechi rămân fără serii; nu li se inventează retrospectiv contoare sau recompense.
- AI-ul folosește opțiunea și contoarele publice în utilitate.
- Noul profil nu schimbă retrospectiv rezultatele benchmark-ului clasic înghețat.

Pentru probabilitatea unei mâini de o carte, alegerea ofertei maximizează formula profilului activ.

În consecință pragul utilității poate diferi între Oh Hell și profilul românesc; nu se folosește rotunjirea simplă a levatelor ca regulă universală de licitare.

## Acoperire și limite declarate

`OriginalVariantModelTest` parcurge 48 de partide complete: toate cele opt moduri
locale la 3–6 locuri, plus ambele valori de serie la Romanian/Long Romanian.
Modelul calculează separat distribuirea validă, actorii, legalitatea, câștigul,
scorul, seriile și reluarea. `RomanianStreakContractTest` enumeră 5.112 combinații
de mărime, ofertă, levate, contor anterior și valoare selectată într-un singur
caz JUnit; nu le raportăm ca 5.112 teste distincte.

Device Controls se verifică și la integrare Android: Back/Resume și închiderea
procesului/Resume trebuie să păstreze exact checkpoint-ul. Așezarea cărților,
culoarea cercului dealerului și accesarea levatei prin touch sunt cerințe UI,
nu dovezi furnizate de testele de componente.

Secțiunile High Scores, Multiplayer și Multiplayer Ratings au fost citite integral.

- Rating minim 1000, praguri 10000, 28/8 tabele, rating după abandon și restaurare, lobby/join/creator, trei timeout-uri, chat, rating numai față de oameni și Win Avg după 1000 mâini sunt **cerințe inventariate, neimplementate** în jocul nostru local.
- Nu pretindem acoperire de componente pentru servicii pe care nu le avem.
- Normal play offline și păstrarea pe dispozitiv sunt scope-ul curent.
- Raportul separă rezultatele efectiv executate de această listă de lipsuri.
