# Istoricul scorului Rentz

## Pe scurt

Grila prezintă câte un rând pentru fiecare mână confirmată. Coloana din stânga identifică mâna, sub-jocul și alegătorul. Fiecare jucător are două valori, **Δ** pentru punctele mâinii și **Σ** pentru totalul confirmat.

| Responsabilitate | Sursă |
|---|---|
| Proiecția datelor | [RentzScoreContent](RentzScoreContent.java) păstrează ordinea locurilor și copiază delta, totalul și marcajul ×2 din rezultatele motorului. |
| Calculul dimensiunilor | [HistoryGridMetrics](HistoryGridMetrics.java) primește lățimi măsurate în pixeli. Rezervă loc pentru scoruri și stabilește când derulează toate elementele din zona de conținut. |
| Randarea și gesturile | [RentzHistoryGrid](RentzHistoryGrid.java) măsoară cu fontul Android activ, aliniază celulele și sincronizează pozițiile de derulare. |
| Dialogul | [RentzHistoryView](../screens/RentzHistoryView.java) rezolvă numele afișate, mesajul pentru istoricul gol și dimensiunea ferestrei. |
| Detaliul mâinii | [RentzHandDetails](RentzHandDetails.java) afișează profilul, alegătorul, multiplicatorul din titlu, contribuțiile și scorurile confirmate. |

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
- La Rentz, locurile urmează ordinea înregistrată de motor. Ultimul loc îi revine jucătorului cu cărți rămase. Mai multe locuri pot avea același premiu.
- Reluarea reexecută jurnalul canonic și reconstruiește rezultatele publice. Un sumar sintetic fără detalii arată explicit lipsa lor.

Modelul de măsurare și stilul de grilă urmează reperul Whist J4. Contractul de scor și pașii pentru build, fixture și raport sunt documentați în ghidul de integrare. [Istoricul adaptiv în ATF](../../../../../../../../../../docs/rentz/ATF_UiIntegration.md#istoricul-adaptiv-rentz)
