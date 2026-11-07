# Dovezile Rentz Lab 0.3.0

## Pe scurt

Dosarul public păstrează domeniul și identitatea calificării. Rulările brute, jurnalele, PNG-urile native și rapoartele Extent complete sunt în depozitul extern de acceptanță, separat de surse.

## Colecții și ce demonstrează

| Colecție | Populație | Limita interpretării |
|---|---|---|
| Regresie Maven | 39 suite și 373 teste, fără FAIL/ERROR/SKIP | Contracte de componente și guvernanță, fără emulator. |
| Android JVM/Gradle | 28 suite și 183 teste, fără FAIL/ERROR/SKIP; include 72 teste de aplicație și 111 de nucleu | Codul JVM Android și resursele de build; nu substituie interacțiunea nativă. |
| Comparații UI capturate | 1.994 comparații evaluate | Oracole de componentă păstrate la execuție, fără a reconstrui aserțiunile după rulare. |
| Sanity nativ compact | 14 checkpoint-uri, 129 comparații | O mână completă, meniu, profil, limbi, istoric după restart și scor compact; pachet izolat din sursa exactă. |
| Sanity nativ locuri | 15 checkpoint-uri, 174 comparații | Inițializare și geometrie pentru 3 și 6 jucători; pachet izolat din aceeași sursă. |
| APK Release | SHA-256, certificat, `debuggable=false`, aliniere și versiune | Identitatea binarului Release; nu afirmă că sansele native au fost executate cu pachetul Release instalat. |
| Descărcare publică | HTTP 200 anonim, 852.536 bytes, SHA-256 potrivit pentru APK și checksum | Canalul static HTTPS. API-ul release-ului GitHub este separat și verificat autentificat. |

## Rapoarte Extent

Rapoartele de integrare conțin comparațiile componentei și capturile Android originale inline base64, plus ierarhiile native. Fiecare raport identifică explicit pachetul UI izolat și domeniul probei. Nu agregăm rularile diferite într-un singur verdict nediferențiat.

## Limitări

- Probele native au jucat complet o mână de levate din Rentz și nu toate cele șapte sub-jocuri într-o singură partidă completă.
- Intervalul de 550 ms este verificat prin test de componentă cu ceas controlat și prin implementarea care așteaptă primul cadru. Sanity-ul nativ verifică stările vizuale, dar nu cronometrează separat fiecare indicator și carte adversă.
- APK-ul Release nu a înlocuit aplicația instalată care păstrează date; testele native au folosit varianta `ro.rentzlab.app.uisanity` construită din același snapshot. Semnătura, versiunea, buildul și copia publică ale APK-ului Release au fost verificate separat.
- Nici aceste probe, nici scorurile lor nu afirmă paritate completă cu aplicația de referință.