# Rentz Lab 0.3.0

## Pe scurt

Rentz Lab 0.3.0 este publicat pentru Android 8.0 și versiunile mai noi. APK-ul este semnat cu identitatea persistentă Rentz Lab, iar copia publică și checksum-ul au fost descărcate și confruntate cu buildul calificat.

## Descărcare și identitate

- [Descarcă APK-ul 0.3.0](https://rentz-lab-apk.totaloatcake.chatgpt.site/v0.3.0/RentzLab-0.3.0.apk)
- Fișier: `RentzLab-0.3.0.apk` · 852.536 bytes
- SHA-256: `d63153edd9445cc2130a50bac7c3dfe87b1e1a9d36ee9145fc0494da5fa05e8c`
- Certificatul semnatarului, SHA-256: `5a4605343bf27f41cdb9e6f8bdf2f4087b0b080779bea250b823307da5666b68`
- `applicationId`: `ro.rentzlab.app` · versiune `0.3.0` · `versionCode` 6 · `minSdk` 26 · `targetSdk` 36

## Funcționalități incluse

- Masa Rentz redimensionează compoziția pentru 3–6 jucători și rezervă centrul pentru tabla secvențelor.
- Cărțile legale sunt evidențiate discret, iar mâna păstrează ordinea și starea legală furnizate de motor.
- Actorul automat este indicat înainte de mutarea sa; logica așteaptă primul cadru afișat, apoi programează mutarea după 550 ms. Schimbarea rutei sau sesiunii invalidează acțiunile rămase.
- Meniul, profilul, scorurile confirmate, istoricul și restaurarea sesiunii rămân integrate cu regulile Rentz.

## Calificare

- Regresie offline: 39 suite Maven, 373 teste; zero eșecuri, erori și teste ignorate.
- Suita Android JVM/Gradle: 28 suite, 183 teste (72 aplicație și 111 nucleu); zero eșecuri, erori și teste ignorate. Lint și asamblarea Release au trecut.
- Calificarea UI izolată a evaluat 1.994 comparații capturate din testele de componentă.
- Două rulări native pe aceeași sursă, cu pachetul izolat `ro.rentzlab.app.uisanity`: 29 checkpoint-uri și 303 comparații. Au acoperit o mână completă de levate Rentz, meniul, numele și profilul, limbile, istoricul după restart, scorurile semnate compacte și mesele de 3/6 locuri. Capturile originale sunt inline base64 în rapoartele Extent externe.
- APK-ul de release a fost calificat separat: build Release, `debuggable=false`, semnătură și hash potrivite. Sanity-ul nativ a folosit varianta izolată construită din același snapshot pentru a păstra instalarea și datele aplicației existente.
- Intervalul de 550 ms și anularea după schimbarea cadrului sunt verificate în testele de componentă `TurnPacingTest`; rulările native verifică fluxul vizual și jocul, dar nu pretind că au cronometrat independent intervalul.
- Descărcarea HTTPS anonimă a întors HTTP 200 pentru APK și checksum; octeții APK-ului coincid cu SHA-256 de mai sus. Release-ul GitHub este verificat prin API autentificat; repo-ul nu oferă metadate release anonim, de aceea descărcarea publică folosește ruta HTTPS directă.

Rapoartele Extent și capturile complete rămân în depozitul extern de acceptanță, nu în arborele sursă.

[Notele versiunii](release-notes.md) · [Domeniul rapoartelor](reports/README.md) · [Procesul release-ului](../../docs/rentz/ATF_Release.md)