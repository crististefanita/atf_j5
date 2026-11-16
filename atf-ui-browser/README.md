# Adaptor browser Rentz

## Pe scurt

Adaptorul folosește contractul Card Table Browser și proiectează starea publică din `GameSession`. Jucătorul poate dezvălui cărțile, alege sub-jocul, folosi un contract la orb când este permis și juca până la decontare. Sesiunea definește acțiunile legale, trecerile forțate și totalurile.

## Pornire

Setați `$BrowserRoot` la directorul modulului Card Table Browser din checkout-ul care deține sursa comună. `$WorkRoot` indică un director extern pentru clase. Deschideți adresa completă afișată de lansator.

```powershell
.\atf-ui-browser\Start-RentzBrowser.ps1 -BrowserRoot $BrowserRoot -WorkRoot $WorkRoot
```

## Verificare

`-BuildOnly` întoarce directorul de clase pentru verificarea de componentă comună. Parcursul verifică dezvăluirea, alegerea contractului, acțiunea manuală și înregistrarea totalurilor. Comparația vizuală pe telefon și ecranele de profil și realizări rămân criterii de calificare. [Contractul jocului](../rentz-lab-app/game-core/README.md)
