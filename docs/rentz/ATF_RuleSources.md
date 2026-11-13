# Proveniența și certitudinea regulilor

## Surse de adevăr

[Pagina oficială Rentz](https://play.google.com/store/apps/details?id=com.boonwiz.rentz) identifică pachetul și declarațiile autorului. Textul publicat este probă declarativă; observația în aplicația instalată și verificarea mecanică au niveluri separate.

| Statut | Semnificație |
|---|---|
| `PUBLISHER_DECLARED` | Autorul descrie explicit regula; comportamentul executabil nu rezultă numai din text. |
| `PUBLISHER_APK_DECLARED` | Instrucțiune din resursa identificată a APK-ului semnat; manifestul păstrează hash-ul artefactului și cheia resursei. Nu înseamnă verificare în execuție. |
| `PUBLISHER_SCREENSHOT_OBSERVED` | O valoare este lizibilă în captura publicată de autor; nu identifică singură preset-ul sau versiunea executată. |
| Declarație cu interpretare | Mecanismul este descris, dar o frontieră de implementare cere clarificare. |
| `LAB_VARIANT_REQUIRES_REFERENCE_CONFIRMATION` | Convenție a laboratorului, fără atribuire implicită aplicației originale. |
| Neimplementat | Extensie ori funcție a produsului, separată de modelul activ. |

## Declarațiile relevante ale autorului

- Trei până la șase jucători, cu opt cărți fiecare și opt sub-jocuri de bază.
- Alegerea fiecărui sub-joc o dată de fiecare actor; opțiune pe nevăzute cu punctaj dublu.
- Pachetul conține rangurile superioare pentru masa aleasă, fără cărți rămase; levatele cer urmarea culorii și nu au atu.
- Pe nevăzute dublează punctele tuturor; nu este permis când alegătorului îi mai rămâne un singur sub-joc.
- Alegătorul deschide mâna; eventualele excepții ale secvențelor se califică separat.
- Penalizări cumulate în Totale, inclusiv dubla categorie a damei de caro.
- Deschidere a secvențelor dependentă de masă; as cu mutare suplimentară și carte minimă care sare următorul actor.
- Preset-uri „Classic” și „Traditional”, puncte configurabile și opțiuni de sub-jocuri.
- Zece de treflă se încheie după captura țintei; secvențele permit pasul când actorul nu are continuare.

Inventarul păstrează sensul regulilor, fără copierea textului comercial sau algoritmilor aplicației. [Manifestul](../../config/rentz-rules.yaml) leagă identitățile regulilor de cod și componente.

Instrucțiunile împachetate se citesc fără pornirea produsului, din resursele APK-ului identificat. Textul declară reguli și opțiuni; valorile dinamice ale preset-urilor, setările active și aplicarea efectivă a regulilor cer probe suplimentare.

## Observație numerică și identificarea preset-ului

[Catalogul numeric](../../config/rentz-reference-observations.yaml) consemnează câmp cu câmp ce este lizibil în capturile de alegere și scor ale autorului. El păstrează URL-ul imaginii, masa observată și necunoscutele explicite; nu este profil executabil.

| Nivel | Ce permite | Ce nu dovedește |
|---|---|---|
| Etichetă numerică vizibilă | Magnitudine și unitate pentru contribuția etichetată. | Identitatea „Classic” sau „Traditional”, dacă nu apare. |
| Premii vizibile în istoric | Mulțimea premiilor acordate pe acea masă. | Ordinea tuturor locurilor, fără corelația cu sosirile. |
| Declarație despre dublare | Contractul descris al alegerii pe nevăzute. | Calificarea implementării pentru fiecare sub-joc. |
| Profil configurat și evaluat | Comportamentul motorului la acele valori. | Echivalența cu preset-ul original ori cu altă platformă. |

Un rezultat din simulator nu promovează statutul unei surse. O captură nu confirmă controalele pe care nu le arată, iar o asemănare numerică între platforme nu stabilește identitatea regulilor.

## Clarificări necesare

| Frontieră | Ce trebuie confirmat |
|---|---|
| Punctaje | Asocierea valorilor vizibile cu preset-ul, punctajul Whist, etichetele tuturor sosirilor și mesele de alte dimensiuni. |
| Program | Primul alegător și corespondența dintre sensul spre stânga și indexarea locurilor; rotația și deschiderea alegătorului sunt declarate. |
| Levate | Aplicarea în execuție și eventualele restricții suplimentare; urmarea culorii și lipsa atuului sunt declarate în APK. |
| Închidere | Frontierele pentru dame și carouri; terminarea la ținta 10♣ este declarată. |
| Secvențe | Asul fără continuare, actorii terminați și ultimul loc; pasul fără carte legală este declarat. |
| Variante | Totale plus și dezactivarea sub-jocurilor rămân neimplementate. Jocurile noi folosesc rangul central superior din [regulile laboratorului](ATF_Rules.md#pregătirea-mesei). Jurnalele păstrează politica de la începutul partidei. Presetul și opțiunile aplicației originale cer o confirmare separată. |

[O altă platformă Rentz](https://rentz.doizece.ro/rentz/help) descrie propria variantă. Ea poate sugera întrebări și cazuri, dar punctajele sau excepțiile ei nu devin contractul pachetului BoonWiz.

## Păstrarea informației

- În surse: reguli, statut, profiluri, observații și componente independente.
- În depozitul extern: APK-uri, semnături, capturi, jurnale și rezultatele rulărilor.
- În feedback: diferența rămasă și criteriul verificabil de închidere.

Confirmarea unui profil cere sursă identificată, valori capturate și punctare independentă. Schimbarea etichetei YAML sau acordul motorului cu propriul simulator nu este confirmare.
