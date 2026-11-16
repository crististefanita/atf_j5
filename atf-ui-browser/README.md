# Adaptor browser Rentz

## Pe scurt

Adaptorul folosește contractul Card Table Browser și proiectează starea publică din `GameSession`. Jucătorul poate dezvălui cărțile, alege sub-jocul, folosi un contract la orb când este permis și juca până la decontare. Sesiunea definește acțiunile legale, trecerile forțate și totalurile.

## Pornire

Pregătiți PowerShell 7 și JDK 21. Lansatorul verifică SHA-256 și folosește Card Table Browser 0.1.0 din `lib/`. `$WorkRoot` indică un director extern pentru clase. Deschideți adresa completă afișată de lansator.

```powershell
.\atf-ui-browser\Start-RentzBrowser.ps1 -WorkRoot $WorkRoot
```

## Verificare

Arhiva `lib/atf-ui-browser-0.1.0-tests.jar` furnizează aceleași probe reutilizabile pentru contractul HTTP și afișajul mobil. Extrageți arhiva cu `jar xf` într-un director extern, apoi rulați următoarea comandă cu Node.js 24. `$Classes` este classpath-ul returnat de lansator cu `-BuildOnly`, iar `$Chrome` poate indica executabilul Chrome sau Edge.

```powershell
node .\packaged-sanity.mjs $EvidenceRoot $Classes "ro.rentzlab.browser.RentzBrowser" "Rentz Lab" $Chrome
```

`-BuildOnly` întoarce directorul de clase pentru verificarea de componentă comună. Parcursul verifică dezvăluirea, alegerea contractului, acțiunea manuală și înregistrarea totalurilor. Comparația vizuală pe telefon și ecranele de profil și realizări rămân criterii de calificare. [Contractul jocului](../rentz-lab-app/game-core/README.md)
