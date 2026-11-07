# Original Romanian Hard — feedback de integrare, 4 noiembrie 2026

Prima încercare live a confirmat setup-ul original **Romanian / 4 / Hard**, mode4 în salvarea v145.

A păstrat un backup valid al jocului anterior, a pornit partida cerută și a auditat distribuția, licitațiile și primele cărți publice. Partida nu este încă finalizată. Niciun rezultat de victorie nu este declarat.

**P1 — defect al adaptorului Whist Lab, remediat:** după transmiterea primei cărți, construcția `AtfUiActionRecord(0, ...)` a aruncat `sequence must be positive`.

- Compilarea fluxului nu putea certifica această cale.
- Folosim acum `AndroidUiDriver.recordAction`, mecanismul comun care atribuie secvențe valide.
- Regresia execută traseul real driver → trace → checkpoint fără dispozitiv și verifică continuarea numerotării după import.
- Nu este defect al JAR-ului modulul de biblioteci comune.

**P1 — acceptarea inputului trebuie observată, remediat în recuperare:** o intenție JSON sau un ADB tap transmis nu dovedește cartea acceptată.

- Reluarea reconstruiește auditorul din aceeași distribuție, verifică prefixul public și cere zero ori exact o nouă carte proprie.
- Dacă există una, identitatea trebuie să coincidă cu intenția salvată; nu se repetă tap-ul.
- O altă carte, o altă mână sau mai multe cărți proprii opresc execuția.
- Snapshot-ul se ia înainte de orice force-stop care ar putea pierde starea încă nesalvată.

**P1 — izolare între controlere, deschis:** în timpul primei încercări, jurnalul Android a arătat porniri `ro.whistlab.app` și schimbări de rezoluție.

- După oprirea încercării, fluxul `Whist Lab Whist Lab display acceptance` a revendicat rezervarea.
- Încercarea de reluare a respectat-o și nu a trimis comenzi Android.
- Fiecare punct Maven/IDE/script care comandă dispozitivul trebuie să revendice și să respecte aceeași rezervare.
- Confirmarea verbală că emulatorul este liber nu înlocuiește verificarea proprietarului rezervării.
- Rezervarea unui alt flux nu este suprascrisă automat.

**P2 — valori prea lungi pentru aserțiuni, remediat la intrare:** afișarea întregului prefix de32 de cărți ar depăși limita240.

- Receipt-ul compară în continuare listele integral, iar câmpurile expected/actual folosesc numărul de intrări și SHA-256.
- Auditorul păstrează separat comparațiile fiecărei cărți.
- Regresia schimbă pe rând toate cele28 de intrări anterioare și cere respingere.
- Nu am trunchiat valori și nu am schimbat rendererul sau JAR-ul.

**P2 — publicare cu toate imaginile, de verificat la final:** aproximativ145 de checkpoint-uri cu PNG original pot produce zeci de MB de base64.

Memoria fork-ului de test este configurabilă prin `-TestHeapMax`. Publicarea și auditul HTML trebuie verificate cu datele reale. Dacă publicarea eșuează, se reface din aceleași dovezi, fără repetarea partidei, omiterea imaginilor sau reinterpretarea unei înfrângeri ca succes.

Dovezi externe păstrate:

- `<evidence-root>/original-hard-live/20261104-live-romanian-hard-01/`:
  încercare live incompletă, eroarea nativă și Extent-ul parțial FAIL, fără
  rescrierea lor ca PASS.
- `<evidence-root>/original-hard-live/20261104-live-romanian-hard-02/`:
  **26 metode native PASS**, zero eșecuri/erori/sărite; reluarea Android nu a
  început deoarece rezervarea era ocupată.

Decizia publică disponibilă până acum este o situație de o carte: J♥ propriu, A♠ întors, licitație0, apoi 9♦ și10♥ publice.

J♥ este singura carte legală și nu poate câștiga levata condusă pe caro. Această mutare forțată nu este o eroare de joc. Acceptarea ei și scorul final al mâinii cer snapshot-ul original curent; intenția salvată nu este tratată drept rezultat observat.
