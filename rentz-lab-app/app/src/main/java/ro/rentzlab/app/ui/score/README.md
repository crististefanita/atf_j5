# Istoricul scorului Rentz

## Pe scurt

Grila prezintă câte un rând pentru fiecare mână confirmată. Coloana din stânga identifică mâna, sub-jocul și alegătorul. Fiecare jucător are două valori, **Δ** pentru punctele mâinii și **Σ** pentru totalul confirmat.

| Responsabilitate | Sursă |
|---|---|
| Proiecția datelor | [RentzScoreContent](RentzScoreContent.java) adaptează [ScoreSettlement](../../../../../../../../../game-core/src/main/java/ro/rentzlab/core/score/ScoreSettlement.java) și [ScoreStandings](../../../../../../../../../game-core/src/main/java/ro/rentzlab/core/score/ScoreStandings.java). Aceeași clasă este compilată pentru WEB: ordinea locurilor, Δ:/Σ:, contribuțiile și clasamentul au o singură proiecție. |
| Limbaj și roluri | [RentzText](../copy/RentzText.java) este portabil; [RentzScoreLayout](RentzScoreLayout.java) selectează ponderile din `ScoreGridStyle` și separatorul comun adaptoarelor. AAR-ul deține widgeturile Android; browserul consumă aceleași modele portabile. |
| Calculul dimensiunilor | [HistoryGridMetrics](HistoryGridMetrics.java) este o fațadă de compatibilitate peste `ro.cardtable.ui.score.HistoryGridMetrics`; nu mai duplică algoritmul. |
| Randarea și gesturile | [RentzHistoryGrid](RentzHistoryGrid.java) păstrează ID-urile aplicației și callback-ul mâinii; `ScoreHistoryView` din AAR măsoară textul, aliniază celulele și sincronizează derularea. |
| Dialogul | [RentzHistoryView](../screens/RentzHistoryView.java) rezolvă numele afișate, mesajul pentru istoricul gol și dimensiunea ferestrei. |
| Detaliul mâinii | [RentzHandDetails](RentzHandDetails.java) afișează profilul, alegătorul, multiplicatorul din titlu, contribuțiile și scorurile confirmate. |
| Rezultatul compact | [RentzSettlementTable](RentzSettlementTable.java) transmite conținutul confirmat către `ScoreTableView` din AAR: un rând per loc, coloane Jucător/Δ:/Σ: și text normal. Detaliile se deschid separat. |
| Ajutorul din meniu | [RentzAwardGrid](RentzAwardGrid.java) afișează premiile profilului efectiv, în ordinea sosirii, pentru mesele de 3–6 jucători. |

## Cum se păstrează contextul

| | Coloana mâinii | Coloanele jucătorilor |
|---|---|---|
| Antet | „Mână · joc” | Nume, Δ și Σ |
| Rânduri | Identitatea mâinii și alegătorul | Delta și totalul pentru fiecare loc |

- Derularea orizontală mută împreună antetul jucătorilor și valorile lor.
- Derularea verticală folosește un singur viewport pentru ambele jumătăți ale fiecărui rând.
- Antetul și legenda rămân vizibile vertical dacă încape cel puțin primul rând complet. Într-o fereastră mai scundă, conținutul folosește derularea integrală.

## Decizii de păstrat la următoarea schimbare

- Măsoară numele, etichetele și numerele cu stilul care le va desena. Coloana mâinii se poate împărți pe mai multe rânduri, iar numerele își păstrează lățimea necesară, inclusiv semnul minus.
- Aplică aceleași dimensiuni antetului și celulelor. Înălțimea unui rând este maximul dintre descrierea mâinii și valorile jucătorilor, astfel încât despărțirea în două coloane de layout să păstreze alinierea.
- Tratează locul jucătorului ca identitate. Editarea numelui schimbă eticheta afișată, iar poziția, delta și totalul vin din aceeași înregistrare confirmată.
- Confruntă lățimea completă a grupului de la margine. Prezența textului în ierarhia Android poate coexista cu o celulă vizibilă doar parțial.
- Folosește un jurnal legal și determinist pentru testarea unei tabele lungi. Componentele verifică proiecția și restaurarea, iar Android verifică gesturile, măsurarea și accesul la capetele tabelului.
- La redimensionare, verifică separat cine derulează vertical. Fereastra înaltă folosește viewportul rândurilor, iar fereastra scundă folosește containerul exterior pentru a păstra accesul la rânduri și legendă.
- Măsoară limitele gesturilor în coordonatele logice din ierarhia Android. Pe emulatorul API 28, PNG-ul poate păstra dimensiunea fizică chiar după ce WindowManager a aplicat o dimensiune logică mai mică.

## Contribuții și locuri

- Atinge eticheta subliniată a unei mâini pentru a deschide detaliul. Întoarcerea păstrează aceeași grilă și poziția de derulare.
- La Totale, Q♦ contribuie atât la dame, cât și la carouri. Numărul contribuțiilor rămâne brut, iar punctele afișate includ deja dublarea.
- La Rentz, locurile urmează ordinea înregistrată de motor. Ultimul loc îi revine jucătorului cu cărți rămase și primește singur zero în profilul curent.
- Jurnalele cu profil istoric își păstrează premiile, inclusiv când mai multe locuri au primit zero. Detaliul folosește profilul sesiunii restaurate.
- În ajutor, un loc existent cu premiu zero afișează `0`, iar un loc care nu există la acea masă are marcajul `—`. Prețurile levatelor din același ecran vin din același profil.
- Reluarea reexecută jurnalul canonic și reconstruiește rezultatele publice. Un sumar sintetic fără detalii arată explicit lipsa lor.

Stilul neutru vine din biblioteca Whist; punctajele și vocabularul rămân în adaptorul Rentz. Rendererul WEB primește tabelul canonic, fără să reconstruiască punctele din alte celule. [Istoricul adaptiv în ATF](../../../../../../../../../../docs/rentz/ATF_UiIntegration.md#istoricul-adaptiv-rentz)
